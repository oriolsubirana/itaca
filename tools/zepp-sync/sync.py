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
    """Map one Zepp weight record to the Ítaca body-composition payload.

    Proven shape (SmartScaleConnect): generatedTime is unix SECONDS, weightType != 0 rows carry
    broken values, and the metrics live in a nested "summary" — whose muscleRate is actually a
    MASS in kg despite the name. Flat fields are kept as fallback; missing -> null.
    """
    if record.get("weightType") not in (0, None):
        return None
    summary = record.get("summary") or {}
    src = {**record, **summary}

    ts = first(src, "generatedTime", "timestamp", "time")
    if ts is None:
        return None
    ts = float(ts)
    if ts > 1e11:  # milliseconds
        ts /= 1000.0
    date = dt.datetime.fromtimestamp(ts).date().isoformat()

    weight = num(first(src, "weight", "weightKg"))
    if weight is None:
        return None

    fat_pct = num(first(src, "fatRate", "bodyFatRate", "bodyFat"))
    water_pct = num(first(src, "bodyWaterRate", "moistureRate", "waterRate"))
    bone = num(first(src, "boneMass", "bone"))
    visceral = num(first(src, "visceralFat", "visceralFatLevel"))
    bmi = num(src.get("bmi"))
    bmr = num(first(src, "metabolism", "basalMetabolism", "bmr"))

    # muscleRate is a mass (kg) in practice; if it exceeds the weight it must be a real rate.
    muscle = num(first(src, "muscleMass", "muscle", "muscleRate"))
    if muscle is not None and muscle > weight:
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


HEADERS_BASE = {
    "appname": "com.huami.midong",
    "appplatform": "android_phone",
    "user-agent": "Zepp/9.12.5 (Pixel 4; Android 12; Density/2.75)",
}


def member_records(host, app_token, user_id, member_id, from_s):
    """All weight records of one member newer than from_s, paginating with `next` (toTime unix s)."""
    headers = {**HEADERS_BASE, "apptoken": app_token}
    items, to_time = [], int(dt.datetime.now(dt.timezone.utc).timestamp())
    while to_time and to_time > 0:
        r = requests.get(
            f"https://{host}/users/{user_id}/members/{member_id}/weightRecords",
            params={"limit": 200, "toTime": to_time},
            headers=headers,
            timeout=30,
        )
        r.raise_for_status()
        data = r.json() if isinstance(r.json(), dict) else {}
        page = data.get("items") or []
        items.extend(page)
        if not page:
            break
        oldest = min(float(i.get("generatedTime") or 0) for i in page)
        if oldest and oldest < from_s:
            break
        to_time = data.get("next") or 0
    return [i for i in items if float(i.get("generatedTime") or 0) >= from_s]


def family_member_ids(host, app_token, user_id):
    """Scale family member ids (fuid) — weigh-ins may live under a member instead of -1."""
    try:
        r = requests.post(
            f"https://{host}/huami.health.scale.familymember.get.json",
            data={"fuid": "all", "userid": user_id},
            headers={**HEADERS_BASE, "apptoken": app_token},
            timeout=30,
        )
        if r.status_code != 200:
            return []
        members = ((r.json() or {}).get("data") or {}).get("list") or []
        ids = [m.get("fuid") for m in members if m.get("fuid") is not None]
        print(f"Zepp: {len(ids)} scale family member(s) on {host}", file=sys.stderr)
        return ids
    except (requests.RequestException, ValueError):
        return []


