#!/usr/bin/env python3
"""Pull daily Garmin Connect wellness metrics and push them into Ítaca.

Unofficial: logs into Garmin Connect with your own credentials (via the
`garminconnect` library) and POSTs sleep / HRV / recovery metrics to
`POST {ITACA_URL}/api/wellness/daily` (one idempotent upsert per day).

Run it daily (cron or the bundled GitHub Action). Env vars:
  GARMIN_EMAIL, GARMIN_PASSWORD   your Garmin Connect login
  ITACA_URL                       e.g. https://itaca.example.com (no trailing slash)
  ITACA_API_TOKEN                 the same value as the backend's ITACA_API_TOKEN
  DAYS                            optional, how many past days to backfill (default 1 = yesterday)

Garmin's private API shape varies between library versions, so every metric is
fetched defensively: a missing field just becomes null. If a metric never shows
up, check the field paths against your installed `garminconnect` version.
"""
import os
import sys
import datetime as dt

import requests
from garminconnect import Garmin


def dig(obj, *path):
    """Safely walk nested dicts/lists; returns None if any step is missing."""
    cur = obj
    for key in path:
        if isinstance(cur, dict):
            cur = cur.get(key)
        elif isinstance(cur, list) and isinstance(key, int) and -len(cur) <= key < len(cur):
            cur = cur[key]
        else:
            return None
    return cur


def as_int(v):
    try:
        return int(round(float(v))) if v is not None else None
    except (TypeError, ValueError):
        return None


def secs_to_min(v):
    i = as_int(v)
    return i // 60 if i is not None else None


def metrics_for(client, date_str):
    """Best-effort extraction of one day's metrics into the Ítaca payload shape."""
    out = {"date": date_str}

    def try_fetch(fn):
        try:
            return fn()
        except Exception as e:  # noqa: BLE001 - the private API throws all sorts; degrade gracefully
            print(f"  · skip {fn.__name__ if hasattr(fn, '__name__') else fn}: {e}", file=sys.stderr)
            return None

    sleep = try_fetch(lambda: client.get_sleep_data(date_str)) or {}
    s = sleep.get("dailySleepDTO", sleep) if isinstance(sleep, dict) else {}
    out["sleepMinutes"] = secs_to_min(dig(s, "sleepTimeSeconds"))
    out["deepMinutes"] = secs_to_min(dig(s, "deepSleepSeconds"))
    out["lightMinutes"] = secs_to_min(dig(s, "lightSleepSeconds"))
    out["remMinutes"] = secs_to_min(dig(s, "remSleepSeconds"))
    out["awakeMinutes"] = secs_to_min(dig(s, "awakeSleepSeconds"))
    out["sleepScore"] = as_int(dig(s, "sleepScores", "overall", "value"))

    hrv = try_fetch(lambda: client.get_hrv_data(date_str)) or {}
    out["hrvAvgMs"] = as_int(dig(hrv, "hrvSummary", "lastNightAvg"))
    out["hrvStatus"] = dig(hrv, "hrvSummary", "status")

    summary = try_fetch(lambda: client.get_user_summary(date_str)) or {}
    out["restingHr"] = as_int(summary.get("restingHeartRate"))
    out["stressAvg"] = as_int(summary.get("averageStressLevel"))
    out["bodyBatteryHigh"] = as_int(summary.get("bodyBatteryHighestValue"))
    out["bodyBatteryLow"] = as_int(summary.get("bodyBatteryLowestValue"))
    out["steps"] = as_int(summary.get("totalSteps"))
    out["activeCalories"] = as_int(summary.get("activeKilocalories"))
    out["spo2Avg"] = as_int(summary.get("averageSpo2") or summary.get("averageSpO2"))
    resp = summary.get("avgWakingRespirationValue") or summary.get("respiration")
    try:
        out["respirationAvg"] = round(float(resp), 1) if resp is not None else None
    except (TypeError, ValueError):
        out["respirationAvg"] = None

    # SpO2 isn't in the daily summary unless pulse ox is enabled; the dedicated endpoint is slow
    # and times out when it's off, so only hit it when FETCH_SPO2 is set.
    if out.get("spo2Avg") is None and os.environ.get("FETCH_SPO2", "").lower() in ("1", "true", "yes"):
        spo2 = try_fetch(lambda: client.get_spo2_data(date_str)) or {}
        out["spo2Avg"] = as_int(dig(spo2, "averageSpO2") or dig(spo2, "averageSpo2"))

    return {k: v for k, v in out.items() if v is not None or k == "date"}


def connect():
    """Resume a saved Garmin session if present (no re-login, no MFA, no rate-limit); else log
    in fully. Passing the tokenstore to `login()` makes garminconnect persist the tokens itself,
    so subsequent runs resume instead of re-authenticating (repeated logins trigger Garmin 429s).
    The first interactive login handles MFA (a code prompt on stdin)."""
    email = os.environ["GARMIN_EMAIL"]
    password = os.environ["GARMIN_PASSWORD"]
    tokenstore = os.path.expanduser(os.environ.get("GARMINTOKENS", "~/.garminconnect"))
    try:
        client = Garmin()
        client.login(tokenstore)  # resume from saved tokens (no credentials needed)
        print(f"Garmin: resumed saved session ({tokenstore})", file=sys.stderr)
        return client
    except Exception:  # noqa: BLE001 - no/expired token -> full login below
        pass
    client = Garmin(email, password)
    try:
        client.login(tokenstore)  # full login; garminconnect dumps the tokens to tokenstore for next time
    except Exception as e:  # noqa: BLE001 - turn the library's stack traces into one clear line
        msg = str(e).lower()
        if any(s in msg for s in ("resolve", "nameresolution", "connection", "max retries", "timed out")):
            sys.exit("Network error reaching Garmin (DNS/connection). Check your internet/VPN and retry.")
        if "429" in msg or "too many" in msg:
            sys.exit("Garmin rate-limited the login (429). Wait a few minutes, then run it once.")
        raise
    print(f"Garmin: logged in; session saved to {tokenstore}", file=sys.stderr)
    return client


def main():
    dry_run = os.environ.get("DRY_RUN", "").lower() in ("1", "true", "yes")
    days = int(os.environ.get("DAYS", "1"))

    client = connect()

    session = requests.Session()
    if not dry_run:
        itaca_url = os.environ.get("ITACA_URL")
        if not itaca_url:
            sys.exit("Set ITACA_URL (and ITACA_API_TOKEN) to push, or DRY_RUN=1 to just print the metrics.")
        itaca_url = itaca_url.rstrip("/")
        token = os.environ.get("ITACA_API_TOKEN", "")
        session.headers["Content-Type"] = "application/json"
        if token:
            session.headers["Authorization"] = f"Bearer {token}"

    today = dt.date.today()
    for i in range(1, days + 1):
        date_str = (today - dt.timedelta(days=i)).isoformat()
        print(f"Garmin {date_str}…", file=sys.stderr)
        payload = metrics_for(client, date_str)
        got = sorted(k for k in payload if k != "date")
        if dry_run:
            print(f"  [dry-run] {date_str}: {payload}")
            continue
        r = session.post(f"{itaca_url}/api/wellness/daily", json=payload, timeout=30)
        r.raise_for_status()
        print(f"  pushed {date_str}: {got}", file=sys.stderr)


if __name__ == "__main__":
    main()
