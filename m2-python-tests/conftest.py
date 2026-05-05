"""
Milestone 2 Integration & E2E Test Suite — conftest.py
=======================================================
Session-scoped pytest fixtures for:
  - Base URLs for all 5 microservices (ports 8081-8085)
  - Direct database connections: PostgreSQL, MongoDB, Redis,
    Elasticsearch, Neo4j, Cassandra
  - JWT auth tokens (ATTENDEE user + ADMIN user) to drive protected
    endpoints throughout every test file

Cross-Cutting Requirements tested here:
  CC-1  (Section 9.1)  — JWT required on all endpoints
  CC-5  (Section 9.5)  — Docker Compose with 6 databases
  S1-F10 (Section 10.1.1) — public register endpoint used to create
                            the session-scoped test user
"""

import os
import uuid
import time
import pytest
import requests
import jwt            # PyJWT — decode claims without secret verification

# ---------------------------------------------------------------------------
# Connection config — override via environment variables in CI
# ---------------------------------------------------------------------------
USER_SERVICE_URL   = os.environ.get("USER_SERVICE_URL",    "http://localhost:8081")
EVENT_SERVICE_URL  = os.environ.get("EVENT_SERVICE_URL",   "http://localhost:8082")
BOOK_SERVICE_URL   = os.environ.get("BOOK_SERVICE_URL",    "http://localhost:8083")
TKT_SERVICE_URL    = os.environ.get("TKT_SERVICE_URL",     "http://localhost:8084")
SALES_SERVICE_URL  = os.environ.get("SALES_SERVICE_URL",   "http://localhost:8085")

PG_DSN      = os.environ.get(
    "PG_DSN",
    "host=localhost port=5432 dbname=eventticketingdb user=postgres password=postgres",
)
MONGO_URI   = os.environ.get(
    "MONGO_URI",
    "mongodb://root:rootpass@localhost:27017/eventticketingmongo?authSource=admin",
)
REDIS_HOST  = os.environ.get("REDIS_HOST",  "localhost")
REDIS_PORT  = int(os.environ.get("REDIS_PORT",  "6379"))
REDIS_PASS  = os.environ.get("REDIS_PASS",  "redispass")
ES_URL      = os.environ.get("ES_URL",      "http://localhost:9200")
NEO4J_URI   = os.environ.get("NEO4J_URI",   "bolt://localhost:7687")
NEO4J_USER  = os.environ.get("NEO4J_USER",  "neo4j")
NEO4J_PASS  = os.environ.get("NEO4J_PASS",  "neo4jpass")
CASSANDRA_HOST     = os.environ.get("CASSANDRA_HOST",     "localhost")
CASSANDRA_PORT     = int(os.environ.get("CASSANDRA_PORT",     "9042"))
CASSANDRA_KEYSPACE = os.environ.get("CASSANDRA_KEYSPACE", "eventticketingks")

# Seeded ADMIN credentials — must match the M1 seed data in user-service.
# Override via env vars if your seed uses different values.
ADMIN_EMAIL    = os.environ.get("ADMIN_EMAIL",    "admin@admin.com")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "adminpass")

