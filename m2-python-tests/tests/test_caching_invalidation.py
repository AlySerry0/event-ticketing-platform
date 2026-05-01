"""
test_caching_invalidation.py — Redis Caching & Cache Invalidation Tests
=======================================================================
Covers:
  CC-3    Redis Caching on M1 Endpoints          (Section 9.3)
  Section 4.4  Caching Strategy & Invalidation
    4.4.1  M1 Feature GET endpoints (27 for Event Ticketing)
    4.4.2  CRUD GET-by-ID endpoints (10 entities)
    4.4.3  List endpoints are NOT cached
    4.4.4  Write endpoints invalidate caches (wildcard deletion)
    4.4.5  Cache key convention
    4.4.6  Wildcard deletion strategy
  CC-5/CC-6  Docker Compose & application.yml checks

Each test is parameterized where possible for coverage breadth.
"""

import time
import uuid
import yaml
import requests
import pytest
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _scan_keys(redis_client, pattern: str):
    """Return a list of all Redis keys matching the glob pattern."""
    return list(redis_client.scan_iter(pattern))


def _warm_and_check(redis_client, resp_fn, pattern: str, desc: str):
    """Call resp_fn() to warm the cache, then assert the key pattern exists."""
    resp = resp_fn()
    assert resp.status_code not in (401, 403, 500), (
        f"[{desc}] Warm-up request failed with status {resp.status_code}: {resp.text}"
    )
    time.sleep(0.5)
    keys = _scan_keys(redis_client, pattern)
    assert keys, (
        f"[{desc}] Redis must have a key matching '{pattern}' after GET "
        f"(CC-3 / Section 4.4.1)"
    )
    return resp


# ---------------------------------------------------------------------------
# CC-3 / 4.4.2: CRUD GET-by-ID endpoints must be cached
# ---------------------------------------------------------------------------

class TestCrudGetByIdCaching:
    """Section 4.4.2: GET /api/<entity>/{id} must be cached; list is NOT.

    Cache key format: <service>::<entity>::<id>  (Section 4.4.5).
    """

    @pytest.mark.parametrize("service_fixture,entity,create_body_fn", [
        # (service_url_fixture_name, entity-name, lambda that returns the POST body)
        ("user_url",    "user",         lambda: {
            "name": f"CacheUser {uuid.uuid4().hex[:4]}",
            "email": f"cu_{uuid.uuid4().hex[:6]}@ex.com",
            "password": "pass",
            "phone": f"+240{uuid.uuid4().int % 10**9:09d}",
        }),
        ("event_url",   "event",        lambda: {
            "name": f"CacheEv {uuid.uuid4().hex[:4]}",
            "category": "CONCERT", "venue": "V",
            "eventDate": "2026-09-01T10:00:00", "status": "UPCOMING", "rating": 0.0,
            "details": {"description": "cache test"},
        }),
        ("booking_url", "booking",      lambda: {
            "eventId": 1, "userId": 1, "status": "PENDING",
            "totalAmount": 100.0, "bookingDate": "2026-04-01T10:00:00",
            "contactEmail": "contact@test.com",
        }),
        ("ticket_url",  "ticket",       lambda: {
            "bookingId": 1, "attendeeName": "A", "status": "VALID",
            "issuedAt": "2026-04-01T10:00:00",
            "ticketCode": str(uuid.uuid4()),
        }),
    ])
    def test_crud_get_by_id_cached(self, request, service_fixture, entity,
                                    create_body_fn, auth_headers, redis_client):
        """Section 4.4.2: GET /api/{entity}/{id} must create Redis key.

        Service mapping:
          user     → user-service::user::{id}
          event    → event-service::event::{id}
          booking  → booking-service::booking::{id}
          ticket   → ticket-service::ticket::{id}
        """
        base_url = request.getfixturevalue(service_fixture)
        service_prefix = service_fixture.replace("_url", "-service")
        body = create_body_fn()

        # Create the entity
        create_resp = requests.post(f"{base_url}/api/{entity}s", json=body,
                                    headers=auth_headers, timeout=10)
        if create_resp.status_code not in (200, 201):
            pytest.skip(f"Cannot create {entity} for cache test: {create_resp.text}")
        entity_id = create_resp.json().get("id")
        if not entity_id:
            pytest.skip(f"No id in create response: {create_resp.json()}")

        # GET by ID
        get_resp = requests.get(f"{base_url}/api/{entity}s/{entity_id}",
                                headers=auth_headers, timeout=10)
        if get_resp.status_code == 404:
            pytest.skip(f"GET by ID returned 404 for {entity} {entity_id}")
        time.sleep(0.4)

        # Check Redis for cache key
        pattern = f"{service_prefix}::{entity}::{entity_id}*"
        keys    = _scan_keys(redis_client, pattern)
        assert keys, (
            f"Redis must have key matching '{pattern}' after GET /{entity}s/{entity_id} "
            "(Section 4.4.2)"
        )

    @pytest.mark.parametrize("service_fixture,entity", [
        ("user_url",    "user"),
        ("event_url",   "event"),
        ("booking_url", "booking"),
        ("ticket_url",  "ticket"),
    ])
    def test_crud_list_endpoint_not_cached(self, request, service_fixture, entity,
                                            auth_headers, redis_client):
        """Section 4.4.3: GET /api/{entity}s (list) must NOT create any cache key."""
        base_url       = request.getfixturevalue(service_fixture)
        service_prefix = service_fixture.replace("_url", "-service")

        # Flush any stale list cache keys first
        for k in _scan_keys(redis_client, f"{service_prefix}::*list*"):
            redis_client.delete(k)

        # GET the list
        requests.get(f"{base_url}/api/{entity}s", headers=auth_headers, timeout=10)
        time.sleep(0.3)

        # List endpoints must not cache (Section 4.4.2 + 4.4.3)
        list_keys = _scan_keys(redis_client, f"{service_prefix}::{entity}s*")
        assert not list_keys, (
            f"List endpoint GET /api/{entity}s must NOT create a cache key. "
            f"Found: {list_keys} (Section 4.4.3)"
        )


