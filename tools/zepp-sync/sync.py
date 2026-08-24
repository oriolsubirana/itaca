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
import secrets as pysecrets
import sys
import urllib.parse
import uuid

import requests
from huami_token.constants import HEADERS as HT_HEADERS
from huami_token.constants import PAYLOADS as HT_PAYLOADS
from huami_token.constants import URLS as HT_URLS
from huami_token.errors import AuthenticationError
from huami_token.zepp import ZeppSession
from loguru import logger

# huami-token logs tokens at DEBUG; keep CI logs at INFO and token-free.
logger.remove()
logger.add(sys.stderr, level="INFO")

# Regional mifit hosts, tried in order until one answers with data (override with ZEPP_HOST).
# The login response's `domains` lists the account's REAL data hosts — those go first.
HOSTS = [
    "api-mifit-de2.zepp.com",
    "api-mifit.zepp.com",
    "api-mifit-us2.zepp.com",
    "api-mifit-us3.zepp.com",
]


class ZeppSessionWithDomains(ZeppSession):
    """huami-token's ZeppSession discards the login response's `domains` (the per-account data
    hosts); this re-does only the second login step to also capture them."""

    def __init__(self, username, password):
        super().__init__(username, password)
        self.data_hosts = []

    def _login(self):
        payload = HT_PAYLOADS.ZEPP_LOGIN.value.copy()
        payload["code"] = self._access_token
        payload["device_id"] = str(uuid.uuid4())
        response = requests.post(HT_URLS.ZEPP_LOGIN.value, data=payload, headers=HT_HEADERS.ZEPP_LOGIN.value)
        if response.status_code != 200:
            raise AuthenticationError(code="login-failed", message=f"Login HTTP {response.status_code}")
        data = response.json()
        token_info = data.get("token_info", {})
        self._login_token = token_info.get("login_token")
        self._app_token = token_info.get("app_token")
        self._user_id = token_info.get("user_id")
        if not self._login_token or not self._app_token or not self._user_id:
            raise AuthenticationError(code="no-login-tokens", message="Missing tokens in login response")
        for d in data.get("domains") or []:
            host = d.get("host") if isinstance(d, dict) else None
            if host and "mifit" in host:
                self.data_hosts.append(host)
            for cname in (d.get("cnames") or []) if isinstance(d, dict) else []:
                if cname and "mifit" in cname:
                    self.data_hosts.append(cname)
        print(f"Zepp: account data host(s) from login: {self.data_hosts or 'none advertised'}", file=sys.stderr)


# --- "Sign in with Mi Account" (the Zepp app's Xiaomi login) ---------------------------------
# Recipe from SmartScaleConnect: Xiaomi OAuth2 (Zepp Life's client) -> code -> Huami login with
# third_name=xiaomi-hm-mifit. Lands on the Huami account linked to the Xiaomi identity.
XIAOMI_OAUTH_PARAMS = (
    "_json=true&client_id=428135909242707968&pt=1"
    "&redirect_uri=https://api-mifit-cn.huami.com/huami.health.loginview.do&response_type=code"
)


def _xiaomi_json(text):
    prefix = "&&&START&&&"
    if text.startswith(prefix):
        text = text[len(prefix):]
    return json.loads(text)


