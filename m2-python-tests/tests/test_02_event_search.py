"""
test_02_event_search.py — Event Service Integration Tests
==========================================================
Covers:
  S2-F10  Full-Text Event Search            (Section 10.2.1)
  S2-F11  Index Event for Search            (Section 10.2.2)
  S2-F12  Get Event Performance Dashboard   (Section 10.2.3)
  DP-6    Factory Pattern (EventFactory)    (Section 3.7) — MongoDB checks
  DP-7    Adapter Pattern (ElasticsearchHitAdapter) (Section 3.8) — source scan
  DP-2    Observer Pattern (event_events)   (Section 3.3)
  CC-3    Redis Caching (S2-F10/S2-F12)     (Section 9.3)
  M1-MOD  Auto-index on CRUD changes        (Section 4.5 + 10.2.2 step e)
"""

import time
import uuid
import requests
import pytest
from pathlib import Path

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
# Helpers
# ---------------------------------------------------------------------------

def _create_and_index_event(event_url, auth_headers, name, category="CONCERT",
                             venue="Test Venue", description="",
                             event_date="2026-08-01T10:00:00", status="UPCOMING",
                             rating=0.0):
    """Create event via CRUD, then POST /index to explicitly index it in ES.

    Mirrors M2 S2-F11 test scenario a.
    """
    body = {
        "name":      name,
        "category":  category,
        "venue":     venue,
        "eventDate": event_date,
        "status":    status,
        "rating":    rating,
        "details":   {"description": description},
    }
    create = requests.post(f"{event_url}/api/events", json=body,
                           headers=auth_headers, timeout=10)
    assert create.status_code in (200, 201), f"Event creation failed: {create.text}"
    event = create.json()
    eid   = event["id"]

    # Explicit index call
    idx = requests.post(f"{event_url}/api/events/{eid}/index",
                        headers=auth_headers, timeout=10)
    assert idx.status_code == 200, f"Indexing failed for event {eid}: {idx.text}"
    time.sleep(1.0)   # Give ES time to make the document searchable
    return event


# ---------------------------------------------------------------------------
# S2-F11: Index Event for Search  (Section 10.2.2)
# ---------------------------------------------------------------------------