# ---------------------------------------------------------------------------
# Endpoint map — complete reference of all M2 API endpoints
# Format: "service.operation": (method, path, min_auth_role)
# ---------------------------------------------------------------------------
ENDPOINT_MAP = {
    # user-service (port 8081)
    "user.register":              ("POST",   "/api/auth/register",                      "public"),
    "user.login":                 ("POST",   "/api/auth/login",                         "public"),
    "user.health":                ("GET",    "/api/users/health",                       "public"),
    "user.list":                  ("GET",    "/api/users",                              "ATTENDEE"),
    "user.get":                   ("GET",    "/api/users/{id}",                         "ATTENDEE"),
    "user.update":                ("PUT",    "/api/users/{id}",                         "ATTENDEE"),
    "user.preferences":           ("PUT",    "/api/users/{id}/preferences",             "ATTENDEE"),
    "user.activity":              ("GET",    "/api/users/{id}/activity",                "ATTENDEE"),
    "user.role":                  ("PUT",    "/api/users/{id}/role",                    "ADMIN"),
    # event-service (port 8082)
    "event.list":                 ("GET",    "/api/events",                             "ATTENDEE"),
    "event.create":               ("POST",   "/api/events",                             "ATTENDEE"),
    "event.get":                  ("GET",    "/api/events/{id}",                        "ATTENDEE"),
    "event.update":               ("PUT",    "/api/events/{id}",                        "ATTENDEE"),
    "event.delete":               ("DELETE", "/api/events/{id}",                        "ATTENDEE"),
    "event.search":               ("GET",    "/api/events/search",                      "ATTENDEE"),
    "event.full_text_search":     ("GET",    "/api/events/search/full-text",            "ATTENDEE"),
    "event.index":                ("POST",   "/api/events/{id}/index",                  "ATTENDEE"),
    "event.dashboard":            ("GET",    "/api/events/{id}/dashboard",              "ATTENDEE"),
    # booking-service (port 8083)
    "booking.list":               ("GET",    "/api/bookings",                           "ATTENDEE"),
    "booking.create":             ("POST",   "/api/bookings",                           "ATTENDEE"),
    "booking.get":                ("GET",    "/api/bookings/{id}",                      "ATTENDEE"),
    "booking.update":             ("PUT",    "/api/bookings/{id}",                      "ATTENDEE"),
    "booking.analytics_m1":       ("GET",    "/api/bookings/analytics",                 "ATTENDEE"),
    "booking.dashboard":          ("GET",    "/api/bookings/analytics/dashboard",       "ATTENDEE"),
    "booking.record_attendance":  ("POST",   "/api/bookings/{id}/record-attendance",    "ATTENDEE"),
    "booking.recommendations":    ("GET",    "/api/bookings/recommendations",           "ATTENDEE"),
    "booking_item.list":          ("GET",    "/api/booking-items",                      "ATTENDEE"),
    "booking_item.create":        ("POST",   "/api/booking-items",                      "ATTENDEE"),
    # ticket-service (port 8084)
    "ticket.list":                ("GET",    "/api/tickets",                            "ATTENDEE"),
    "ticket.create":              ("POST",   "/api/tickets",                            "ATTENDEE"),
    "ticket.get":                 ("GET",    "/api/tickets/{id}",                       "ATTENDEE"),
    "ticket.update":              ("PUT",    "/api/tickets/{id}",                       "ATTENDEE"),
    "ticket.analytics":           ("GET",    "/api/tickets/analytics",                  "ATTENDEE"),
    "ticket.scan":                ("POST",   "/api/tickets/{id}/scan",                  "ATTENDEE"),
    "ticket.scan_history":        ("GET",    "/api/tickets/{id}/scans",                 "ATTENDEE"),
    # sales-service (port 8085)
    "sale.list":                  ("GET",    "/api/sales",                              "ATTENDEE"),
    "sale.create":                ("POST",   "/api/sales",                              "ATTENDEE"),
    "sale.get":                   ("GET",    "/api/sales/{id}",                         "ATTENDEE"),
    "sale.update":                ("PUT",    "/api/sales/{id}",                         "ATTENDEE"),
    "sale.process":               ("POST",   "/api/sales/booking/{id}",                 "ATTENDEE"),
    "sale.refund_m1":             ("PUT",    "/api/sales/{id}/refund",                  "ATTENDEE"),
    "sale.tier_analytics":        ("GET",    "/api/sales/analytics/tier",               "ATTENDEE"),
    "sale.audit_trail":           ("GET",    "/api/sales/{id}/audit-trail",             "ATTENDEE"),
    "sale.refund_window":         ("POST",   "/api/sales/{id}/refund-window-policy",    "ATTENDEE"),
    "sale.promotions.list":       ("GET",    "/api/sales/promotions",                   "ATTENDEE"),
    "sale.promotions.create":     ("POST",   "/api/sales/promotions",                   "ATTENDEE"),
    "sale.promotions.apply":      ("POST",   "/api/sales/{saleId}/promotions/{promoId}","ATTENDEE"),
}

# ---------------------------------------------------------------------------
# Internal utility
# ---------------------------------------------------------------------------

def _decode_token(token: str) -> dict:
    """Decode a JWT (no signature verification) and return the payload dict.

    The spec (Section 5.2) mandates claims: sub (email), uid (user.id), role.
    """
    return jwt.decode(token, options={"verify_signature": False}, algorithms=["HS256"])


