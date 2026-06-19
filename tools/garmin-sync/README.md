# Garmin → Ítaca wellness sync

Pulls daily Garmin Connect metrics (sleep, HRV, resting HR, stress, body battery,
steps, SpO2, respiration) and pushes them to Ítaca's `POST /api/wellness/daily`.

This is an **unofficial** path: it logs into Garmin Connect with your own
credentials via the [`garminconnect`](https://github.com/cyberjunky/python-garminconnect)
library (no official Garmin partner API needed). It only reads your own data.
Garmin's private API can change between library versions — if a metric stops
arriving, check the field paths in `sync.py` against your installed version.

Ítaca never stores your Garmin credentials: they live only where you run this
script (your machine's env, or GitHub Action secrets).

## Run locally

```bash
cd tools/garmin-sync
python3 -m venv .venv && source .venv/bin/activate   # macOS: use python3 to create the venv
pip install -r requirements.txt                       # inside the venv, pip/python resolve fine

export GARMIN_EMAIL="you@example.com"
export GARMIN_PASSWORD="…"
export ITACA_URL="http://localhost:8080"        # or your deployed URL
export ITACA_API_TOKEN="…"                       # same as the backend's ITACA_API_TOKEN
export DAYS=1                                     # 1 = yesterday; bump to backfill

python sync.py
```

To test just the Garmin login + fetch (no Ítaca needed), set `DRY_RUN=1` and it
prints the metrics instead of posting. With 2FA, the first run prompts for a code
on the console and then caches the session (`GARMINTOKENS`, default `~/.garminconnect`),
so later runs don't re-login.

Schedule it daily with cron, or use the bundled GitHub Action
(`.github/workflows/garmin-sync.yml`) — add the four values above as repository
**secrets** (`GARMIN_EMAIL`, `GARMIN_PASSWORD`, `ITACA_URL`, `ITACA_API_TOKEN`).

Re-runs are safe: each day is an idempotent upsert keyed by date.
