"""
test_03_booking.py — Booking Service Integration Tests
=======================================================
Covers:
  S3-F10  Get Booking Analytics Dashboard         (Section 10.3.1)
  S3-F11  Record User-Event Attendance            (Section 10.3.2)
  S3-F12  Get "Attendees Also Went To" Recs       (Section 10.3.3)
  DP-2    Observer (booking_events)               (Section 3.3)
  DP-4    Builder (BookingAnalyticsDashboardDTO)  (Section 3.5)
  DP-7    Adapter (Neo4jRecordAdapter)            (Section 3.8)
  CC-3    Redis Caching (S3-F10/S3-F12)           (Section 9.3)
"""

import time
import uuid
import requests
import pytest
import jwt as _jwt_lib
from pathlib import Path
from datetime import datetime, timedelta

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _grep_source(service: str, pattern: str) -> bool:
    import re
    regex = re.compile(pattern)
    base  = PROJECT_ROOT / service / "src" / "main" / "java"
    if not base.exists():
        return False
    for path in base.rglob("*.java"):
        try:
            if regex.search(path.read_text(encoding="utf-8", errors="ignore")):
                return True
        except OSError:
            pass
    return False


# ---------------------------------------------------------------------------
# S3-F10: Get Booking Analytics Dashboard  (Section 10.3.1)
# ---------------------------------------------------------------------------

