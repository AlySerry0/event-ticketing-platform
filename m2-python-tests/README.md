# Milestone 2 — Python Integration & E2E Test Suite

End-to-end pytest suite that verifies all Milestone 2 functional and cross-cutting requirements
against **live, running microservices**. Every test makes real HTTP requests and (where needed)
directly queries databases to verify side-effects that the API alone cannot expose.

---

## Quick Start

```bash
# 1. Start the full stack
cd <project-root>
docker compose up -d

# 2. Wait for all services to report healthy (~60 s on first run)
docker compose ps          # all containers should show "Up (healthy)"

# 3. Install Python dependencies (one-time)
cd m2-python-tests
pip install -r requirements.txt

# 4. Run the full suite
pytest
```

---

## Prerequisites

| Requirement | Detail |
|---|---|
| Docker Compose stack | All 11 containers up and healthy (see below) |
| Python 3.9+ | Tested on 3.13 |
| pip packages | `pip install -r requirements.txt` |

### Expected containers

| Container | Role | Default port |
|---|---|---|
| `user-service` | Spring Boot – auth, users | 8081 |
| `event-service` | Spring Boot – events | 8082 |
| `booking-service` | Spring Boot – bookings | 8083 |
| `ticket-service` | Spring Boot – tickets | 8084 |
| `sales-service` | Spring Boot – sales | 8085 |
| `eventticketing-db` | PostgreSQL | 5432 |
| `eventticketing-mongo` | MongoDB | 27017 |
| `eventticketing-redis` | Redis | 6379 |
| `eventticketing-elasticsearch` | Elasticsearch | 9200 |
| `eventticketing-neo4j` | Neo4j | 7687 / 7474 |
| `eventticketing-cassandra` | Cassandra | 9042 |

---

## Running Tests

### Full suite
```bash
pytest
```

### Single test file
```bash
pytest tests/test_01_user_auth.py -v
pytest tests/test_03_booking.py -v
pytest tests/test_caching_invalidation.py -v
```

### Single test class
```bash
pytest tests/test_05_sales.py::TestS5F12RefundWindowPolicy -v
pytest tests/test_03_booking.py::TestS3F10BookingAnalytics -v
```

### Single test
```bash
pytest "tests/test_01_user_auth.py::TestS1F10Register::test_register_returns_201_with_token" -v
```

### Quiet mode (summary only)
```bash
pytest -q                  # one dot per test
pytest --tb=no -q          # no tracebacks, just pass/fail counts
```

### Stop on first failure
```bash
pytest -x
```

---

## Configuration

All connection details default to the local Docker Compose stack.
Override any of them with environment variables for CI or remote targets:

| Variable | Default | Description |
|---|---|---|
| `USER_SERVICE_URL` | `http://localhost:8081` | User service |
| `EVENT_SERVICE_URL` | `http://localhost:8082` | Event service |
| `BOOK_SERVICE_URL` | `http://localhost:8083` | Booking service |
| `TKT_SERVICE_URL` | `http://localhost:8084` | Ticket service |
| `SALES_SERVICE_URL` | `http://localhost:8085` | Sales service |
| `ADMIN_EMAIL` | `admin@admin.com` | Seeded admin email |
| `ADMIN_PASSWORD` | `adminpass` | Seeded admin password |
| `PG_DSN` | `host=localhost port=5432 dbname=eventticketingdb user=postgres password=postgres` | PostgreSQL DSN |
| `MONGO_URI` | `mongodb://root:rootpass@localhost:27017/eventticketingmongo?authSource=admin` | MongoDB URI |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASS` | `redispass` | Redis password |
| `ES_URL` | `http://localhost:9200` | Elasticsearch URL |
| `NEO4J_URI` | `bolt://localhost:7687` | Neo4j bolt URI |
| `NEO4J_USER` | `neo4j` | Neo4j username |
| `NEO4J_PASS` | `neo4jpass` | Neo4j password |
| `CASSANDRA_HOST` | `localhost` | Cassandra host |
| `CASSANDRA_PORT` | `9042` | Cassandra port |
| `CASSANDRA_KEYSPACE` | `eventticketingks` | Cassandra keyspace |

---

## Test Files

### `test_01_user_auth.py`

Tests the User Service (port 8081).