class MiAccountZeppSession:
    """Log into the Huami/Zepp cloud through a Xiaomi (Mi) account, headlessly."""

    def __init__(self, username, password):
        self.username = username
        self.password = password
        self.app_token = None
        self.user_id = None
        self.data_hosts = []

    def login(self):
        import hashlib

        http = requests.Session()
        r1 = http.get(f"https://account.xiaomi.com/oauth2/authorize?{XIAOMI_OAUTH_PARAMS}", timeout=30)
        j1 = _xiaomi_json(r1.text)
        if not j1.get("_sign"):
            raise SystemExit(f"Xiaomi OAuth bootstrap failed: keys {sorted(j1)}")

        http.cookies.set("deviceId", pysecrets.token_hex(8))
        r2 = http.post(
            "https://account.xiaomi.com/pass/serviceLoginAuth2",
            data={
                "_json": "true",
                "hash": hashlib.md5(self.password.encode()).hexdigest().upper(),
                "sid": j1.get("sid"),
                "callback": j1.get("callback"),
                "_sign": j1.get("_sign"),
                "qs": j1.get("qs"),
                "user": self.username,
            },
            timeout=30,
        )
        j2 = _xiaomi_json(r2.text)
        if j2.get("code") != 0 or not j2.get("location"):
            hint = j2.get("description") or j2.get("desc") or j2.get("code")
            extra = " (captcha/2FA challenge — Xiaomi is blocking this IP)" if j2.get("captchaUrl") else ""
            raise SystemExit(f"Xiaomi login failed: {hint}{extra}")
        print("Xiaomi: authenticated", file=sys.stderr)

        # Follow the OAuth redirect chain until the huami redirect_uri carrying ?code=
        code, url = None, j2["location"]
        for _ in range(5):
            r = http.get(url, allow_redirects=False, timeout=30)
            nxt = r.headers.get("Location")
            for candidate in (nxt, url):
                if candidate and "code=" in candidate:
                    code = urllib.parse.parse_qs(urllib.parse.urlparse(candidate).query).get("code", [None])[0]
            if code or not nxt:
                break
            url = nxt
        if not code:
            raise SystemExit("Xiaomi OAuth: no authorization code in the redirect chain")

        r3 = requests.post(
            "https://account.zepp.com/v2/client/login",
            data={
                "app_name": "com.xiaomi.hm.health",
                "app_version": "6.14.0",
                "code": code,
                "country_code": "CN",
                "device_id": str(uuid.uuid4()),
                "device_model": "phone",
                "dn": "api-mifit.zepp.com",
                "grant_type": "request_token",
                "third_name": "xiaomi-hm-mifit",
            },
            timeout=30,
        )
        data = r3.json() if r3.status_code == 200 else {}
        token_info = data.get("token_info") or {}
        self.app_token = token_info.get("app_token")
        self.user_id = token_info.get("user_id")
        if not self.app_token or not self.user_id:
            raise SystemExit(f"Huami third-party login failed (HTTP {r3.status_code}, result {data.get('result')})")
        for d in data.get("domains") or []:
            if isinstance(d, dict):
                if d.get("host") and "mifit" in d["host"]:
                    self.data_hosts.append(d["host"])
                for cname in d.get("cnames") or []:
                    if cname and "mifit" in cname:
                        self.data_hosts.append(cname)
        print(f"Zepp (Mi account): logged in, data host(s): {self.data_hosts or 'none advertised'}", file=sys.stderr)


def candidate_hosts(session):
    if os.environ.get("ZEPP_HOST"):
        return [os.environ["ZEPP_HOST"]]
    seen, hosts = set(), []
    for h in list(getattr(session, "data_hosts", [])) + HOSTS:
        if h not in seen:
            seen.add(h)
            hosts.append(h)
    return hosts


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


def fetch_records(session, days):
    app_token, user_id = session.app_token, session.user_id
    from_s = int((dt.datetime.now(dt.timezone.utc) - dt.timedelta(days=days)).timestamp())
    hosts = candidate_hosts(session)
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




def diagnose(session):
    app_token, user_id = session.app_token, session.user_id
    """Probe candidate endpoints across hosts; print only status, counts and key names (no values),
    so the account's data layout can be located without leaking personal data into CI logs."""
    headers = {**HEADERS_BASE, "apptoken": app_token}
    now = int(dt.datetime.now(dt.timezone.utc).timestamp())
    base = candidate_hosts(session)
    hosts = base + [h.replace(".zepp.com", ".huami.com") for h in base if ".zepp.com" in h]

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

    # ZEPP_AUTH: "zepp" (email account), "xiaomi" (Mi Account sign-in), or "auto" (default):
    # try the plain Zepp account first and fall back to the Mi Account if it holds no records.
    mode = os.environ.get("ZEPP_AUTH", "auto").lower()
    diagnosing = os.environ.get("DIAGNOSE", "").lower() in ("1", "true", "yes")

    sessions = []
    if mode in ("zepp", "auto"):
        try:
            zs = ZeppSessionWithDomains(email, password)
            zs.login()
            sessions.append(zs)
        except Exception as e:  # noqa: BLE001 - in auto mode the Mi path may still work
            if mode == "zepp":
                raise
            print(f"Zepp email login failed ({e}); trying Mi Account", file=sys.stderr)
    if mode in ("xiaomi", "auto"):
        try:
            ms = MiAccountZeppSession(email, password)
            ms.login()
            # In auto mode skip the Mi session if it resolved to the very same account.
            if not any(getattr(s0, "user_id", None) == ms.user_id for s0 in sessions):
                sessions.append(ms)
        except SystemExit as e:
            if mode == "xiaomi" or not sessions:
                raise
            print(f"Mi Account login failed ({e}); continuing with the Zepp account", file=sys.stderr)
    if not sessions:
        sys.exit("No login method succeeded.")

    if diagnosing:
        for session in sessions:
            print(f"=== diagnosing account {session.user_id}", file=sys.stderr)
            diagnose(session)
        return

    records, session = [], sessions[0]
    for candidate in sessions:
        records = fetch_records(candidate, days)
        if records:
            session = candidate
            break
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