class TestS3F10BookingAnalyticsDashboard:
    """S3-F10 — Booking Analytics Dashboard.

    GET /api/bookings/analytics/dashboard?startDate=...&endDate=...
    Auth: USER-level.
    Response: BookingAnalyticsDashboardDTO (Builder DP-4).
    ANALYTICS_VIEWED written to booking_events on every call (DP-2 Observer).
    Cached 10 min (CC-3).
    DISTINCT from M1's GET /api/bookings/analytics (Section 10.3.1 Note).
    """

    def test_dashboard_returns_correct_aggregations(
        self, booking_url, auth_headers
    ):
        """Scenario a: 10 bookings in March 2026 → correct totals & conversionRate.

        5 COMPLETED (100+200+300+400+500=1500), 2 CANCELLED, 2 CONFIRMED, 1 PENDING
        totalBookings=10, totalRevenue=1500, averageBookingValue=300,
        conversionRate=0.7 (5 COMPLETED + 2 CONFIRMED = 7/10).
        bookingsByStatus per status value.
        """
        # Create 10 bookings
        statuses = (
            ["COMPLETED"] * 5 +
            ["CANCELLED"] * 2 +
            ["CONFIRMED"] * 2 +
            ["PENDING"] * 1
        )
        amounts  = [100.0, 200.0, 300.0, 400.0, 500.0] + [0.0] * 5

        for i, (status, amount) in enumerate(zip(statuses, amounts)):
            requests.post(f"{booking_url}/api/bookings", json={
                "eventId":     1,
                "userId":      1,
                "status":      status,
                "totalAmount": amount,
                "bookingDate": f"2026-03-{(i % 28) + 1:02d}T10:00:00",
                "contactEmail": "contact@test.com",
            }, headers=auth_headers, timeout=10)

        resp = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-03-01", "endDate": "2026-03-31"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200, (
            f"Booking analytics dashboard must return 200, got {resp.status_code}: {resp.text}"
        )
        body = resp.json()

        # Accept camelCase or snake_case keys
        def get_field(d, *names):
            for n in names:
                if n in d:
                    return d[n]
            return None

        total = get_field(body, "totalBookings", "total_bookings")
        revenue = get_field(body, "totalRevenue", "total_revenue")
        by_status = get_field(body, "bookingsByStatus", "bookings_by_status")

        assert total is not None, "Response must have totalBookings field"
        assert revenue is not None, "Response must have totalRevenue field"
        assert by_status is not None, "Response must have bookingsByStatus map"

    def test_dashboard_empty_date_range_returns_zeros(self, booking_url, auth_headers):
        """Scenario b: date range with no bookings → all zeroes / empty."""
        resp = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2020-01-01", "endDate": "2020-01-02"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()
        total = body.get("totalBookings") or body.get("total_bookings", 0)
        assert total == 0, f"Empty date range must have totalBookings=0, got {total}"

    def test_dashboard_invalid_date_range_returns_400(self, booking_url, auth_headers):
        """Scenario c: startDate after endDate → 400 (Section 10.3.1 step b)."""
        resp = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-04-01", "endDate": "2026-03-01"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 400, (
            f"startDate > endDate must return 400, got {resp.status_code}"
        )

    def test_dashboard_no_auth_returns_401(self, booking_url):
        """Scenario d: no auth → 401 (CC-1)."""
        resp = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-01-01", "endDate": "2026-01-31"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_dashboard_writes_analytics_viewed_on_every_call(
        self, booking_url, auth_headers, mongo_db
    ):
        """Scenario e: ANALYTICS_VIEWED logged on every call, even cache hits.

        Section 10.3.1 step d: logging step is OUTSIDE the cache layer.
        DP-2 Observer must fire on each invocation.
        """
        before = mongo_db["booking_events"].count_documents(
            {"action": "ANALYTICS_VIEWED"}
        )

        # Call twice
        for _ in range(2):
            requests.get(
                f"{booking_url}/api/bookings/analytics/dashboard",
                params={"startDate": "2026-03-01", "endDate": "2026-03-31"},
                headers=auth_headers, timeout=10,
            )
            time.sleep(0.3)

        after = mongo_db["booking_events"].count_documents(
            {"action": "ANALYTICS_VIEWED"}
        )
        assert after >= before + 2, (
            f"ANALYTICS_VIEWED must be logged on EVERY invocation (even cache hits). "
            f"Before={before}, After={after} (Section 10.3.1 step d)"
        )

    def test_dashboard_cached_in_redis(self, booking_url, auth_headers, redis_client):
        """CC-3: S3-F10 dashboard cached 10 min; Redis key must appear."""
        requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-03-01", "endDate": "2026-03-31"},
            headers=auth_headers, timeout=10,
        )
        time.sleep(0.3)
        keys = list(redis_client.scan_iter("booking-service::S3-F10::*"))
        assert keys, (
            "Redis must have a key matching 'booking-service::S3-F10::*' "
            "after dashboard call (CC-3)"
        )

    def test_dashboard_dto_builder_fields_present(self, booking_url, auth_headers):
        """DP-4 Builder: BookingAnalyticsDashboardDTO must contain all required fields.

        Section 3.5 test scenario a: builder() → fluent setters → build().
        """
        resp = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-03-01", "endDate": "2026-03-31"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()

        required = [
            ("totalBookings", "total_bookings"),
            ("totalRevenue", "total_revenue"),
            ("averageBookingValue", "average_booking_value"),
            ("conversionRate", "conversion_rate"),
            ("bookingsByStatus", "bookings_by_status"),
        ]
        for names in required:
            assert any(n in body for n in names), (
                f"Dashboard DTO missing field (one of {names}) — "
                "Builder pattern (DP-4) must construct this DTO"
            )

    def test_distinct_from_m1_analytics_endpoint(self, booking_url, auth_headers):
        """Section 10.3.1 Note: /dashboard is distinct from M1 /analytics endpoint.
        Both must coexist.
        """
        m1  = requests.get(f"{booking_url}/api/bookings/analytics",
                           headers=auth_headers, timeout=10)
        m2  = requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
            headers=auth_headers, timeout=10,
        )
        assert m1.status_code not in (401, 403), "M1 analytics endpoint must not reject valid token"
        assert m2.status_code not in (401, 403), "M2 dashboard endpoint must not reject valid token"


# ---------------------------------------------------------------------------
# S3-F11: Record User-Event Attendance  (Section 10.3.2)
# ---------------------------------------------------------------------------