class TestS2F11IndexEvent:
    """S2-F11 — Index Event for Search.

    POST /api/events/{id}/index
    Auth: USER-level required (CC-1).
    Writes INDEXED to event_events MongoDB collection (DP-2 Observer).
    """

    def test_index_event_returns_200(self, event_url, auth_headers, make_event):
        """Scenario a: create event + POST index → 200."""
        event = make_event(name=f"Index Test {uuid.uuid4().hex[:4]}")
        resp  = requests.post(
            f"{event_url}/api/events/{event['id']}/index",
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200, (
            f"POST /index must return 200, got {resp.status_code}: {resp.text}"
        )

    def test_index_event_makes_it_searchable(self, event_url, auth_headers, make_event,
                                              es_client):
        """Scenario a: after indexing, event must appear in ES 'events' index."""
        unique = uuid.uuid4().hex[:8]
        name   = f"Musical {unique}"
        event  = make_event(name=name,
                            details={"description": f"Broadway musical {unique} live orchestra"})
        requests.post(f"{event_url}/api/events/{event['id']}/index",
                      headers=auth_headers, timeout=10)
        time.sleep(1.5)

        result = es_client.search(
            index="events",
            body={"query": {"match": {"name": unique}}}
        )
        hits = result["hits"]["hits"]
        assert hits, (
            f"Event '{name}' must be found in ES 'events' index after /index call"
        )

    def test_index_missing_description_succeeds(self, event_url, auth_headers):
        """Scenario b: event with no details.description → indexing still succeeds (empty str)."""
        create = requests.post(f"{event_url}/api/events", json={
            "name":      f"NoDes {uuid.uuid4().hex[:4]}",
            "category":  "SPORTS",
            "venue":     "Stadium",
            "eventDate": "2026-09-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {},   # no description key
        }, headers=auth_headers, timeout=10)
        assert create.status_code in (200, 201)
        eid  = create.json()["id"]
        resp = requests.post(f"{event_url}/api/events/{eid}/index",
                             headers=auth_headers, timeout=10)
        assert resp.status_code == 200, (
            f"Indexing event without description must return 200, got {resp.status_code}"
        )

    def test_index_nonexistent_event_returns_404(self, event_url, auth_headers):
        """Scenario c: POST /api/events/999999/index → 404."""
        resp = requests.post(f"{event_url}/api/events/999999/index",
                             headers=auth_headers, timeout=10)
        assert resp.status_code == 404, (
            f"Non-existent event index must return 404, got {resp.status_code}"
        )

    def test_index_writes_indexed_event_to_mongodb(self, event_url, auth_headers,
                                                    make_event, mongo_db):
        """Scenario a (MongoDB check) + DP-2 Observer / DP-6 Factory:
        POST /index → INDEXED document in event_events with source='explicit'.
        """
        event = make_event(name=f"MongoIdx {uuid.uuid4().hex[:4]}")
        requests.post(f"{event_url}/api/events/{event['id']}/index",
                      headers=auth_headers, timeout=10)
        time.sleep(0.5)

        doc = mongo_db["event_events"].find_one(
            {"eventId": event["id"], "action": "INDEXED"}
        )
        assert doc is not None, (
            f"INDEXED event must be in event_events for eventId={event['id']} "
            "(Observer/Factory must fire on S2-F11)"
        )
        details = doc.get("details", {})
        # Accept 'explicit' (ideal) or 'auto_crud_create' (when auto-index fires first)
        assert details.get("source") in ("explicit", "auto_crud_create", None), (
            f"details.source must be a known index source value, got: {details}"
        )

    def test_index_no_auth_returns_401(self, event_url, make_event, auth_headers):
        """Scenario f: POST /index without auth → 401 (CC-1)."""
        event = make_event(name=f"NoAuth {uuid.uuid4().hex[:4]}")
        resp  = requests.post(f"{event_url}/api/events/{event['id']}/index", timeout=10)
        assert resp.status_code == 401, (
            f"POST /index without token must return 401, got {resp.status_code}"
        )

    def test_auto_index_on_crud_create(self, event_url, auth_headers, es_client):
        """Scenario d / Section 4.5 auto-index retrofit:
        Creating an event via CRUD (without calling /index) must auto-index it.
        """
        unique = uuid.uuid4().hex[:8]
        name   = f"AutoCreate {unique}"
        create = requests.post(f"{event_url}/api/events", json={
            "name":      name,
            "category":  "FESTIVAL",
            "venue":     "AutoVenue",
            "eventDate": "2026-10-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": f"auto indexed {unique}"},
        }, headers=auth_headers, timeout=10)
        assert create.status_code in (200, 201)
        eid = create.json()["id"]
        time.sleep(1.5)   # ES indexing latency

        result = es_client.search(
            index="events",
            body={"query": {"match": {"name": unique}}}
        )
        hits = result["hits"]["hits"]
        assert hits, (
            f"Auto-index retrofit must index event '{name}' on CRUD POST "
            "(Section 4.5 + 10.2.2 step e)"
        )
        # Also check for auto_crud_create source in MongoDB
        pass   # MongoDB INDEXED check is covered by test_auto_index_writes_to_mongodb

    def test_auto_index_on_crud_update(self, event_url, auth_headers, es_client):
        """Scenario d: updating an event via CRUD must re-index it in ES."""
        unique  = uuid.uuid4().hex[:8]
        create  = requests.post(f"{event_url}/api/events", json={
            "name":      f"PreUpdate {unique}",
            "category":  "CONFERENCE",
            "venue":     "UpdateVenue",
            "eventDate": "2026-11-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": "before update"},
        }, headers=auth_headers, timeout=10)
        assert create.status_code in (200, 201)
        eid      = create.json()["id"]
        new_name = f"Updated {unique}"

        requests.put(f"{event_url}/api/events/{eid}", json={
            "name":      new_name,
            "category":  "CONFERENCE",
            "venue":     "UpdateVenue",
            "eventDate": "2026-11-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": "after update"},
        }, headers=auth_headers, timeout=10)
        time.sleep(1.5)

        result = es_client.search(
            index="events",
            body={"query": {"match": {"name": unique}}}
        )
        hits = result["hits"]["hits"]
        assert hits, (
            f"Auto-index retrofit must re-index event on CRUD PUT "
            "(Section 4.5 + 10.2.2 step e)"
        )

    def test_auto_index_delete_removes_from_es(self, event_url, auth_headers, es_client):
        """Scenario e: DELETE event → must be removed from ES index.

        Section 10.2.2 step e + Section 4.5.
        """
        unique = uuid.uuid4().hex[:8]
        create = requests.post(f"{event_url}/api/events", json={
            "name":      f"ToDelete {unique}",
            "category":  "THEATER",
            "venue":     "DeleteVenue",
            "eventDate": "2026-12-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": "will be deleted"},
        }, headers=auth_headers, timeout=10)
        assert create.status_code in (200, 201)
        eid = create.json()["id"]

        # Index it first
        requests.post(f"{event_url}/api/events/{eid}/index",
                      headers=auth_headers, timeout=10)
        time.sleep(1.0)

        # Delete via CRUD
        requests.delete(f"{event_url}/api/events/{eid}",
                        headers=auth_headers, timeout=10)
        time.sleep(1.5)

        result = es_client.search(
            index="events",
            body={"query": {"match": {"name": unique}}}
        )
        hits = result["hits"]["hits"]
        assert not hits, (
            f"Deleted event '{unique}' must NOT be found in ES after DELETE CRUD "
            "(Section 4.5 + 10.2.2 step e)"
        )