def _uid_from_token(token: str) -> int:
    """Extract the numeric uid claim from a JWT token (Section 5.2)."""
    return int(_decode_token(token)["uid"])


# ---------------------------------------------------------------------------
# Base URL fixtures  (scope="session" — one URL per test run)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def user_url():
    """User Service base URL — port 8081 (S1-F10, S1-F11, S1-F12, CC-2)."""
    return USER_SERVICE_URL

@pytest.fixture(scope="session")
def event_url():
    """Event Service base URL — port 8082 (S2-F10, S2-F11, S2-F12)."""
    return EVENT_SERVICE_URL

@pytest.fixture(scope="session")
def booking_url():
    """Booking Service base URL — port 8083 (S3-F10, S3-F11, S3-F12)."""
    return BOOK_SERVICE_URL

@pytest.fixture(scope="session")
def ticket_url():
    """Ticket Service base URL — port 8084 (S4-F10, S4-F11, S4-F12)."""
    return TKT_SERVICE_URL

@pytest.fixture(scope="session")
def sales_url():
    """Sales Service base URL — port 8085 (S5-F10, S5-F11, S5-F12)."""
    return SALES_SERVICE_URL

# ---------------------------------------------------------------------------
# Database connection fixtures  (scope="session")
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def pg_conn():
    """PostgreSQL connection — direct DB assertions for passwords, roles, etc.

    Used by: Section 4.1 (BCrypt hashes), Section 4.2 (ENUM values),
             admin_user fallback promotion, CC-5 checks.
    """
    import psycopg2
    try:
        conn = psycopg2.connect(PG_DSN)
        conn.autocommit = True
        yield conn
        conn.close()
    except Exception as exc:
        pytest.skip(f"[SKIP] PostgreSQL unavailable: {exc}")


@pytest.fixture(scope="session")
def mongo_db():
    """MongoDB database handle — 'eventticketingmongo'.

    Used to assert Observer/Factory writes to: auth_events, event_events,
    booking_events, ticket_events, payment_audit_trail  (Section 7.1, DP-2/DP-6).
    Soft dependency: tests skip if Mongo is down.
    """
    try:
        from pymongo import MongoClient
        client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5_000)
        client.admin.command("ping")
        db = client["eventticketingmongo"]
        yield db
        client.close()
    except Exception as exc:
        pytest.skip(f"[SKIP] MongoDB unavailable: {exc}")


@pytest.fixture(scope="session")
def redis_client():
    """Redis client (decode_responses=True) — cache-key assertions.

    Used by: CC-3 (Section 9.3), Section 4.4 cache & invalidation tests.
    """
    try:
        import redis
        r = redis.Redis(
            host=REDIS_HOST, port=REDIS_PORT, password=REDIS_PASS,
            decode_responses=True, socket_connect_timeout=5,
        )
        r.ping()
        yield r
    except Exception as exc:
        pytest.skip(f"[SKIP] Redis unavailable: {exc}")


@pytest.fixture(scope="session")
def es_client():
    """Elasticsearch client — verify S2-F10/S2-F11 index operations (Section 7.2)."""
    try:
        from elasticsearch import Elasticsearch
        es = Elasticsearch(ES_URL, request_timeout=10)
        es.info()
        yield es
    except Exception as exc:
        pytest.skip(f"[SKIP] Elasticsearch unavailable: {exc}")


@pytest.fixture(scope="session")
def neo4j_driver():
    """Neo4j driver — verify S3-F11 ATTENDED graph writes (Section 7.3)."""
    try:
        from neo4j import GraphDatabase
        driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASS))
        driver.verify_connectivity()
        yield driver
        driver.close()
    except Exception as exc:
        pytest.skip(f"[SKIP] Neo4j unavailable: {exc}")


@pytest.fixture(scope="session")
def cassandra_session():
    """Cassandra session connected to eventticketingks keyspace.

    Used by: S4-F11 / S4-F12 scan-event assertions (Section 7.4).
    Cassandra is a soft dependency — tests skip if it is unreachable.
    """
    try:
        from cassandra.cluster import Cluster
        from cassandra.policies import RoundRobinPolicy
        cluster = Cluster(
            [CASSANDRA_HOST],
            port=CASSANDRA_PORT,
            load_balancing_policy=RoundRobinPolicy(),
            connect_timeout=15,
            protocol_version=4,
        )
        session = cluster.connect()
        session.execute(f"USE {CASSANDRA_KEYSPACE}")
        yield session
        cluster.shutdown()
    except Exception as exc:
        pytest.skip(f"[SKIP] Cassandra unavailable or keyspace missing: {exc}")