| Class | What it tests |
|---|---|
| `TestS1F10Register` | `POST /api/auth/register` — 201 + JWT, duplicate email/phone 409, missing fields 400 |
| `TestS1F11Login` | `POST /api/auth/login` — 200 + JWT, wrong password 401, non-existent email 401 |
| `TestS1F12ActivityFeed` | `GET /api/users/{id}/activity` — returns `auth_events` MongoDB docs (REGISTERED, LOGGED_IN) |
| `TestCC1JwtProtection` | All service endpoints require a valid Bearer token — 401 without one |
| `TestCC2RoleManagement` | `PUT /api/users/{id}/role` — ADMIN can promote; ATTENDEE cannot (403) |
| `TestM1BCryptRoles` | Password stored as BCrypt hash; ADMIN never assignable via register |
| `TestDP3ChainOfResponsibility` | Source scan: `AuthHandler`/`AuthContext` CoR classes exist |
| `TestDP5Singleton` | Source scan: `JwtConfigurationManager` uses Singleton pattern |

### `test_02_event_search.py`

Tests the Event Service (port 8082).

| Class | What it tests |
|---|---|
| `TestS2F10FullTextSearch` | `GET /api/events/search/full-text` — fuzzy name/venue/category search via Elasticsearch |
| `TestS2F11EventIndex` | `POST /api/events/{id}/index` — explicit ES indexing; CRUD auto-index on POST/PUT |
| `TestS2F12Dashboard` | `GET /api/events/{id}/dashboard` — aggregated stats (total bookings, revenue, tickets) |
| `TestDP7ElasticsearchAdapter` | Source scan: `ElasticsearchDocumentAdapter` class exists in event-service |
| `TestAutoIndexRetrofit` | Creating/updating events via CRUD automatically indexes to Elasticsearch |

### `test_03_booking.py`

Tests the Booking Service (port 8083).

| Class | What it tests |
|---|---|
| `TestS3F10BookingAnalytics` | `GET /api/bookings/analytics?startDate=&endDate=` — booking stats, ANALYTICS_VIEWED to MongoDB |
| `TestS3F11RecordAttendance` | `POST /api/bookings/{id}/record-attendance` — Neo4j ATTENDED relationship, idempotency, MongoDB event |
| `TestS3F12Recommendations` | `GET /api/bookings/recommendations?userId=` — collaborative filtering via Neo4j, ownership 403 |
| `TestDP7Neo4jAdapter` | Source scan: `Neo4jRecordAdapter` and `MongoDocumentAdapter` classes exist in booking-service |

### `test_04_tickets.py`

Tests the Ticket Service (port 8084).

| Class | What it tests |
|---|---|
| `TestS4F10TicketAnalytics` | `GET /api/tickets/analytics?startDate=&endDate=` — counts by status, ANALYTICS_VIEWED to MongoDB, Redis cached |
| `TestS4F11RecordScanEvent` | `POST /api/tickets/{id}/scan` — Cassandra write, TRACKING_RECORDED to MongoDB, cache invalidation |
| `TestS4F12ScanHistory` | `GET /api/tickets/{id}/scans` — scan history from Cassandra, Redis cached |
| `TestDP7CassandraAdapter` | Source scan: `CassandraRowAdapter` and `MongoDocumentAdapter` classes exist in ticket-service |

### `test_05_sales.py`

Tests the Sales Service (port 8085).

| Class | What it tests |
|---|---|
| `TestS5F10TicketSalesByTier` | `GET /api/sales/analytics/tier?startDate=&endDate=` — tier revenue breakdown, ANALYTICS_VIEWED to MongoDB, Redis cached |
| `TestS5F11SaleAuditTrail` | `GET /api/sales/{id}/audit-trail` — MongoDB audit events (CREATED, COMPLETED), sorted ASC, excludes ANALYTICS_VIEWED, Redis cached |
| `TestS5F12RefundWindowPolicy` | `POST /api/sales/{id}/refund-window-policy` — Strategy pattern: >48h full refund, 24-48h partial, <24h 400 + REFUND_DENIED |
| `TestDP1StrategyPattern` | Source scan: `RefundStrategy`, `FullWindowRefundStrategy`, `PartialWindowRefundStrategy`, `NoRefundStrategy` |
| `TestDP2ObserverPattern` | Source scan: `EntityObserver`/`EntitySubject`/`MongoEventLogger` classes |
| `TestDP4BuilderPattern` | Source scan: `TierRevenueDTO` Builder pattern; response has required fields |
| `TestDP6FactoryPattern` | Source scan: `EventFactory`/`PaymentAuditEvent` Factory pattern |
| `TestDP7MongoAdapter` | Source scan: `MongoDocumentAdapter` class exists in sales-service |
| `TestM1DesignPatternRetrofits` | S5-F4 `simulateFailure=true` writes FAILED to MongoDB; S5-F5 promotion writes PROMOTION_APPLIED; Observer NOT via `@EventListener` |

### `test_caching_invalidation.py`

Tests Redis caching (CC-3) and cache invalidation (Section 4.4) across all services.

