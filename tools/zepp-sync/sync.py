#!/usr/bin/env python3
"""Pull body composition from the Xiaomi scale (Zepp cloud) and push it into Ítaca.

Unofficial: logs into the Zepp (Huami) account with your own credentials — via the
`huami-token` library, which implements the current encrypted app login — and reads
`GET /users/{id}/members/-1/weightRecords` from the regional mifit API, then POSTs one
idempotent upsert per day to `POST {ITACA_URL}/api/wellness/body`.

Run it daily (cron or the bundled GitHub Action). Env vars:
  ZEPP_EMAIL, ZEPP_PASSWORD    your Zepp account login (email registration; social login won't work)
  ITACA_URL                    e.g. https://itaca.example.com (no trailing slash)
  ITACA_API_TOKEN              the same value as the backend's ITACA_API_TOKEN
  DAYS                         optional, how many past days to scan (default 7 — weigh-ins are sparse)
  ZEPP_HOST                    optional, skip host discovery (e.g. api-mifit-de2.zepp.com)
  DRY_RUN                      optional, print instead of pushing

The API shape is reverse-engineered and drifts: every metric is fetched defensively —
a missing field just becomes null. Some scales report rates (%), others masses (kg);
both are handled.
"""
import datetime as dt
import json
import os
import sys

import requests
from huami_token.zepp import ZeppSession
from loguru import logger

# huami-token logs tokens at DEBUG; keep CI logs at INFO and token-free.
logger.remove()
logger.add(sys.stderr, level="INFO")

# Regional mifit hosts, tried in order until one answers with data (override with ZEPP_HOST).
HOSTS = [
    "api-mifit-de2.zepp.com",
    "api-mifit.zepp.com",
    "api-mifit-us2.zepp.com",
    "api-mifit-us3.zepp.com",
]


def num(v):
    try:
        f = float(v)
        return f if f > 0 else None
    except (TypeError, ValueError):
        return None


def first(record, *keys):
    for k in keys:
        if record.get(k) is not None:
            return record[k]
    return None


def to_payload(record):
    """Map one Zepp weight record to the Ítaca body-composition payload (defensive)."""
    ts = first(record, "timestamp", "time", "generatedTime")
    if ts is None:
        return None
    ts = float(ts)
    if ts > 1e11:  # milliseconds
        ts /= 1000.0
    date = dt.datetime.fromtimestamp(ts).date().isoformat()

    weight = num(first(record, "weight", "weightKg"))
    if weight is None:
        return None

    fat_pct = num(first(record, "bodyFatRate", "fatRate", "bodyFat"))
    water_pct = num(first(record, "moistureRate", "waterRate", "moisture"))
    bone = num(first(record, "boneMass", "bone"))
    visceral = num(first(record, "visceralFat", "visceralFatLevel"))
    bmi = num(record.get("bmi"))
    bmr = num(first(record, "metabolism", "basalMetabolism", "bmr"))

    # Muscle arrives either as kg ("muscle"/"muscleMass") or as a rate (% of weight).
    muscle = num(first(record, "muscleMass", "muscle"))
    muscle_rate = num(record.get("muscleRate"))
    if muscle is None and muscle_rate is not None:
        muscle = round(weight * muscle_rate / 100.0, 2)
    elif muscle is not None and muscle > weight:  # clearly a rate mislabeled as mass
        muscle = round(weight * muscle / 100.0, 2)

    payload = {
        "date": date,
        "weightKg": round(weight, 2),
        "bmi": bmi,
        "bodyFatPct": fat_pct,
        "muscleKg": muscle,
        "waterPct": water_pct,
        "boneKg": bone,
        "visceralFat": visceral,
        "bmrKcal": int(round(bmr)) if bmr is not None else None,
    }
    return {k: v for k, v in payload.items() if v is not None or k in ("date",)}