# ---------------------------------------------------------------------------
# Auth token fixtures  (scope="session")
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def auth_user(user_url):
    """Register a fresh ATTENDEE test user and return full auth context dict.

    CC-1 (Section 9.1): register is a public endpoint — no token needed.
    S1-F10 (Section 10.1.1): POST /api/auth/register → 201 with JWT.
    Section 5.2: token carries sub (email), uid (user.id), role claims.

    Returns dict keys: token, user_id, email, password, headers
    """
    suffix   = uuid.uuid4().hex[:8]
    email    = f"testuser_{suffix}@m2test.com"
    # Phone must match +20 format assumed by seed; keep 11 digits after +
    phone    = f"+201{int(suffix[:8], 16) % 10**9:09d}"
    password = "TestPass123!"

    resp = requests.post(
        f"{user_url}/api/auth/register",
        json={"name": "M2 Test User", "email": email, "password": password, "phone": phone},
        timeout=15,
    )
    assert resp.status_code == 201, (
        f"[conftest] ATTENDEE user registration failed ({resp.status_code}): {resp.text}"
    )

    login = requests.post(
        f"{user_url}/api/auth/login",
        json={"email": email, "password": password},
        timeout=10,
    )
    assert login.status_code == 200, f"[conftest] Attendee re-login failed: {login.text}"

    token   = login.json()["token"]
    user_id = _uid_from_token(token)
    return {
        "token":    token,
        "user_id":  user_id,
        "email":    email,
        "password": password,
        "headers":  {"Authorization": f"Bearer {token}"},
    }


@pytest.fixture(scope="session")
def auth_token(auth_user):
    """Convenience: return only the Bearer token string for the ATTENDEE test user."""
    return auth_user["token"]


@pytest.fixture(scope="session")
def auth_headers(auth_user):
    """Convenience: return {'Authorization': 'Bearer <attendee-token>'} dict."""
    return auth_user["headers"]


@pytest.fixture(scope="session")
def admin_user(user_url, pg_conn):
    """Return an ADMIN auth context dict.

    Strategy:
      1. Login with ADMIN_EMAIL / ADMIN_PASSWORD (env vars, seeded admin).
      2. If login fails, register a fresh user then promote via direct PG UPDATE.
         (Mirrors the grader's approach of testing against the seeded ADMIN.)

    CC-2 (Section 9.2): ADMIN role required for PUT /api/users/{id}/role.
    Section 4.2: ADMIN is never assigned on register; must come from seed or PG.
    """
    # --- Attempt 1: use seeded admin credentials ---
    resp = requests.post(
        f"{user_url}/api/auth/login",
        json={"email": ADMIN_EMAIL, "password": ADMIN_PASSWORD},
        timeout=10,
    )
    if resp.status_code == 200:
        token   = resp.json()["token"]
        user_id = _uid_from_token(token)
        return {
            "token":   token,
            "user_id": user_id,
            "email":   ADMIN_EMAIL,
            "headers": {"Authorization": f"Bearer {token}"},
        }

    # --- Attempt 2: create user + promote via PG ---
    suffix   = uuid.uuid4().hex[:8]
    email    = f"admin_{suffix}@m2test.com"
    phone    = f"+202{int(suffix[:8], 16) % 10**9:09d}"
    password = "AdminPass123!"

    reg = requests.post(
        f"{user_url}/api/auth/register",
        json={"name": "M2 Admin", "email": email, "password": password, "phone": phone},
        timeout=10,
    )
    assert reg.status_code == 201, (
        f"[conftest] Admin fallback registration failed: {reg.text}\n"
        "Set ADMIN_EMAIL / ADMIN_PASSWORD env vars to match your seed data."
    )
    prov_uid = _uid_from_token(reg.json()["token"])

    cur = pg_conn.cursor()
    cur.execute("UPDATE users SET role = 'ADMIN' WHERE id = %s", (prov_uid,))
    cur.close()

    login = requests.post(
        f"{user_url}/api/auth/login",
        json={"email": email, "password": password},
        timeout=10,
    )
    assert login.status_code == 200, f"[conftest] Admin re-login failed: {login.text}"

    token   = login.json()["token"]
    user_id = _uid_from_token(token)
    return {
        "token":   token,
        "user_id": user_id,
        "email":   email,
        "headers": {"Authorization": f"Bearer {token}"},
    }