def fetch_records(app_token, user_id, days):
    from_s = int((dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)).timestamp())
    hosts = [os.environ["ZEPP_HOST"]] if os.environ.get("ZEPP_HOST") else HOSTS
    last_err = None
    reachable = False
    for host in hosts:
        try:
            items = member_records(host, app_token, user_id, -1, from_s)
            reachable = True
            if not items:  # weigh-ins may sit under a family member, not the main profile
                for fuid in family_member_ids(host, app_token, user_id):
                    items.extend(member_records(host, app_token, user_id, fuid, from_s))
            if items:
                print(f"Zepp: {len(items)} weight records from {host}", file=sys.stderr)
                return items
            print(f"  · {host} -> reachable but 0 records", file=sys.stderr)
        except requests.RequestException as e:
            last_err = f"{host} -> {e}"
            print(f"  · {last_err}", file=sys.stderr)
    if reachable:
        print(
            "No weight records on any host: either no weigh-ins in the window, or the scale "
            "syncs to another app (Zepp Life / Mi Fitness) instead of Zepp.",
            file=sys.stderr,
        )
        return []
    sys.exit(f"Could not reach weightRecords on any host (last: {last_err}). Set ZEPP_HOST to your region.")




def diagnose(app_token, user_id):
    """Probe candidate endpoints across hosts; print only status, counts and key names (no values),
    so the account's data layout can be located without leaking personal data into CI logs."""
    headers = {**HEADERS_BASE, "apptoken": app_token}
    now = int(dt.datetime.now(dt.timezone.utc).timestamp())
    hosts = [os.environ["ZEPP_HOST"]] if os.environ.get("ZEPP_HOST") else (
        HOSTS + [h.replace(".zepp.com", ".huami.com") for h in HOSTS]
    )

    def shape(obj, depth=0):
        if isinstance(obj, dict):
            return {k: shape(v, depth + 1) if depth < 2 else type(v).__name__ for k, v in obj.items()}
        if isinstance(obj, list):
            return [len(obj), shape(obj[0], depth + 1) if obj else None]
        return type(obj).__name__

    probes = [
        ("GET", "/users/{uid}/devices", {"enableMultiDevice": "true"}),
        ("GET", "/users/{uid}/members/-1/weightRecords", {"limit": 200, "toTime": now}),
        ("GET", "/users/{uid}/weightRecords", {"limit": 200, "toTime": now}),
        ("GET", "/users/{uid}/events", {"eventType": "weight", "from": (now - 400 * 86400) * 1000,
                                        "to": now * 1000, "limit": 100}),
        ("GET", "/users/{uid}/events", {"eventType": "body_composition", "from": (now - 400 * 86400) * 1000,
                                        "to": now * 1000, "limit": 100}),
        ("POST", "/huami.health.scale.familymember.get.json", {"fuid": "all", "userid": user_id}),
        ("POST", "/huami.health.scale.datalist.get.json",
         {"userid": user_id, "fuid": "-1", "startdate": "0", "enddate": str(now)}),
    ]
    for host in hosts:
        print(f"--- {host}", file=sys.stderr)
        for method, path, params in probes:
            url = f"https://{host}" + path.format(uid=user_id)
            try:
                if method == "GET":
                    r = requests.get(url, params=params, headers=headers, timeout=20)
                else:
                    r = requests.post(url, data=params, headers=headers, timeout=20)
                try:
                    body = shape(r.json())
                except ValueError:
                    body = f"non-json ({len(r.content)} bytes)"
                print(f"  {method} {path} {params.get('eventType', '')} -> {r.status_code} {body}", file=sys.stderr)
            except requests.RequestException as e:
                print(f"  {method} {path} -> {e}", file=sys.stderr)


def main():
    dry_run = os.environ.get("DRY_RUN", "").lower() in ("1", "true", "yes")
    days = int(os.environ.get("DAYS", "7"))

    email = os.environ.get("ZEPP_EMAIL")
    password = os.environ.get("ZEPP_PASSWORD")
    if not email or not password:
        sys.exit("Set ZEPP_EMAIL and ZEPP_PASSWORD (a Zepp email-registered account).")

    session = ZeppSession(email, password)
    session.login()

    if os.environ.get("DIAGNOSE", "").lower() in ("1", "true", "yes"):
        diagnose(session.app_token, session.user_id)
        return

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