def fetch_records(app_token, user_id, days):
    now = dt.datetime.now(dt.timezone.utc)
    from_s = int((now - dt.timedelta(days=days)).timestamp())
    to_s = int(now.timestamp())
    headers = {
        "apptoken": app_token,
        "appname": "com.huami.midong",
        "appplatform": "android_phone",
        "user-agent": "Zepp/9.12.5 (Pixel 4; Android 12; Density/2.75)",
    }
    hosts = [os.environ["ZEPP_HOST"]] if os.environ.get("ZEPP_HOST") else HOSTS
    # The records carry millisecond timestamps; whether the range filter wants s or ms is
    # undocumented and has drifted, so try ms first, then seconds, per host. An empty list
    # is NOT success (wrong host answers 200 + empty): keep trying, remember we got one.
    saw_empty_host = None
    last_err = None
    for host in hosts:
        url = f"https://{host}/users/{user_id}/members/-1/weightRecords"
        for unit, (f, t) in (("ms", (from_s * 1000, to_s * 1000)), ("s", (from_s, to_s))):
            try:
                r = requests.get(
                    url,
                    params={"fromTime": f, "toTime": t, "limit": 300, "isForward": 0},
                    headers=headers,
                    timeout=30,
                )
                if r.status_code != 200:
                    last_err = f"{host} ({unit}) -> HTTP {r.status_code}"
                    print(f"  · {last_err}", file=sys.stderr)
                    break  # same host won't improve with other units
                data = r.json()
                items = data.get("items") if isinstance(data, dict) else data
                if isinstance(items, list) and items:
                    print(f"Zepp: {len(items)} weight records from {host} ({unit})", file=sys.stderr)
                    return items
                saw_empty_host = host
                print(f"  · {host} ({unit}) -> 200 but 0 records", file=sys.stderr)
            except requests.RequestException as e:
                last_err = f"{host} ({unit}) -> {e}"
                print(f"  · {last_err}", file=sys.stderr)
                break
    if saw_empty_host:
        print(
            f"No weight records on any host (e.g. {saw_empty_host} answered fine but empty). "
            "Either no weigh-ins in the window, or the scale syncs to another app "
            "(Zepp Life / Mi Fitness) instead of Zepp.",
            file=sys.stderr,
        )
        return []
    sys.exit(f"Could not read weightRecords from any host (last: {last_err}). Set ZEPP_HOST to your region.")


def main():
    dry_run = os.environ.get("DRY_RUN", "").lower() in ("1", "true", "yes")
    days = int(os.environ.get("DAYS", "7"))

    email = os.environ.get("ZEPP_EMAIL")
    password = os.environ.get("ZEPP_PASSWORD")
    if not email or not password:
        sys.exit("Set ZEPP_EMAIL and ZEPP_PASSWORD (a Zepp email-registered account).")

    session = ZeppSession(email, password)
    session.login()

    records = fetch_records(session.app_token, session.user_id, days)
    # One payload per date; records come newest first, keep the newest of each day.
    by_date = {}
    for rec in records:
        p = to_payload(rec if isinstance(rec, dict) else {})
        if p and p["date"] not in by_date:
            by_date[p["date"]] = p

    if not by_date:
        print("No weigh-ins in the window — nothing to push.", file=sys.stderr)
        return

    if dry_run:
        for p in sorted(by_date.values(), key=lambda x: x["date"]):
            print(f"  [dry-run] {json.dumps(p)}")
        return

    itaca_url = os.environ.get("ITACA_URL")
    if not itaca_url:
        sys.exit("Set ITACA_URL (and ITACA_API_TOKEN) to push, or DRY_RUN=1 to just print.")
    http = requests.Session()
    http.headers["Content-Type"] = "application/json"
    token = os.environ.get("ITACA_API_TOKEN", "")
    if token:
        http.headers["Authorization"] = f"Bearer {token}"
    for p in sorted(by_date.values(), key=lambda x: x["date"]):
        r = http.post(f"{itaca_url.rstrip('/')}/api/wellness/body", json=p, timeout=30)
        r.raise_for_status()
        print(f"  pushed {p['date']}: {sorted(k for k in p if k != 'date')}", file=sys.stderr)


if __name__ == "__main__":
    main()