@pytest.fixture(scope="session")
def admin_token(admin_user):
    """Convenience: return only the Bearer token string for the ADMIN user."""
    return admin_user["token"]


@pytest.fixture(scope="session")
def admin_headers(admin_user):
    """Convenience: return {'Authorization': 'Bearer <admin-token>'} dict."""
    return admin_user["headers"]


# ---------------------------------------------------------------------------
# Shared entity-creation helpers (function-scoped)
# ---------------------------------------------------------------------------

@pytest.fixture
def make_event(event_url, admin_headers):
    """Factory fixture: create an event via CRUD and return the parsed JSON dict.

    Usage in a test:
        event = make_event(name="Jazz Night", category="CONCERT")

    S2-F11 step e (Section 10.2.2): the auto-index retrofit means every CRUD
    POST/PUT also writes the event to Elasticsearch.
    """
    def _create(**overrides):
        body = {
            "name":      f"Event-{uuid.uuid4().hex[:6]}",
            "category":  "CONCERT",
            "venue":     "Test Venue Cairo",
            "eventDate": "2026-08-15T10:00:00",
            "status":    "UPCOMING",
            "rating":    0.0,
            "details":   {"description": "A test event for M2 integration tests"},
        }
        body.update(overrides)
        resp = requests.post(f"{event_url}/api/events", json=body,
                             headers=admin_headers, timeout=10)
        assert resp.status_code in (200, 201), (
            f"make_event: CRUD POST failed ({resp.status_code}): {resp.text}"
        )
        return resp.json()
    return _create


@pytest.fixture
def make_user(user_url):
    """Factory fixture: register a fresh ATTENDEE user and return auth context dict."""
    def _create(name=None, **extra):
        suffix   = uuid.uuid4().hex[:8]
        email    = f"user_{suffix}@m2test.com"
        phone    = f"+203{int(suffix[:8], 16) % 10**9:09d}"
        password = "UserPass123!"
        body = {
            "name":     name or f"User {suffix}",
            "email":    email,
            "password": password,
            "phone":    phone,
        }
        body.update(extra)
        resp = requests.post(f"{user_url}/api/auth/register", json=body, timeout=10)
        assert resp.status_code == 201, f"make_user failed: {resp.text}"
        token   = resp.json()["token"]
        user_id = _uid_from_token(token)
        return {
            "token":    token,
            "user_id":  user_id,
            "email":    email,
            "password": password,
            "headers":  {"Authorization": f"Bearer {token}"},
        }
    return _create


@pytest.fixture
def make_booking(booking_url, auth_headers):
    """Factory fixture: create a booking via CRUD.

    The caller may pass `headers` to override the default auth_headers.
    """
    def _create(event_id, user_id, status="PENDING", total_amount=500.0,
                headers=None, **extra):
        from datetime import datetime
        body = {
            "eventId":     event_id,
            "userId":      user_id,
            "status":      status,
            "totalAmount": total_amount,
            "bookingDate": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"),
        }
        body.update(extra)
        resp = requests.post(f"{booking_url}/api/bookings", json=body,
                             headers=headers or auth_headers, timeout=10)
        assert resp.status_code in (200, 201), f"make_booking failed: {resp.text}"
        return resp.json()
    return _create


@pytest.fixture
def make_booking_item(booking_url, auth_headers):
    """Factory fixture: create a booking item with optional JSONB metadata."""
    def _create(booking_id, quantity=2, unit_price=250.0,
                ticket_tier="standard", headers=None, **extra):
        body = {
            "bookingId": booking_id,
            "quantity":  quantity,
            "unitPrice": unit_price,
            "metadata":  {"ticketTier": ticket_tier},
        }
        body.update(extra)
        resp = requests.post(f"{booking_url}/api/booking-items", json=body,
                             headers=headers or auth_headers, timeout=10)
        assert resp.status_code in (200, 201), f"make_booking_item failed: {resp.text}"
        return resp.json()
    return _create


