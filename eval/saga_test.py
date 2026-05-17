#!/usr/bin/env python3
"""Saga happy-path smoke test against localhost:30080."""

import base64
import json
import sys
import time
import urllib.error
import urllib.request

TS = str(int(time.time()))[-6:]  # unique suffix so re-runs don't clash

BASE = "http://localhost:30080"


def req(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read()
        body_text = raw.decode(errors="replace")
        return e.code, body_text


def decode_uid(token):
    payload = token.split(".")[1]
    payload += "=" * (4 - len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["uid"]


def check(step, status, resp, expected_status, key=None):
    ok = status == expected_status
    symbol = "PASS" if ok else "FAIL"
    print(f"  {symbol} Step {step}: HTTP {status} (expected {expected_status})", end="")
    if key and isinstance(resp, dict):
        print(f"  |  {key}={resp.get(key)}", end="")
    print()
    if not ok:
        print(f"    Response: {resp}")
        sys.exit(1)


def poll(path, token, field, target, retries=10, delay=2):
    for i in range(retries):
        status, resp = req("GET", path, token=token)
        val = resp.get(field) if isinstance(resp, dict) else None
        print(f"    poll [{i+1}/{retries}] {field}={val}")
        if val == target:
            return resp
        time.sleep(delay)
    print(f"  FAIL Timed out waiting for {field}={target}")
    sys.exit(1)


print("=" * 60)
print("Saga Happy Path Test")
print("=" * 60)

# ── 1. Register ──────────────────────────────────────────────
print("\n[1] Register user")
status, resp = req("POST", "/api/auth/register", {
    "name": "Alice Smith",
    "email": f"alice{TS}@saga.test",
    "password": "secret123",
    "phone": f"+1-555-{TS}"
})
check(1, status, resp, 201)
TOKEN = resp["token"]
USER_ID = decode_uid(TOKEN)
print(f"    USER_ID={USER_ID}")

# ── 2. Login ─────────────────────────────────────────────────
print("\n[2] Login")
status, resp = req("POST", "/api/auth/login", {
    "email": f"alice{TS}@saga.test",
    "password": "secret123"
})
check(2, status, resp, 200)
TOKEN = resp["token"]
print(f"    token={TOKEN[:40]}...")

# ── 3. Create event ───────────────────────────────────────────
print("\n[3] Create event")
status, resp = req("POST", "/api/events", {
    "name": "Saga Conf 2026",
    "venue": "Test Hall A",
    "eventDate": "2025-01-10T09:00:00",
    "category": "CONFERENCE",
    "status": "UPCOMING"
}, token=TOKEN)
check(3, status, resp, 201, key="id")
EVENT_ID = resp["id"]
print(f"    EVENT_ID={EVENT_ID}")

# ── 4. Add event session ──────────────────────────────────────
print("\n[4] Add event session")
status, resp = req("POST", f"/api/events/{EVENT_ID}/sessions", {
    "title": "Opening Keynote",
    "speaker": "Dr. Jane Doe",
    "startTime": "2026-09-15T09:00:00",
    "endTime": "2026-09-15T10:30:00",
    "capacity": 200
}, token=TOKEN)
check(4, status, resp, 201, key="id")
SESSION_ID = resp["id"]
print(f"    SESSION_ID={SESSION_ID}")

# ── 5. Place booking ──────────────────────────────────────────
print("\n[5] Place booking")
status, resp = req("POST", "/api/bookings", {
    "userId": USER_ID,
    "contactEmail": f"alice{TS}@saga.test"
}, token=TOKEN)
check(5, status, resp, 200, key="status")
BOOKING_ID = resp["id"]
print(f"    BOOKING_ID={BOOKING_ID}  status={resp.get('status')}")

# ── 6. Add booking items ──────────────────────────────────────
print("\n[6] Add booking items")
status, resp = req("POST", f"/api/bookings/{BOOKING_ID}/items", [
    {
        "sessionId": SESSION_ID,
        "sessionTitle": "Opening Keynote",
        "quantity": 2,
        "unitPrice": 75.0,
        "eventOrder": 1
    }
], token=TOKEN)
check(6, status, resp, 200)
print(f"    items count={len(resp.get('bookingItems', []))}")

# ── 7. Confirm booking ────────────────────────────────────────
print("\n[7] Confirm booking (assigns event, publishes booking.placed)")
status, resp = req("PUT", f"/api/bookings/{BOOKING_ID}/confirm",
                   params={"eventId": EVENT_ID}, token=TOKEN)
check(7, status, resp, 200, key="status")

# ── 8. Issue ticket ───────────────────────────────────────────
print("\n[8] Issue ticket")
status, resp = req("POST", f"/api/tickets/booking/{BOOKING_ID}", {
    "attendeeName": "Alice Smith",
    "ticketCode": f"TKT-2026-SAGA-{TS}"
}, token=TOKEN)
check(8, status, resp, 201, key="id")
TICKET_ID = resp["id"]
print(f"    TICKET_ID={TICKET_ID}  status={resp.get('status')}")

# ── 9a. Scan ticket ───────────────────────────────────────────
print("\n[9a] Scan ticket")
status, resp = req("POST", f"/api/tickets/{TICKET_ID}/scan", {
    "location": "Gate A",
    "scannedAt": "2026-09-15T08:55:00"
}, token=TOKEN)
check("9a", status, resp, 201)

# ── 9b. Mark ticket USED ──────────────────────────────────────
print("\n[9b] Mark ticket USED")
status, resp = req("PUT", f"/api/tickets/{TICKET_ID}", {"status": "USED"}, token=TOKEN)
check("9b", status, resp, 200, key="status")

# ── 10. Check-in booking ──────────────────────────────────────
print("\n[10] Check-in booking ->CHECKED_IN")
status, resp = req("PUT", f"/api/bookings/{BOOKING_ID}", {"status": "CHECKED_IN"}, token=TOKEN)
check(10, status, resp, 200, key="status")

# ── 10b. Advance event to ONGOING (required before complete) ──
print("\n[10b] Advance event status to ONGOING")
status, resp = req("PUT", f"/api/events/{EVENT_ID}/status", {"status": "ONGOING"}, token=TOKEN)
check("10b", status, resp, 200)

# ── 11. Complete booking (saga trigger) ───────────────────────
print("\n[11] Complete booking ->COMPLETING then PAYMENT_PENDING")
status, resp = req("PUT", f"/api/bookings/{BOOKING_ID}/complete", token=TOKEN)
check(11, status, resp, 200, key="status")
print(f"    immediate status={resp.get('status')}  (polling for PAYMENT_PENDING...)")
time.sleep(2)
resp = poll(f"/api/bookings/{BOOKING_ID}", TOKEN, "status", "PAYMENT_PENDING")
print(f"    ->status={resp.get('status')}")

# ── 12. Process payment ───────────────────────────────────────
print("\n[12] Process payment ->POST /api/sales/booking/{id}")
status, resp = req("POST", f"/api/sales/booking/{BOOKING_ID}", {
    "method": "CREDIT_CARD",
    "cardLastFour": "4242"
}, token=TOKEN)
check(12, status, resp, 201, key="status")
print(f"    sale status={resp.get('status')}  amount={resp.get('amount')}")

# ── 13. Poll until PAID ───────────────────────────────────────
print("\n[13] Poll booking until PAID")
time.sleep(2)
resp = poll(f"/api/bookings/{BOOKING_ID}", TOKEN, "status", "PAID")
print(f"    ->status={resp.get('status')}")

print("\n" + "=" * 60)
print("ALL SAGA STEPS PASSED")
print("=" * 60)