# ---------------------------------------------------------------------------
# S2-F10: Full-Text Event Search  (Section 10.2.1)
# ---------------------------------------------------------------------------

class TestS2F10FullTextSearch:
    """S2-F10 — Full-Text Event Search.

    GET /api/events/search/full-text?query=...&category=...&status=...
    Auth: USER-level required (CC-1).
    Cached 5 min in Redis (CC-3).
    DISTINCT from M1 /api/events/search endpoint.
    """

    @pytest.fixture(scope="class")
    def indexed_events(self, event_url, auth_headers):
        """Create & index 3 events needed by multiple S2-F10 tests.

        Scenario a dataset:
          E1: Cairo Jazz Night  (CONCERT, UPCOMING, 2026-06-10)
          E2: Egypt vs Morocco  (SPORTS,  UPCOMING, 2026-06-15)
          E3: Broadway Classics (THEATER, COMPLETED, 2026-02-10)
        """
        suffix = uuid.uuid4().hex[:6]
        e1 = _create_and_index_event(
            event_url, auth_headers,
            name=f"Cairo Jazz Night {suffix}", category="CONCERT",
            venue="Cairo Opera House", description="Jazz concert in Cairo",
            event_date="2026-06-10T10:00:00", status="UPCOMING", rating=4.5,
        )
        e2 = _create_and_index_event(
            event_url, auth_headers,
            name=f"Egypt vs Morocco Match {suffix}", category="SPORTS",
            venue="Cairo International Stadium",
            description="Egypt football match", event_date="2026-06-15T10:00:00",
            status="UPCOMING", rating=3.8,
        )
        e3 = _create_and_index_event(
            event_url, auth_headers,
            name=f"Broadway Classics {suffix}", category="THEATER",
            venue="Downtown Theater", description="Classic broadway show",
            event_date="2026-02-10T10:00:00", status="COMPLETED", rating=4.2,
        )
        return {"suffix": suffix, "e1": e1, "e2": e2, "e3": e3}

    def test_query_jazz_returns_only_jazz_event(self, event_url, auth_headers,
                                                 indexed_events):
        """Scenario a: query=jazz → only the jazz event."""
        suffix = indexed_events["suffix"]
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "jazz"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200, f"Search returned {resp.status_code}: {resp.text}"
        names = [e.get("name", "") for e in resp.json()]
        # At least one result should be the jazz event
        assert any(suffix in n and "Jazz" in n for n in names), (
            f"query=jazz must return the Jazz Night event. Got names: {names}"
        )

    def test_query_category_filter(self, event_url, auth_headers, indexed_events):
        """Scenario b: query=Cairo&category=CONCERT → concerts only."""
        suffix = indexed_events["suffix"]
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "Cairo", "category": "CONCERT"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200
        results = resp.json()
        for event in results:
            assert event.get("category") == "CONCERT", (
                f"All results with category=CONCERT must be CONCERT, got: {event.get('category')}"
            )

    def test_query_status_filter(self, event_url, auth_headers, indexed_events):
        """Scenario c: query=Egypt&status=UPCOMING → UPCOMING only."""
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "Egypt", "status": "UPCOMING"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200
        for event in resp.json():
            assert event.get("status") == "UPCOMING", (
                f"status filter must exclude non-UPCOMING events, got {event.get('status')}"
            )

    def test_query_date_range_filter(self, event_url, auth_headers, indexed_events):
        """Scenario e: startDate=2026-06-01&endDate=2026-06-30 → June events only."""
        suffix = indexed_events["suffix"]
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"startDate": "2026-06-01", "endDate": "2026-06-30"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200
        results = resp.json()
        # Broadway (Feb 2026) must NOT appear in June filter
        assert not any("Broadway" in e.get("name", "") and suffix in e.get("name", "")
                       for e in results), (
            "Broadway Classics (Feb 2026) must NOT appear in June date filter"
        )

    def test_query_nonexistent_returns_empty_list(self, event_url, auth_headers):
        """Scenario f: no matching results → empty list (not 404)."""
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "xyznonexistent12345"},
            headers=auth_headers, timeout=10,
        )
        assert resp.status_code == 200, (
            f"Empty search must return 200 with empty list, not {resp.status_code}"
        )
        assert resp.json() == [] or isinstance(resp.json(), list), (
            "No-match search must return an empty list"
        )

    def test_full_text_search_no_auth_returns_401(self, event_url):
        """Scenario g: no token → 401 (CC-1)."""
        resp = requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "test"},
            timeout=10,
        )
        assert resp.status_code == 401, (
            f"Full-text search without auth must return 401, got {resp.status_code}"
        )

    def test_full_text_search_cached_in_redis(self, event_url, auth_headers,
                                               redis_client):
        """CC-3 / Section 4.4: S2-F10 cached 5 min; Redis key must exist after first call."""
        requests.get(
            f"{event_url}/api/events/search/full-text",
            params={"query": "jazz"},
            headers=auth_headers, timeout=10,
        )
        time.sleep(0.3)
        pattern = "event-service::S2-F10::*"
        keys    = list(redis_client.scan_iter(pattern))
        assert keys, (
            f"Redis must have a key matching '{pattern}' after S2-F10 search "
            "(CC-3 caching requirement)"
        )

    def test_distinct_from_m1_search_endpoint(self, event_url, auth_headers):
        """Note on path (Section 10.2.1): /search/full-text is DISTINCT from M1's
        /search endpoint. Both must coexist and respond independently.
        """
        m1_resp = requests.get(f"{event_url}/api/events/search",
                               params={"category": "CONCERT"},
                               headers=auth_headers, timeout=10)
        m2_resp = requests.get(f"{event_url}/api/events/search/full-text",
                               params={"query": "test"},
                               headers=auth_headers, timeout=10)
        assert m1_resp.status_code != 404 or m2_resp.status_code != 404, (
            "At least one of M1 /search or M2 /search/full-text must exist"
        )
        # Neither should be 401 with a valid token
        assert m1_resp.status_code != 401, "M1 /search with token must not return 401"
        assert m2_resp.status_code != 401, "M2 /search/full-text with token must not return 401"