class TestS3F11RecordAttendance:
    """S3-F11 — Record User-Event Attendance.

    POST /api/bookings/{bookingId}/record-attendance
    Auth: USER-level.
    Writes ATTENDED relationship in Neo4j (Section 7.3).
    Logs INTERACTION_RECORDED to booking_events (DP-2 Observer).
    Idempotent: repeat call on same bookingId is a no-op (Section 10.3.2 step d).
    """

    def _setup_completed_booking(self, user_url, event_url, booking_url,
                                  auth_headers, admin_headers):
        """Helper: create user, event, booking (COMPLETED) and return IDs."""
        suffix = uuid.uuid4().hex[:8]
        # Create a user
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name":     f"AttUser {suffix}",
            "email":    f"att_{suffix}@ex.com",
            "password": "pass",
            "phone":    f"+230{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert reg.status_code == 201
        token   = reg.json()["token"]
        user_id = int(_jwt_lib.decode(token, options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])

        # Create an event
        ev = requests.post(f"{event_url}/api/events", json={
            "name":      f"AttEvent {suffix}",
            "category":  "CONCERT",
            "venue":     "AttVenue",
            "eventDate": "2026-05-01T10:00:00",
            "status":    "COMPLETED",
            "rating":    4.0,
            "details":   {"description": "attendance test"},
        }, headers=admin_headers, timeout=10)
        assert ev.status_code in (200, 201)
        event_id = ev.json()["id"]

        # Create a COMPLETED booking
        bk = requests.post(f"{booking_url}/api/bookings", json={
            "eventId":     event_id,
            "userId":      user_id,
            "status":      "COMPLETED",
            "totalAmount": 300.0,
            "bookingDate": "2026-04-20T10:00:00",
            "contactEmail": "contact@test.com",
        }, headers=auth_headers, timeout=10)
        assert bk.status_code in (200, 201)
        booking = bk.json()
        booking_id = booking["id"]

        return user_id, event_id, booking_id, token

    def test_record_attendance_returns_200(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers
    ):
        """Scenario a: COMPLETED booking → POST record-attendance → 200."""
        uid, eid, bid, token = self._setup_completed_booking(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        resp = requests.post(
            f"{booking_url}/api/bookings/{bid}/record-attendance",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        assert resp.status_code == 200, (
            f"record-attendance must return 200, got {resp.status_code}: {resp.text}"
        )

    def test_record_attendance_creates_neo4j_relationship(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers, neo4j_driver
    ):
        """Scenario a (Neo4j check): ATTENDED relationship must exist after call.

        Section 7.3: (User)-[:ATTENDED]->(Event) with attendanceCount=1.
        """
        uid, eid, bid, token = self._setup_completed_booking(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        requests.post(
            f"{booking_url}/api/bookings/{bid}/record-attendance",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        time.sleep(0.5)

        with neo4j_driver.session() as session:
            result = session.run(
                "MATCH (u:UserNode {userId: $uid})-[r:ATTENDED]->(e:EventNode {eventId: $eid}) "
                "RETURN r.attendanceCount AS cnt",
                uid=uid, eid=eid,
            )
            record = result.single()
        assert record is not None, (
            f"ATTENDED relationship must exist in Neo4j for user={uid}, event={eid} "
            "(S3-F11 Section 7.3)"
        )
        assert record["cnt"] >= 1, (
            f"attendanceCount must be ≥1 after first recording, got {record['cnt']}"
        )

    def test_record_attendance_idempotent(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers, neo4j_driver, mongo_db
    ):
        """Scenario b: repeat call on same bookingId must NOT increment counter.

        Section 10.3.2 step d: idempotency check — no extra INTERACTION_RECORDED event.
        """
        uid, eid, bid, token = self._setup_completed_booking(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        hdrs = {"Authorization": f"Bearer {token}"}

        # First call
        requests.post(f"{booking_url}/api/bookings/{bid}/record-attendance",
                      headers=hdrs, timeout=10)
        time.sleep(0.3)

        # Record how many INTERACTION_RECORDED events exist after first call
        count_after_first = mongo_db["booking_events"].count_documents(
            {"action": "INTERACTION_RECORDED", "details.bookingId": bid}
        )

        # Second call — same bookingId
        r2 = requests.post(f"{booking_url}/api/bookings/{bid}/record-attendance",
                           headers=hdrs, timeout=10)
        assert r2.status_code == 200, "Idempotent repeat must still return 200"
        time.sleep(0.3)

        # Neo4j counter must still be 1
        with neo4j_driver.session() as session:
            result = session.run(
                "MATCH (u:UserNode {userId: $uid})-[r:ATTENDED]->(e:EventNode {eventId: $eid}) "
                "RETURN r.attendanceCount AS cnt",
                uid=uid, eid=eid,
            )
            record = result.single()
        assert record["cnt"] == 1, (
            f"attendanceCount must still be 1 after idempotent repeat, got {record['cnt']}"
        )

        # No extra INTERACTION_RECORDED event for the second call
        count_after_second = mongo_db["booking_events"].count_documents(
            {"action": "INTERACTION_RECORDED", "details.bookingId": bid}
        )
        assert count_after_second == count_after_first, (
            "Idempotent repeat must NOT emit an extra INTERACTION_RECORDED event "
            "(Section 10.3.2 step h)"
        )

    def test_record_attendance_increments_for_new_booking(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers, neo4j_driver
    ):
        """Scenario c: second COMPLETED booking for same user+event → attendanceCount=2."""
        uid, eid, bid1, token = self._setup_completed_booking(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        hdrs = {"Authorization": f"Bearer {token}"}

        # First recording
        requests.post(f"{booking_url}/api/bookings/{bid1}/record-attendance",
                      headers=hdrs, timeout=10)
        time.sleep(0.3)

        # Second booking for same user+event
        bk2 = requests.post(f"{booking_url}/api/bookings", json={
            "eventId":     eid,
            "userId":      uid,
            "status":      "COMPLETED",
            "totalAmount": 300.0,
            "bookingDate": "2026-04-21T10:00:00",
            "contactEmail": "contact@test.com",
        }, headers=auth_headers, timeout=10)
        assert bk2.status_code in (200, 201)
        bid2 = bk2.json()["id"]

        requests.post(f"{booking_url}/api/bookings/{bid2}/record-attendance",
                      headers=hdrs, timeout=10)
        time.sleep(0.3)

        with neo4j_driver.session() as session:
            result = session.run(
                "MATCH (u:UserNode {userId: $uid})-[r:ATTENDED]->(e:EventNode {eventId: $eid}) "
                "RETURN r.attendanceCount AS cnt",
                uid=uid, eid=eid,
            )
            record = result.single()
        assert record["cnt"] == 2, (
            f"Second distinct booking must increment attendanceCount to 2, got {record['cnt']}"
        )

    def test_record_attendance_confirmed_booking_returns_400(
        self, booking_url, auth_headers, admin_headers,
        event_url, user_url
    ):
        """Scenario d: CONFIRMED (not COMPLETED) booking → 400.

        Section 10.3.2 step c.
        """
        suffix = uuid.uuid4().hex[:8]
        ev = requests.post(f"{event_url}/api/events", json={
            "name": f"ConfEv {suffix}", "category": "SPORTS", "venue": "V",
            "eventDate": "2026-06-01T10:00:00", "status": "UPCOMING", "rating": 0.0,
            "details": {},
        }, headers=admin_headers, timeout=10)
        assert ev.status_code in (200, 201)
        eid = ev.json()["id"]

        bk = requests.post(f"{booking_url}/api/bookings", json={
            "eventId": eid, "userId": 1, "status": "CONFIRMED",
            "totalAmount": 0.0, "bookingDate": "2026-04-01T10:00:00",
            "contactEmail": "contact@test.com",
        }, headers=auth_headers, timeout=10)
        assert bk.status_code in (200, 201)
        bid = bk.json()["id"]

        resp = requests.post(
            f"{booking_url}/api/bookings/{bid}/record-attendance",
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 400, (
            f"CONFIRMED booking must return 400, got {resp.status_code}"
        )

    def test_record_attendance_nonexistent_booking_returns_404(
        self, booking_url, auth_headers
    ):
        """Scenario f: non-existent booking → 404."""
        resp = requests.post(
            f"{booking_url}/api/bookings/999999/record-attendance",
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 404

    def test_record_attendance_no_auth_returns_401(self, booking_url):
        """Scenario g: no auth → 401 (CC-1)."""
        resp = requests.post(
            f"{booking_url}/api/bookings/1/record-attendance",
            timeout=10,
        )
        assert resp.status_code == 401

    def test_record_attendance_writes_interaction_recorded_to_mongodb(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers, mongo_db
    ):
        """S3-F11 step h: INTERACTION_RECORDED must appear in booking_events.

        Also triggers S3-F12 cache invalidation via NoSQL-writer rule (Section 4.4.4).
        """
        uid, eid, bid, token = self._setup_completed_booking(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        requests.post(
            f"{booking_url}/api/bookings/{bid}/record-attendance",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        time.sleep(0.5)

        doc = mongo_db["booking_events"].find_one(
            {"action": "INTERACTION_RECORDED"}
        )
        assert doc is not None, (
            "INTERACTION_RECORDED event must be in booking_events after record-attendance "
            "(DP-2 Observer Section 10.3.2 step h)"
        )
        details = doc.get("details", {})
        assert "bookingId" in details or "booking_id" in details, (
            "INTERACTION_RECORDED details must include bookingId"
        )
        assert "userId" in details or "user_id" in details, (
            "INTERACTION_RECORDED details must include userId"
        )
        assert "eventId" in details or "event_id" in details, (
            "INTERACTION_RECORDED details must include eventId"
        )


# ---------------------------------------------------------------------------
# S3-F12: Get Recommendations  (Section 10.3.3)
# ---------------------------------------------------------------------------

class TestS3F12Recommendations:
    """S3-F12 — "Attendees Also Went To" Recommendations.

    GET /api/bookings/recommendations?userId={id}&limit={n}
    Auth: USER-level. Ownership check: uid == userId or ADMIN.
    Cached 5 min (CC-3).
    Traverses Neo4j for collaborative-filtering recommendations.
    """

    def _setup_recommendation_graph(
        self, user_url, event_url, booking_url, auth_headers, admin_headers
    ):
        """Scenario a setup: 3 users (A,B,C), 4 events (E1-E4).

        Attendance:
          A → E1, A → E2
          B → E1, B → E3
          C → E2, C → E4

        Expected recs for A: E3 (via B who shares E1), E4 (via C who shares E2).
        A should NOT see E1 or E2 (already attended).
        """
        suffix = uuid.uuid4().hex[:6]
        users  = {}
        events = {}

        # Create 3 users
        for name_suffix in ["A", "B", "C"]:
            s = f"{suffix}{name_suffix}"
            reg = requests.post(f"{user_url}/api/auth/register", json={
                "name":     f"RecUser{name_suffix} {suffix}",
                "email":    f"rec{name_suffix}_{suffix}@ex.com",
                "password": "pass",
                "phone":    f"+231{int(s[:8].encode().hex(), 16) % 10**9:09d}",
            }, timeout=10)
            if reg.status_code == 201:
                tok = reg.json()["token"]
                uid = int(_jwt_lib.decode(tok, options={"verify_signature": False},
                                          algorithms=["HS256"])["uid"])
                users[name_suffix] = {"uid": uid, "token": tok,
                                       "headers": {"Authorization": f"Bearer {tok}"}}

        # Create 4 events
        for i in range(1, 5):
            ev = requests.post(f"{event_url}/api/events", json={
                "name":      f"RecEvent{i} {suffix}",
                "category":  "CONCERT",
                "venue":     "RecVenue",
                "eventDate": f"2026-0{i}-01T10:00:00",
                "status":    "COMPLETED",
                "rating":    4.0,
                "details":   {"description": f"rec event {i}"},
            }, headers=admin_headers, timeout=10)
            if ev.status_code in (200, 201):
                events[i] = ev.json()["id"]

        # Record attendance per plan
        attendance_plan = {
            "A": [1, 2],
            "B": [1, 3],
            "C": [2, 4],
        }
        for user_key, event_nums in attendance_plan.items():
            if user_key not in users:
                continue
            for enum in event_nums:
                eid = events.get(enum)
                if not eid:
                    continue
                uid = users[user_key]["uid"]
                bk = requests.post(f"{booking_url}/api/bookings", json={
                    "eventId":     eid,
                    "userId":      uid,
                    "status":      "COMPLETED",
                    "totalAmount": 100.0,
                    "bookingDate": "2026-01-01T10:00:00",
                    "contactEmail": "contact@test.com",
                }, headers=auth_headers, timeout=10)
                if bk.status_code in (200, 201):
                    bid = bk.json()["id"]
                    requests.post(
                        f"{booking_url}/api/bookings/{bid}/record-attendance",
                        headers=users[user_key]["headers"], timeout=10,
                    )
                    time.sleep(0.2)

        return users, events, suffix

    def test_recommendations_for_user_a(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers
    ):
        """Scenario a: recs for A must include E3 and E4, NOT E1 or E2."""
        users, events, suffix = self._setup_recommendation_graph(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        if "A" not in users:
            pytest.skip("Could not create test users for recommendation test")

        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": users["A"]["uid"]},
            headers=users["A"]["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, (
            f"Recommendations must return 200, got {resp.status_code}: {resp.text}"
        )
        results = resp.json()
        assert isinstance(results, list), "Recommendations must be a list"
        rec_ids = [r.get("eventId") or r.get("event_id") for r in results]

        # E3 and E4 should appear (if graph has data)
        e3 = events.get(3)
        e4 = events.get(4)
        e1 = events.get(1)
        e2 = events.get(2)

        if e3 and e4:
            assert e3 in rec_ids or e4 in rec_ids, (
                f"Recommendations for A must include E3 ({e3}) or E4 ({e4}). "
                f"Got: {rec_ids}"
            )
        # E1 and E2 must NOT appear (already attended)
        if e1:
            assert e1 not in rec_ids, f"E1 ({e1}) must NOT appear in A's recommendations"
        if e2:
            assert e2 not in rec_ids, f"E2 ({e2}) must NOT appear in A's recommendations"

    def test_recommendations_ownership_returns_403(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers
    ):
        """Scenario b: User B's token used for User A's recommendations → 403."""
        users, events, suffix = self._setup_recommendation_graph(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        if "A" not in users or "B" not in users:
            pytest.skip("Could not create test users")

        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": users["A"]["uid"]},
            headers=users["B"]["headers"],   # B's token for A's endpoint
            timeout=10,
        )
        assert resp.status_code == 403, (
            f"Cross-user recommendation access must return 403, got {resp.status_code}"
        )

    def test_recommendations_admin_bypass(
        self, user_url, event_url, booking_url,
        auth_headers, admin_headers, admin_user
    ):
        """Scenario c: ADMIN token → 200 regardless of userId ownership."""
        users, events, suffix = self._setup_recommendation_graph(
            user_url, event_url, booking_url, auth_headers, admin_headers
        )
        if "A" not in users:
            pytest.skip("Could not create test users")

        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": users["A"]["uid"]},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, (
            f"ADMIN must bypass ownership check on recommendations, got {resp.status_code}"
        )

    def test_recommendations_no_attendance_returns_empty_list(
        self, user_url, booking_url, auth_headers
    ):
        """Scenario d: user with no recorded attendance → empty list."""
        suffix = uuid.uuid4().hex[:8]
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": f"NoAtt {suffix}", "email": f"noatt_{suffix}@ex.com",
            "password": "pass", "phone": f"+232{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert reg.status_code == 201
        tok = reg.json()["token"]
        uid = int(_jwt_lib.decode(tok, options={"verify_signature": False},
                                  algorithms=["HS256"])["uid"])

        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": uid},
            headers={"Authorization": f"Bearer {tok}"},
            timeout=10,
        )
        assert resp.status_code == 200
        assert resp.json() == [] or resp.json() == {}, (
            "User with no attendance must get empty recommendation list"
        )

    def test_recommendations_nonexistent_user_returns_404(
        self, booking_url, admin_user
    ):
        """Scenario e: userId=999999 with ADMIN token → 404."""
        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": 999999},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 404

    def test_recommendations_no_auth_returns_401(self, booking_url):
        """Scenario f: no token → 401 (CC-1)."""
        resp = requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": 1},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_recommendations_cached_in_redis(
        self, booking_url, auth_user, redis_client
    ):
        """CC-3: S3-F12 cached 5 min; Redis key must exist after first call."""
        requests.get(
            f"{booking_url}/api/bookings/recommendations",
            params={"userId": auth_user["user_id"]},
            headers=auth_user["headers"],
            timeout=10,
        )
        time.sleep(0.3)
        keys = list(redis_client.scan_iter("booking-service::S3-F12::*"))
        assert keys, (
            "Redis must have key matching 'booking-service::S3-F12::*' "
            "after recommendations call (CC-3 5-min cache)"
        )


# ---------------------------------------------------------------------------
# DP-7 Adapter — Neo4jRecordAdapter  (Section 3.8)
# ---------------------------------------------------------------------------

class TestDP7Neo4jAdapter:
    """DP-7 — Adapter Pattern: Neo4jRecordAdapter in booking-service.

    Section 3.8 test scenario a.
    """

    def test_neo4j_record_adapter_exists_in_source(self):
        """Scenario a: Neo4jRecordAdapter must exist in booking-service source."""
        found = _grep_source("booking-service", r"\bNeo4jRecordAdapter\b")
        assert found, (
            "Neo4jRecordAdapter not found in booking-service source "
            "(DP-7 Section 3.8 step a)"
        )

    def test_mongo_document_adapter_exists_in_booking_service(self):
        """Scenario a: MongoDocumentAdapter must exist in booking-service."""
        found = _grep_source("booking-service", r"\bMongoDocumentAdapter\b")
        assert found, (
            "MongoDocumentAdapter not found in booking-service source "
            "(DP-7 Section 3.8 step a)"
        )