@pytest.fixture
def make_ticket(ticket_url, auth_headers):
    """Factory fixture: issue a ticket via CRUD."""
    def _create(booking_id, attendee_name="Test Attendee",
                status="VALID", headers=None, **extra):
        from datetime import datetime
        body = {
            "bookingId":    booking_id,
            "attendeeName": attendee_name,
            "status":       status,
            "issuedAt":     datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"),
        }
        body.update(extra)
        resp = requests.post(f"{ticket_url}/api/tickets", json=body,
                             headers=headers or auth_headers, timeout=10)
        assert resp.status_code in (200, 201), f"make_ticket failed: {resp.text}"
        return resp.json()
    return _create


@pytest.fixture
def make_ticket_sale(sales_url, booking_url, auth_headers):
    """Factory fixture: process a ticket sale via M1 S5-F4 endpoint.

    Section 4.5 (M1 S5-F4): POST /api/sales/booking/{bookingId}
    Returns parsed JSON dict of the created TicketSale.
    """
    def _create(booking_id, simulate_failure=False, headers=None):
        params = {}
        if simulate_failure:
            params["simulateFailure"] = "true"
        resp = requests.post(
            f"{sales_url}/api/sales/booking/{booking_id}",
            params=params,
            headers=headers or auth_headers,
            timeout=10,
        )
        assert resp.status_code in (200, 201), f"make_ticket_sale failed: {resp.text}"
        return resp.json()
    return _create


@pytest.fixture
def set_booking_status(booking_url, admin_headers):
    """Helper: PATCH/PUT the booking status to a target value."""
    def _update(booking_id, new_status, headers=None):
        resp = requests.put(
            f"{booking_url}/api/bookings/{booking_id}",
            json={"status": new_status},
            headers=headers or admin_headers,
            timeout=10,
        )
        assert resp.status_code in (200, 201), (
            f"set_booking_status({new_status}) failed: {resp.text}"
        )
        return resp.json()
    return _update


@pytest.fixture
def set_sale_status(sales_url, admin_headers):
    """Helper: PUT the ticket sale status to a target value (e.g., COMPLETED)."""
    def _update(sale_id, new_status, headers=None):
        resp = requests.put(
            f"{sales_url}/api/sales/{sale_id}",
            json={"status": new_status},
            headers=headers or admin_headers,
            timeout=10,
        )
        assert resp.status_code in (200, 201), (
            f"set_sale_status({new_status}) failed: {resp.text}"
        )
        return resp.json()
    return _update


@pytest.fixture
def fresh_booking(event_url, booking_url, admin_headers, auth_user):
    """Create a fresh event + COMPLETED booking via the service endpoints.

    Ensures every test that needs a booking builds it from scratch rather
    than relying on pre-seeded data (userId=1, eventId=1).

    Returns dict keys: booking_id, event_id, user_id, booking_headers.
    """
    from datetime import datetime
    suffix = uuid.uuid4().hex[:6]
    ev = requests.post(f"{event_url}/api/events", json={
        "name":      f"TestEv {suffix}",
        "category":  "CONCERT",
        "venue":     "Test Venue",
        "eventDate": "2027-08-01T10:00:00",
        "status":    "UPCOMING",
        "rating":    0.0,
        "details":   {"description": "auto-created test event"},
    }, headers=admin_headers, timeout=10)
    assert ev.status_code in (200, 201), (
        f"fresh_booking: event creation failed ({ev.status_code}): {ev.text}"
    )
    event_id = ev.json()["id"]

    bk = requests.post(f"{booking_url}/api/bookings", json={
        "eventId":      event_id,
        "userId":       auth_user["user_id"],
        "status":       "COMPLETED",
        "totalAmount":  100.0,
        "bookingDate":  datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%S"),
        "contactEmail": "contact@test.com",
    }, headers=auth_user["headers"], timeout=10)
    assert bk.status_code in (200, 201), (
        f"fresh_booking: booking creation failed ({bk.status_code}): {bk.text}"
    )

    return {
        "booking_id":      bk.json()["id"],
        "event_id":        event_id,
        "user_id":         auth_user["user_id"],
        "booking_headers": auth_user["headers"],
    }