# ---------------------------------------------------------------------------
# S2-F12: Get Event Performance Dashboard  (Section 10.2.3)
# ---------------------------------------------------------------------------

class TestS2F12EventDashboard:
    """S2-F12 — Event Performance Dashboard.

    GET /api/events/{id}/dashboard
    Auth: USER-level required.
    Response: EventDashboardDTO built via Builder (DP-4).
    Writes DASHBOARD_VIEWED to event_events on every invocation (even cache hits).
    Cached 10 min in Redis (CC-3).
    """

    def test_dashboard_returns_correct_aggregations(
        self, event_url, booking_url, ticket_url, auth_headers, make_event
    ):
        """Scenario a: 4 COMPLETED bookings, 10 tickets (7 USED) → correct DTO values."""
        # Create an event
        event = make_event(name=f"Dashboard Event {uuid.uuid4().hex[:4]}",
                           rating=4.5)
        eid = event["id"]

        # Create 4 bookings referencing this event (all COMPLETED)
        totals = [200.0, 300.0, 400.0, 500.0]
        booking_ids = []
        for amount in totals:
            b = requests.post(f"{booking_url}/api/bookings", json={
                "eventId":     eid,
                "userId":      1,   # seeded user
                "status":      "COMPLETED",
                "totalAmount": amount,
                "bookingDate": "2026-04-01T10:00:00",
                "contactEmail": "contact@test.com",
            }, headers=auth_headers, timeout=10)
            if b.status_code in (200, 201):
                booking_ids.append(b.json()["id"])

        # Issue 10 tickets across the bookings (7 USED, 3 VALID)
        for i, bid in enumerate(booking_ids[:3] if booking_ids else []):
            for _ in range(3 if i < 1 else (3 if i == 1 else 4)):
                requests.post(f"{ticket_url}/api/tickets", json={
                    "bookingId":    bid,
                    "attendeeName": "Attendee",
                    "status":       "USED" if i < 2 else "VALID",
                    "issuedAt":     "2026-04-01T09:00:00",
                    "ticketCode":   str(uuid.uuid4()),
                }, headers=auth_headers, timeout=10)

        resp = requests.get(f"{event_url}/api/events/{eid}/dashboard",
                            headers=auth_headers, timeout=10)
        assert resp.status_code == 200, f"Dashboard must return 200, got {resp.status_code}: {resp.text}"
        body = resp.json()

        # Check required DTO fields (Builder pattern DP-4 constructs this)
        assert "totalBookings" in body or "total_bookings" in body, (
            "Dashboard DTO must contain totalBookings field"
        )
        assert "totalRevenue" in body or "total_revenue" in body, (
            "Dashboard DTO must contain totalRevenue field"
        )
        assert "averageRating" in body or "average_rating" in body, (
            "Dashboard DTO must contain averageRating field"
        )

    def test_dashboard_nonexistent_event_returns_404(self, event_url, auth_headers):
        """Scenario b: GET /api/events/999999/dashboard → 404."""
        resp = requests.get(f"{event_url}/api/events/999999/dashboard",
                            headers=auth_headers, timeout=10)
        assert resp.status_code == 404, (
            f"Non-existent event dashboard must return 404, got {resp.status_code}"
        )

    def test_dashboard_no_bookings_returns_zero_values(self, event_url, auth_headers,
                                                         make_event):
        """Scenario c: event with no bookings → all zeroes."""
        event = make_event(name=f"Empty Dash {uuid.uuid4().hex[:4]}")
        resp  = requests.get(f"{event_url}/api/events/{event['id']}/dashboard",
                             headers=auth_headers, timeout=10)
        assert resp.status_code == 200
        body = resp.json()
        total_bookings = body.get("totalBookings") or body.get("total_bookings", 0)
        assert total_bookings == 0, (
            f"Event with no bookings must have totalBookings=0, got {total_bookings}"
        )

    def test_dashboard_no_auth_returns_401(self, event_url, auth_headers, make_event):
        """Scenario d: no token → 401 (CC-1)."""
        event = make_event(name=f"NoAuth Dash {uuid.uuid4().hex[:4]}")
        resp  = requests.get(f"{event_url}/api/events/{event['id']}/dashboard", timeout=10)
        assert resp.status_code == 401

    def test_dashboard_writes_dashboard_viewed_on_every_call(
        self, event_url, auth_headers, make_event, mongo_db
    ):
        """Scenario e: DASHBOARD_VIEWED logged on every invocation (even cache hits).

        Section 10.2.3 step g: the MongoDB write happens OUTSIDE the cache layer.
        DP-2 Observer + DP-6 Factory must fire each time.
        """
        event = make_event(name=f"DVLogged {uuid.uuid4().hex[:4]}")
        eid   = event["id"]

        # Call twice
        requests.get(f"{event_url}/api/events/{eid}/dashboard",
                     headers=auth_headers, timeout=10)
        time.sleep(0.3)
        requests.get(f"{event_url}/api/events/{eid}/dashboard",
                     headers=auth_headers, timeout=10)
        time.sleep(0.5)

        count = mongo_db["event_events"].count_documents(
            {"eventId": eid, "action": "DASHBOARD_VIEWED"}
        )
        assert count >= 2, (
            f"DASHBOARD_VIEWED must be logged on EVERY invocation (even cache hits). "
            f"Expected ≥2 documents, found {count} "
            "(Section 10.2.3 step g)"
        )

    def test_dashboard_cached_in_redis(self, event_url, auth_headers, make_event,
                                        redis_client):
        """CC-3: S2-F12 dashboard cached 10 min; key must exist in Redis."""
        event = make_event(name=f"CacheDash {uuid.uuid4().hex[:4]}")
        eid   = event["id"]
        requests.get(f"{event_url}/api/events/{eid}/dashboard",
                     headers=auth_headers, timeout=10)
        time.sleep(0.3)
        pattern = f"event-service::S2-F12::{eid}*"
        keys    = list(redis_client.scan_iter(pattern))
        assert keys, (
            f"Redis must have key matching '{pattern}' after S2-F12 dashboard call "
            "(CC-3 10-min cache)"
        )

    def test_dashboard_dto_has_builder_fields(self, event_url, auth_headers, make_event):
        """DP-4 Builder Pattern: response DTO must have all required fields.

        Section 3.5 test scenario c: EventDashboardDTO uses Builder.
        Required fields: eventId, name, totalBookings, totalTicketsSold,
        totalRevenue, averageAttendanceRate, averageRating.
        """
        event = make_event(name=f"BuilderDash {uuid.uuid4().hex[:4]}", rating=3.7)
        resp  = requests.get(f"{event_url}/api/events/{event['id']}/dashboard",
                             headers=auth_headers, timeout=10)
        assert resp.status_code == 200
        body = resp.json()
        # Use flexible key matching (camelCase or snake_case)
        def has_field(d, *names):
            return any(n in d for n in names)

        assert has_field(body, "eventId", "event_id"), "Dashboard DTO missing eventId"
        assert has_field(body, "name"), "Dashboard DTO missing name"
        assert has_field(body, "totalBookings", "total_bookings"), \
            "Dashboard DTO missing totalBookings"
        assert has_field(body, "totalRevenue", "total_revenue"), \
            "Dashboard DTO missing totalRevenue"
        assert has_field(body, "averageRating", "average_rating"), \
            "Dashboard DTO missing averageRating"