| Class | What it tests |
|---|---|
| `TestM2FeatureGetCaching` | First call populates Redis; second call hits cache (no extra DB query). Covers S2-F1, S3-F1, S3-F10, S3-F12, S4-F1, S4-F10, S4-F12, S5-F1, S5-F3, S5-F8, S5-F10, S5-F11 |
| `TestM2CrudCaching` | CRUD GET endpoints cache the entity by ID (event, booking, ticket) |
| `TestM2CacheInvalidation` | PUT/DELETE evict the cached entry; search caches evicted on writes |
| `TestCC5DockerCompose` | `docker-compose.yaml`/`.yml` exists and defines ≥6 database services |
| `TestCC6ApplicationYml` | Each service has `application.yml` with Redis, Mongo, and Cassandra config |

---

## Understanding Results

### Pass / Skip / Fail

- **PASSED** — requirement fully met.
- **SKIPPED** — a database fixture is unavailable (e.g. Neo4j unreachable). The test was not run; this is not a failure of the implementation.
- **FAILED** — an assertion was not met. The test output shows the exact assertion and the actual value received.

### Reading a failure

```
FAILED tests/test_03_booking.py::TestS3F11RecordAttendance::test_record_attendance_returns_200
AssertionError: record-attendance must return 200, got 404: {"error":"..."}
```

The class name (`TestS3F11RecordAttendance`) maps directly to a spec feature (S3-F11).
The method name (`test_record_attendance_returns_200`) describes the exact scenario.
The assertion message explains what was expected and what arrived.

### Current results (as of this commit)

| Result | Count |
|---|---|
| **Passed** | **209** |
| **Skipped** | 5 |
| **Failed** | 14 |

### Remaining failures and why

All 14 remaining failures fall into three categories of **unimplemented features**:

#### S3-F11 Record Attendance (6 tests)

`POST /api/bookings/{id}/record-attendance` does not exist in booking-service.

Requires:
- Endpoint that accepts a COMPLETED booking ID
- Creates a `(User)-[:ATTENDED]->(Event)` relationship in Neo4j with `attendanceCount` and idempotency tracking via `recordedBookingIds`
- Writes `INTERACTION_RECORDED` to MongoDB `booking_events` via the existing Observer chain
- Returns 400 for non-COMPLETED bookings, 404 for missing bookings, 200 on success (idempotent)

#### S3-F12 Recommendations (6 tests + 1 caching test)

`GET /api/bookings/recommendations?userId={id}` does not exist in booking-service.

Requires:
- Neo4j collaborative-filtering query: find events attended by other users who share at least one attended event with the given user, excluding events the user already attended
- Ownership check: 403 if the authenticated user's ID does not match `userId` (ADMIN bypasses)
- 404 if `userId` is not a known user
- Response is a list of objects with at least an `eventId` field
- Result cached in Redis under `booking-service::S3-F12::{userId}` (5-minute TTL)
- Cache invalidated when `record-attendance` is called for the same user

#### S1-F12 Activity Feed — LOGGED_IN missing (1 test)

`GET /api/users/{id}/activity` returns the user's `auth_events` from MongoDB.
The `REGISTERED` event is written correctly on register.
The `LOGGED_IN` event is **never written** in `AuthService.login()` — `notifyObservers("LOGGED_IN", ...)` is not called.

Fix: add `notifyObservers("LOGGED_IN", ...)` at the end of the `login()` method in `user-service/AuthService.java`, mirroring the existing `REGISTERED` call in `register()`.

---

## Soft vs Hard Dependencies

**Hard dependency — PostgreSQL:** The Spring Boot services will not start without it. If Postgres is down, the entire suite fails at fixture setup.

**Soft dependencies — MongoDB, Redis, Neo4j, Cassandra, Elasticsearch:** Each has a session-scoped fixture that issues a `ping` or `verify_connectivity`. If the database is unreachable, **all tests that require that fixture are automatically skipped** (not failed). This lets you run the API-only tests even with partial infrastructure.

You will see skips reported like:
```
SKIPPED [1] conftest.py:174 [SKIP] Neo4j unavailable: ...
```

---

## File Structure

```
m2-python-tests/
├── conftest.py                    # Session fixtures: URLs, DB connections, auth tokens
├── pytest.ini                     # pytest config: timeout=30s, -v, --tb=short
├── requirements.txt               # pip dependencies
├── README.md                      # this file
└── tests/
    ├── __init__.py
    ├── test_01_user_auth.py       # User Service
    ├── test_02_event_search.py    # Event Service
    ├── test_03_booking.py         # Booking Service
    ├── test_04_tickets.py         # Ticket Service
    ├── test_05_sales.py           # Sales Service
    └── test_caching_invalidation.py  # Redis caching + config checks
```