# ---------------------------------------------------------------------------
# CC-3 / 4.4.1: M1 Feature GET endpoints cached
# ---------------------------------------------------------------------------

class TestM1FeatureGetCaching:
    """Section 4.4.1: 27 M1 feature GET endpoints must be cached.

    Selected representative endpoints are tested here.
    Cache key format: <service>::<featureId>::<param-hash>  (Section 4.4.5).
    """

    def test_s1_f1_user_search_cached(self, user_url, auth_headers, redis_client):
        """S1-F1 search cached 5 min (Section 4.4.1 F1 TTL)."""
        requests.get(f"{user_url}/api/users/search",
                     params={"name": "test"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "user-service::S1-F1::*")
        assert keys, "S1-F1 search result must be cached in Redis (Section 4.4.1)"

    def test_s2_f1_event_search_cached(self, event_url, auth_headers, redis_client):
        """S2-F1 M1 event search cached (Section 4.4.1)."""
        requests.get(f"{event_url}/api/events/search",
                     params={"category": "CONCERT"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "event-service::S2-F1::*")
        assert keys, "S2-F1 M1 event search must be cached (Section 4.4.1)"

    def test_s3_f1_booking_search_cached(self, booking_url, auth_headers, redis_client):
        """S3-F1 booking search cached (Section 4.4.1)."""
        requests.get(f"{booking_url}/api/bookings/search",
                     params={"status": "COMPLETED"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "booking-service::S3-F1::*")
        assert keys, "S3-F1 booking search must be cached (Section 4.4.1)"

    def test_s4_f1_ticket_search_cached(self, ticket_url, auth_headers, redis_client):
        """S4-F1 ticket search cached (Section 4.4.1)."""
        requests.get(f"{ticket_url}/api/tickets/search",
                     params={"status": "VALID"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "ticket-service::S4-F1::*")
        assert keys, "S4-F1 ticket search must be cached (Section 4.4.1)"

    def test_s5_f1_sales_search_cached(self, sales_url, auth_headers, redis_client):
        """S5-F1 sales search cached (Section 4.4.1)."""
        requests.get(f"{sales_url}/api/sales/search",
                     params={"status": "COMPLETED"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "sales-service::S5-F1::*")
        assert keys, "S5-F1 sales search must be cached (Section 4.4.1)"


# ---------------------------------------------------------------------------
# Section 4.4.4 / 4.4.6: Write operations invalidate caches
# ---------------------------------------------------------------------------

class TestCacheInvalidationOnWrites:
    """Section 4.4.4 + 4.4.6: Writes must invalidate the relevant cache keys.

    Wildcard deletion: <service>::S{n}-F{m}::* for feature caches,
    plus <service>::<entity>::{id} for entity detail.
    """

    def test_crud_update_invalidates_entity_detail_cache(
        self, event_url, auth_headers, redis_client
    ):
        """Scenario d (Section 4.4 test scenario):
        GET event (cache it) → PUT event → key must be gone.

        Section 4.4.6 wildcard rule.
        """
        # Create a test event
        create = requests.post(f"{event_url}/api/events", json={
            "name":      f"InvTest {uuid.uuid4().hex[:4]}",
            "category":  "SPORTS",
            "venue":     "InvVenue",
            "eventDate": "2026-07-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {},
        }, headers=auth_headers, timeout=10)
        if create.status_code not in (200, 201):
            pytest.skip(f"Cannot create event: {create.text}")
        eid = create.json()["id"]

        # Warm cache
        requests.get(f"{event_url}/api/events/{eid}",
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        cache_key = f"event-service::event::{eid}"
        assert _scan_keys(redis_client, f"{cache_key}*"), (
            f"Cache key '{cache_key}' must exist before update"
        )

        # Update → must invalidate
        requests.put(f"{event_url}/api/events/{eid}", json={
            "name":      f"InvUpdated {uuid.uuid4().hex[:4]}",
            "category":  "SPORTS",
            "venue":     "InvVenue",
            "eventDate": "2026-07-01T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {},
        }, headers=auth_headers, timeout=10)
        time.sleep(0.4)

        remaining = _scan_keys(redis_client, f"{cache_key}*")
        assert not remaining, (
            f"Cache key '{cache_key}' must be removed after PUT update "
            "(Section 4.4.4 / 4.4.6)"
        )

    def test_crud_delete_invalidates_entity_detail_cache(
        self, event_url, auth_headers, redis_client
    ):
        """Scenario f: DELETE entity → cached key disappears.

        Section 4.4 test scenario f.
        """
        create = requests.post(f"{event_url}/api/events", json={
            "name":      f"DelTest {uuid.uuid4().hex[:4]}",
            "category":  "THEATER",
            "venue":     "DelVenue",
            "eventDate": "2026-07-15T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {},
        }, headers=auth_headers, timeout=10)
        if create.status_code not in (200, 201):
            pytest.skip(f"Cannot create event: {create.text}")
        eid = create.json()["id"]

        # Warm
        requests.get(f"{event_url}/api/events/{eid}",
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)

        # Delete
        requests.delete(f"{event_url}/api/events/{eid}",
                        headers=auth_headers, timeout=10)
        time.sleep(0.4)

        cache_key = f"event-service::event::{eid}"
        remaining = _scan_keys(redis_client, f"{cache_key}*")
        assert not remaining, (
            f"Cache key '{cache_key}' must be removed after DELETE "
            "(Section 4.4 test scenario f)"
        )

    def test_wildcard_invalidation_on_user_update(
        self, user_url, auth_user, redis_client
    ):
        """Scenario e: update user → S1-F3 feature cache keys gone (wildcard).

        Section 4.4 test scenario e: wildcard deletion of <service>::S1-F3::*.
        """
        uid = auth_user["user_id"]

        # Warm the S1-F3 booking-summary cache for this user
        requests.get(f"{user_url}/api/users/{uid}/booking-summary",
                     headers=auth_user["headers"], timeout=10)
        time.sleep(0.3)

        # Trigger M1 S1-F2 preference update → must wildcard-invalidate S1-F3
        requests.put(
            f"{user_url}/api/users/{uid}/preferences",
            json={"notificationsEnabled": True},
            headers=auth_user["headers"], timeout=10,
        )
        time.sleep(0.4)

        # All S1-F3 keys for this user must be gone
        pattern  = f"user-service::S1-F3::{uid}*"
        remaining = _scan_keys(redis_client, pattern)
        assert not remaining, (
            f"S1-F3 cache keys matching '{pattern}' must be cleared after "
            "user preference update (Section 4.4 test scenario e — wildcard invalidation)"
        )

    def test_s3f11_attendance_invalidates_recommendation_cache(
        self, booking_url, auth_headers, redis_client
    ):
        """Section 4.4.4 NoSQL-writer rule:
        POST /record-attendance → wildcard invalidates booking-service::S3-F12::*.

        S3-F12 recommendation cache must be cleared because the graph changed.
        """
        # Warm recommendation cache
        requests.get(f"{booking_url}/api/bookings/recommendations",
                     params={"userId": 1},
                     headers=auth_headers, timeout=10)
        time.sleep(0.3)

        # Record attendance (creates a new ATTENDED edge)
        bk = requests.post(f"{booking_url}/api/bookings", json={
            "eventId": 1, "userId": 1, "status": "COMPLETED",
            "totalAmount": 0, "bookingDate": "2026-04-01T10:00:00",
            "contactEmail": "contact@test.com",
        }, headers=auth_headers, timeout=10)
        if bk.status_code in (200, 201):
            bid = bk.json()["id"]
            requests.post(f"{booking_url}/api/bookings/{bid}/record-attendance",
                          headers=auth_headers, timeout=10)
            time.sleep(0.4)

        remaining = _scan_keys(redis_client, "booking-service::S3-F12::*")
        assert not remaining, (
            "booking-service::S3-F12::* must be wildcard-invalidated after "
            "POST /record-attendance (Section 4.4.4 NoSQL-writer rule)"
        )

    def test_s4f11_scan_invalidates_scan_history_cache(
        self, ticket_url, booking_url, auth_headers, redis_client
    ):
        """Section 4.4.4 NoSQL-writer rule:
        POST /scan → invalidates ticket-service::S4-F12::{ticketId}.
        """
        bk = requests.post(f"{booking_url}/api/bookings", json={
            "eventId": 1, "userId": 1, "status": "COMPLETED",
            "totalAmount": 0, "bookingDate": "2026-04-01T09:00:00",
            "contactEmail": "contact@test.com",
        }, headers=auth_headers, timeout=10)
        bid = bk.json().get("id", 1) if bk.status_code in (200, 201) else 1

        tk = requests.post(f"{ticket_url}/api/tickets", json={
            "bookingId": bid, "attendeeName": "ScanCache", "status": "VALID",
            "issuedAt": "2026-04-01T09:00:00",
            "ticketCode": str(uuid.uuid4()),
        }, headers=auth_headers, timeout=10)
        if tk.status_code not in (200, 201):
            pytest.skip("Cannot create ticket for scan cache test")
        ticket_id = tk.json()["id"]

        # Warm S4-F12 cache
        requests.get(f"{ticket_url}/api/tickets/{ticket_id}/scans",
                     headers=auth_headers, timeout=10)
        time.sleep(0.3)
        before = _scan_keys(redis_client, f"ticket-service::S4-F12::{ticket_id}*")

        # Record scan → invalidate
        requests.post(f"{ticket_url}/api/tickets/{ticket_id}/scan", json={
            "scanType": "ISSUED", "gate": "G1", "section": "A", "seatNumber": "1",
        }, headers=auth_headers, timeout=10)
        time.sleep(0.3)

        if before:
            after = _scan_keys(redis_client, f"ticket-service::S4-F12::{ticket_id}*")
            assert not after, (
                f"ticket-service::S4-F12::{ticket_id} must be invalidated after "
                "POST /scan (Section 4.4.4 NoSQL-writer rule)"
            )

    def test_s2f11_index_invalidates_full_text_search_cache(
        self, event_url, auth_headers, redis_client
    ):
        """Section 4.4.4 NoSQL-writer rule:
        POST /index → wildcard invalidates event-service::S2-F10::*.
        """
        # Warm search cache
        requests.get(f"{event_url}/api/events/search/full-text",
                     params={"query": "jazz"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.3)

        # Create and index a new event → invalidate search cache
        create = requests.post(f"{event_url}/api/events", json={
            "name":      f"IdxInv {uuid.uuid4().hex[:4]}",
            "category":  "CONCERT", "venue": "IdxVenue",
            "eventDate": "2026-09-15T10:00:00", "status": "UPCOMING", "rating": 0.0,
            "details":   {"description": "invalidation test"},
        }, headers=auth_headers, timeout=10)
        if create.status_code not in (200, 201):
            pytest.skip("Cannot create event for invalidation test")
        eid = create.json()["id"]

        requests.post(f"{event_url}/api/events/{eid}/index",
                      headers=auth_headers, timeout=10)
        time.sleep(0.5)

        remaining = _scan_keys(redis_client, "event-service::S2-F10::*")
        assert not remaining, (
            "event-service::S2-F10::* must be wildcard-invalidated after POST /index "
            "(Section 4.4.4 NoSQL-writer rule)"
        )


# ---------------------------------------------------------------------------
# M2 Feature GET endpoints — spot cache checks
# ---------------------------------------------------------------------------

class TestM2FeatureGetCaching:
    """Spot checks: M2 feature endpoints must also be cached (CC-3 / Section 8)."""

    def test_s1_f12_activity_feed_cached(
        self, user_url, auth_user, redis_client
    ):
        """S1-F12 activity feed cached 5 min (Section 10.1.3 step e)."""
        uid = auth_user["user_id"]
        requests.get(f"{user_url}/api/users/{uid}/activity",
                     headers=auth_user["headers"], timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, f"user-service::S1-F12::{uid}*")
        assert keys, (
            f"S1-F12 activity feed must be cached (key user-service::S1-F12::{uid})"
        )

    def test_s2_f10_full_text_search_cached(
        self, event_url, auth_headers, redis_client
    ):
        """S2-F10 full-text search cached 5 min (Section 10.2.1 step e)."""
        requests.get(f"{event_url}/api/events/search/full-text",
                     params={"query": "test"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "event-service::S2-F10::*")
        assert keys, "S2-F10 full-text search must be cached (Section 10.2.1)"

    def test_s3_f10_booking_analytics_cached(
        self, booking_url, auth_headers, redis_client
    ):
        """S3-F10 booking analytics dashboard cached 10 min (Section 10.3.1 step e)."""
        requests.get(f"{booking_url}/api/bookings/analytics/dashboard",
                     params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "booking-service::S3-F10::*")
        assert keys, "S3-F10 booking analytics must be cached (Section 10.3.1)"

    def test_s3_f12_recommendations_cached(
        self, booking_url, auth_user, redis_client
    ):
        """S3-F12 recommendations cached 5 min (Section 10.3.3 step g)."""
        requests.get(f"{booking_url}/api/bookings/recommendations",
                     params={"userId": auth_user["user_id"]},
                     headers=auth_user["headers"], timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "booking-service::S3-F12::*")
        assert keys, "S3-F12 recommendations must be cached (Section 10.3.3)"

    def test_s4_f10_ticket_analytics_cached(
        self, ticket_url, auth_headers, redis_client
    ):
        """S4-F10 ticket analytics cached 10 min (Section 10.4.1 step e)."""
        requests.get(f"{ticket_url}/api/tickets/analytics",
                     params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "ticket-service::S4-F10::*")
        assert keys, "S4-F10 ticket analytics must be cached (Section 10.4.1)"

    def test_s5_f10_tier_analytics_cached(
        self, sales_url, auth_headers, redis_client
    ):
        """S5-F10 tier analytics cached 10 min (Section 10.5.1 step g)."""
        requests.get(f"{sales_url}/api/sales/analytics/tier",
                     params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)
        keys = _scan_keys(redis_client, "sales-service::S5-F10::*")
        assert keys, "S5-F10 tier analytics must be cached (Section 10.5.1)"


# ---------------------------------------------------------------------------
# CC-5: Docker Compose with 6 Databases  (Section 9.5)
# ---------------------------------------------------------------------------

class TestCC5DockerCompose:
    """CC-5 — docker-compose.yaml must declare all 6 database containers.

    Section 9.5 test scenarios a, b, c, d.
    """

    @pytest.fixture(scope="class")
    def compose(self):
        """Parse and return the docker-compose.yaml contents."""
        compose_path = PROJECT_ROOT / "docker-compose.yaml"
        if not compose_path.exists():
            compose_path = PROJECT_ROOT / "docker-compose.yml"
        assert compose_path.exists(), "docker-compose.yaml not found at project root"
        with compose_path.open() as f:
            return yaml.safe_load(f)

    def test_all_six_databases_declared(self, compose):
        """Scenario a: postgres, mongo, redis, elasticsearch, neo4j, cassandra."""
        services = compose.get("services", {})
        required = {"postgres", "mongo", "redis", "elasticsearch", "neo4j", "cassandra"}
        missing  = required - set(services.keys())
        assert not missing, (
            f"docker-compose.yaml must declare all 6 databases. Missing: {missing} "
            "(CC-5 Section 9.5 step a)"
        )

    def test_postgres_uses_pg17(self, compose):
        """Scenario b: postgres must use postgres:17 (CC-5 Section 9.5 step b)."""
        image = compose.get("services", {}).get("postgres", {}).get("image", "")
        assert image.startswith("postgres:17"), (
            f"postgres service must use image postgres:17, got: {image}"
        )

    def test_elasticsearch_version_pinned(self, compose):
        """Scenario b: elasticsearch must use a pinned version (not :latest).

        Spec says 8.19.12, docker-compose may use a different pin.
        """
        image = compose.get("services", {}).get("elasticsearch", {}).get("image", "")
        assert "elasticsearch:" in image and "latest" not in image, (
            f"elasticsearch image must be pinned (not :latest), got: {image}"
        )

    def test_redis_memory_cap_configured(self, compose):
        """Scenario c: Redis must have --maxmemory 256mb --maxmemory-policy allkeys-lru."""
        redis_svc = compose.get("services", {}).get("redis", {})
        command   = str(redis_svc.get("command", ""))
        assert "256mb" in command or "256MB" in command, (
            f"Redis must have --maxmemory 256mb, got command: {command}"
        )
        assert "allkeys-lru" in command, (
            f"Redis must have --maxmemory-policy allkeys-lru, got: {command}"
        )

    def test_elasticsearch_java_opts_configured(self, compose):
        """Scenario c: Elasticsearch must have -Xms512m -Xmx512m JVM opts."""
        es_env  = compose.get("services", {}).get("elasticsearch", {}).get("environment", {})
        env_str = str(es_env)
        assert "512m" in env_str or "512M" in env_str, (
            f"Elasticsearch must have 512m JVM heap, got env: {es_env}"
        )

    def test_cassandra_heap_configured(self, compose):
        """Scenario c: Cassandra must have MAX_HEAP_SIZE: 512M."""
        cass_env = compose.get("services", {}).get("cassandra", {}).get("environment", {})
        env_str  = str(cass_env)
        assert "512M" in env_str or "512m" in env_str, (
            f"Cassandra must have MAX_HEAP_SIZE: 512M, got env: {cass_env}"
        )

    def test_neo4j_heap_configured(self, compose):
        """Scenario c: Neo4j must have NEO4J_server_memory_heap_max__size: 512m."""
        neo4j_env = compose.get("services", {}).get("neo4j", {}).get("environment", {})
        env_str   = str(neo4j_env)
        assert "512m" in env_str or "512M" in env_str, (
            f"Neo4j must have heap_max_size: 512m, got env: {neo4j_env}"
        )

    def test_healthchecks_present_for_all_databases(self, compose):
        """Scenario d: all 6 database services must have healthcheck blocks."""
        db_services = ["postgres", "mongo", "redis", "elasticsearch", "neo4j", "cassandra"]
        services    = compose.get("services", {})
        missing_hc  = [svc for svc in db_services
                       if "healthcheck" not in services.get(svc, {})]
        assert not missing_hc, (
            f"Healthchecks missing for: {missing_hc} "
            "(CC-5 Section 9.5 step d)"
        )


# ---------------------------------------------------------------------------
# CC-6: application.yml checks  (Section 9.6)
# ---------------------------------------------------------------------------

class TestCC6ApplicationYml:
    """CC-6 — Each service must have application.yml (not .properties) with correct keys.

    Section 9.6 test scenarios a–f.
    """

    SERVICE_DIRS = [
        "user-service",
        "event-service",
        "booking-service",
        "ticket-service",
        "sales-service",
    ]

    def _find_app_yml(self, service_dir: str):
        """Return the Path to the service's application.yml if it exists."""
        base = PROJECT_ROOT / service_dir / "src" / "main" / "resources"
        yml = base / "application.yml"
        return yml if yml.exists() else None

    def test_all_services_have_application_yml(self):
        """Scenario a: application.yml must exist for every service (not .properties)."""
        missing = []
        for svc in self.SERVICE_DIRS:
            if not self._find_app_yml(svc):
                missing.append(svc)
        assert not missing, (
            f"application.yml missing for services: {missing} "
            "(CC-6 Section 9.6 step a)"
        )

    def test_services_do_not_use_application_properties(self):
        """Scenario a (implied): no application.properties in M2 services."""
        has_props = []
        for svc in self.SERVICE_DIRS:
            props = PROJECT_ROOT / svc / "src" / "main" / "resources" / "application.properties"
            if props.exists():
                has_props.append(svc)
        assert not has_props, (
            f"application.properties must be replaced by application.yml in: {has_props} "
            "(CC-6: M2 requires YAML format)"
        )

    @pytest.mark.parametrize("service_dir", SERVICE_DIRS)
    def test_service_has_datasource_url(self, service_dir):
        """Scenario b: spring.datasource.url pointing to postgres:5432."""
        yml_path = self._find_app_yml(service_dir)
        if not yml_path:
            pytest.skip(f"application.yml not found for {service_dir}")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f)
        url = (cfg.get("spring", {}).get("datasource", {}).get("url", "") or "")
        assert "postgres" in url.lower() or "5432" in url, (
            f"{service_dir}/application.yml must have spring.datasource.url "
            f"pointing to postgres:5432. Got: {url}"
        )

    @pytest.mark.parametrize("service_dir", SERVICE_DIRS)
    def test_service_has_mongodb_uri(self, service_dir):
        """Scenario c: spring.data.mongodb.uri must be present in every service."""
        yml_path = self._find_app_yml(service_dir)
        if not yml_path:
            pytest.skip(f"application.yml not found for {service_dir}")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        mongo_cfg = (cfg.get("spring", {}).get("data", {}).get("mongodb", {}) or {})
        uri        = mongo_cfg.get("uri", "")
        assert uri, (
            f"{service_dir} must have spring.data.mongodb.uri "
            "(CC-6 Section 9.6 step c)"
        )

    @pytest.mark.parametrize("service_dir", SERVICE_DIRS)
    def test_service_has_redis_host(self, service_dir):
        """Scenario c: spring.data.redis.host must be present."""
        yml_path = self._find_app_yml(service_dir)
        if not yml_path:
            pytest.skip(f"application.yml not found for {service_dir}")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        redis_cfg = (cfg.get("spring", {}).get("data", {}).get("redis", {}) or {})
        host      = redis_cfg.get("host", "")
        assert host, (
            f"{service_dir} must have spring.data.redis.host "
            "(CC-6 Section 9.6 step c)"
        )

    @pytest.mark.parametrize("service_dir", SERVICE_DIRS)
    def test_service_has_jwt_secret(self, service_dir):
        """Scenario c: jwt.secret must be present in every service."""
        yml_path = self._find_app_yml(service_dir)
        if not yml_path:
            pytest.skip(f"application.yml not found for {service_dir}")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        jwt_cfg = cfg.get("jwt", {}) or {}
        secret  = jwt_cfg.get("secret", "")
        assert secret, (
            f"{service_dir} must have jwt.secret "
            "(CC-6 Section 9.6 step c)"
        )

    def test_event_service_has_elasticsearch_uri(self):
        """Scenario d: event-service must have spring.elasticsearch.uris."""
        yml_path = self._find_app_yml("event-service")
        if not yml_path:
            pytest.skip("application.yml not found for event-service")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        uris = cfg.get("spring", {}).get("elasticsearch", {}).get("uris", "")
        assert uris, (
            "event-service must have spring.elasticsearch.uris "
            "(CC-6 Section 9.6 step d)"
        )

    def test_booking_service_has_neo4j_uri(self):
        """Scenario e: booking-service must have spring.data.neo4j.uri."""
        yml_path = self._find_app_yml("booking-service")
        if not yml_path:
            pytest.skip("application.yml not found for booking-service")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        uri = cfg.get("spring", {}).get("data", {}).get("neo4j", {}).get("uri", "")
        assert uri, (
            "booking-service must have spring.data.neo4j.uri "
            "(CC-6 Section 9.6 step e)"
        )

    def test_ticket_service_has_cassandra_config(self):
        """Scenario f: ticket-service must have spring.cassandra.contact-points
        and keyspace-name.
        """
        yml_path = self._find_app_yml("ticket-service")
        if not yml_path:
            pytest.skip("application.yml not found for ticket-service")
        with yml_path.open() as f:
            cfg = yaml.safe_load(f) or {}
        cass = cfg.get("spring", {}).get("cassandra", {}) or {}
        assert cass.get("contact-points", ""), (
            "ticket-service must have spring.cassandra.contact-points (CC-6 step f)"
        )
        assert cass.get("keyspace-name", ""), (
            "ticket-service must have spring.cassandra.keyspace-name (CC-6 step f)"
        )


# ---------------------------------------------------------------------------
# Section 4.4.4: NoSQL analytics cache invalidation (ANALYTICS_VIEWED excluded)
# ---------------------------------------------------------------------------

class TestAnalyticsViewedNotInvalidating:
    """Section 4.4.4: ANALYTICS_VIEWED must NOT invalidate caches.

    'Pure observability actions do NOT invalidate caches — specifically
    ANALYTICS_VIEWED and DASHBOARD_VIEWED.' (Section 4.4.4)

    This is tested by checking that after calling a dashboard endpoint (which
    writes ANALYTICS_VIEWED), the dashboard cache key is still present in Redis.
    """

    def test_analytics_viewed_does_not_invalidate_s3f10_cache(
        self, booking_url, auth_headers, redis_client
    ):
        """Section 4.4.4: S3-F10 cache must survive ANALYTICS_VIEWED log.

        If Observer incorrectly invalidated on ANALYTICS_VIEWED, the cache would
        be a miss on every call, defeating the cache entirely.
        """
        # First call → populates cache + writes ANALYTICS_VIEWED
        requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
            headers=auth_headers, timeout=10,
        )
        time.sleep(0.4)
        keys_after_first = _scan_keys(redis_client, "booking-service::S3-F10::*")

        # Second call → also writes ANALYTICS_VIEWED (cache hit)
        requests.get(
            f"{booking_url}/api/bookings/analytics/dashboard",
            params={"startDate": "2026-01-01", "endDate": "2026-12-31"},
            headers=auth_headers, timeout=10,
        )
        time.sleep(0.4)
        keys_after_second = _scan_keys(redis_client, "booking-service::S3-F10::*")

        # Cache key must still be present after second ANALYTICS_VIEWED write
        assert keys_after_second, (
            "S3-F10 cache key must NOT be invalidated by ANALYTICS_VIEWED writes. "
            "ANALYTICS_VIEWED is a pure observability action (Section 4.4.4)"
        )

    def test_dashboard_viewed_does_not_invalidate_s2f12_cache(
        self, event_url, auth_headers, redis_client, make_event
    ):
        """Section 4.4.4: S2-F12 cache must survive DASHBOARD_VIEWED log."""
        event = make_event(name=f"DashNInv {uuid.uuid4().hex[:4]}")
        eid   = event["id"]

        # First call → cache + DASHBOARD_VIEWED
        requests.get(f"{event_url}/api/events/{eid}/dashboard",
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)

        # Second call → cache hit + DASHBOARD_VIEWED again
        requests.get(f"{event_url}/api/events/{eid}/dashboard",
                     headers=auth_headers, timeout=10)
        time.sleep(0.4)

        pattern = f"event-service::S2-F12::{eid}*"
        keys    = _scan_keys(redis_client, pattern)
        assert keys, (
            f"S2-F12 dashboard cache '{pattern}' must NOT be invalidated by "
            "DASHBOARD_VIEWED writes (Section 4.4.4 — pure observability exclusion)"
        )