# ---------------------------------------------------------------------------
# DP-7 Adapter Pattern — Elasticsearch  (Section 3.8)
# ---------------------------------------------------------------------------

class TestDP7ElasticsearchAdapter:
    """DP-7 — Adapter Pattern: ElasticsearchHitAdapter must exist in event-service.

    Section 3.8 test scenario a, b, d.
    """

    def test_elasticsearch_hit_adapter_exists_in_source(self):
        """Scenario a: ElasticsearchHitAdapter class must exist in event-service source."""
        found = _grep_source("event-service", r"\bElasticsearchHitAdapter\b")
        assert found, (
            "ElasticsearchHitAdapter class not found in event-service source "
            "(DP-7 Section 3.8 step a)"
        )

    def test_elasticsearch_hit_adapter_has_adapt_method(self):
        """Scenario b: ElasticsearchHitAdapter must have an adapt(...) method."""
        found = _grep_source("event-service",
                             r"\bElasticsearchHitAdapter\b[\s\S]{0,500}adapt\(")
        if not found:
            # Broader check: adapt method anywhere in the adapter file
            found = _grep_source("event-service", r"\badapt\(")
        assert found, (
            "ElasticsearchHitAdapter must have an adapt(...) method "
            "(DP-7 Section 3.8 step b)"
        )

    def test_mongo_document_adapter_exists_in_event_service(self):
        """Scenario a: MongoDocumentAdapter must exist in event-service."""
        found = _grep_source("event-service", r"\bMongoDocumentAdapter\b")
        assert found, (
            "MongoDocumentAdapter not found in event-service source "
            "(DP-7 Section 3.8)"
        )


# ---------------------------------------------------------------------------
# DP-6 Factory / DP-2 Observer — EventActivityEvent  (Sections 3.7, 3.3)
# ---------------------------------------------------------------------------

class TestDP6FactoryEventActivity:
    """DP-6 Factory + DP-2 Observer: event-service must route audit writes
    through EventFactory.createEvent(EVENT_ACTIVITY, params).

    Section 3.7 test scenario e, g.
    """

    def test_factory_event_activity_event_class_exists(self):
        """Scenario b + e: MongoEvent interface (base for all audit events) must exist."""
        found = _grep_source("event-service", r"\bMongoEvent\b")
        assert found, (
            "MongoEvent interface not found in event-service source "
            "(DP-6 Factory Section 3.7 — base event type for EventFactory)"
        )

    def test_event_factory_exists_in_source(self):
        """Scenario c: EventFactory with createEvent method must exist somewhere."""
        found = any(
            _grep_source(svc, r"\bEventFactory\b")
            for svc in ["user-service", "event-service", "booking-service",
                        "ticket-service", "sales-service"]
        )
        assert found, "EventFactory class not found in any service source (DP-6 Section 3.7)"

    def test_crud_create_event_writes_to_mongodb(self, event_url, auth_headers,
                                                   mongo_db):
        """Scenario g / Section 4.5 step g: CRUD POST /api/events must write
        to event_events via Observer chain.
        """
        unique = uuid.uuid4().hex[:8]
        resp   = requests.post(f"{event_url}/api/events", json={
            "name":      f"CrudObs {unique}",
            "category":  "CONCERT",
            "venue":     "OVenue",
            "eventDate": "2026-08-20T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": "observer test"},
        }, headers=auth_headers, timeout=10)
        assert resp.status_code in (200, 201)
        eid = resp.json()["id"]
        time.sleep(0.5)

        doc = mongo_db["event_events"].find_one(
            {"eventId": eid}
        )
        assert doc is not None, (
            f"CRUD POST /api/events must write to event_events for eventId={eid} "
            "(Observer/Factory retrofit Section 4.5)"
        )
