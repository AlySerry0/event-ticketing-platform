# Event Ticketing Platform — Runbook

## Table of Contents

1. [Environment Setup (Docker Desktop Kubernetes)](#1-environment-setup)
2. [End-to-End Flow (Saga Happy Path)](#2-end-to-end-flow-saga-happy-path)

---

## 1 — Environment Setup

### Prerequisites

- Docker Desktop with Kubernetes enabled (context: `docker-desktop`)
- `kubectl` configured: `kubectl config use-context docker-desktop`
- Maven 3.9+ and Java 25 on PATH

Verify the context before anything else:

```bash
kubectl config current-context
# Expected: docker-desktop
```

---

### Step 1 — Build all JARs

From the repo root (builds all 7 modules including `contracts`):

```bash
mvn clean package -DskipTests
```

---

### Step 2 — Build Docker images

Each service copies `target/*.jar` into an Eclipse Temurin 25 JDK image. Run these from
the repo root — Docker Desktop's daemon is already reachable so no `eval $(minikube docker-env)` needed.

```bash
docker build -t api-gateway:latest       ./api-gateway
docker build -t user-service:latest      ./user-service
docker build -t event-service:latest     ./event-service
docker build -t booking-service:latest   ./booking-service
docker build -t ticket-service:latest    ./ticket-service
docker build -t sales-service:latest     ./sales-service
```

All deployments use `imagePullPolicy: IfNotPresent`, so Kubernetes will use these local images
without attempting a registry pull.

---

### Step 3 — Apply Kubernetes manifests (order matters)

```bash
# 1. Namespaces
kubectl apply -f k8s/namespaces/

# 2. Secrets (JWT key, DB passwords, Neo4j credentials)
kubectl apply -f k8s/secrets/

# 3. ConfigMaps (service env vars, gateway routes)
kubectl apply -f k8s/configmaps/

# 4. PersistentVolumeClaims (Postgres x5, Redis, Mongo, Cassandra, Neo4j, ES, RabbitMQ)
kubectl apply -f k8s/pvcs/

# 5. Services (ClusterIP for all backends + infra)
kubectl apply -f k8s/services/

# 6. StatefulSets (all databases and brokers)
kubectl apply -f k8s/statefulsets/

# 7. Application Deployments
kubectl apply -f k8s/deployments/

# 8. API Gateway (last — depends on all upstream services being registered)
kubectl apply -f k8s/api-gateway/
```

---

### Step 4 — Wait for infrastructure to be ready

StatefulSets (especially Cassandra and Elasticsearch) take the longest. Wait for them before the
application pods start receiving traffic:

```bash
kubectl rollout status statefulset/cassandra       -n eventticketing --timeout=300s
kubectl rollout status statefulset/rabbitmq        -n eventticketing --timeout=300s
kubectl rollout status statefulset/elasticsearch   -n eventticketing --timeout=300s
```

---

### Step 5 — Cassandra keyspace (automatic)

ticket-service manages its own Cassandra schema. `CassandraKeyspaceConfig` registers a
`CqlSessionBuilderCustomizer` that opens a keyspace-less bootstrap session and issues
`CREATE KEYSPACE IF NOT EXISTS eventticketingks` before Spring Data Cassandra's main session
connects. No manual step is needed. If Cassandra is temporarily unreachable the warning is
logged and only scan endpoints fail — the service still starts.

---

### Step 5b — Status columns lost after service restart (known DDL issue)

Several services use `ddl-auto: update`. On each restart Hibernate drops and recreates named PostgreSQL enum types with `DROP TYPE IF EXISTS ... CASCADE`, which also silently drops any column that uses that type. Hibernate then fails to re-add those columns because existing rows have no default. Run the relevant command after restarting a service if you hit a 500 with `column "X" of relation "Y" does not exist`.

**booking-service** (symptom: `POST /api/bookings` → 500):
```bash
kubectl exec -n eventticketing statefulset/booking-postgres -- \
  psql -U postgres -d etdb-bookings -c "
    ALTER TABLE bookings      ADD COLUMN IF NOT EXISTS status bookingstatus      NOT NULL DEFAULT 'PENDING';
    ALTER TABLE booking_items ADD COLUMN IF NOT EXISTS status bookingitemstatus  NOT NULL DEFAULT 'PENDING';
  "
```

**user-service** (symptom: `POST /api/auth/register` → 500):
```bash
kubectl exec -n eventticketing statefulset/user-postgres -- \
  psql -U postgres -d etdb-users -c "
    ALTER TABLE users ADD COLUMN IF NOT EXISTS role   userrole   NOT NULL DEFAULT 'ATTENDEE';
    ALTER TABLE users ADD COLUMN IF NOT EXISTS status userstatus NOT NULL DEFAULT 'ACTIVE';
  "
```

---

### Step 6 — Verify all 17 pods are healthy

```bash
kubectl get pods -n eventticketing
```

Expected pods and their roles:

| Pod | Role |
|-----|------|
| `api-gateway-*` | Spring Cloud Gateway, NodePort 30080 |
| `user-service-*` | User/Auth service |
| `event-service-*` | Event & session management |
| `booking-service-*` | Booking + saga orchestrator |
| `ticket-service-*` | Ticket issuance & scanning |
| `sales-service-*` | Payment processing |
| `user-postgres-0` | PostgreSQL for users (etdb-users) |
| `event-postgres-0` | PostgreSQL for events (etdb-events) |
| `booking-postgres-0` | PostgreSQL for bookings (etdb-bookings) |
| `ticket-postgres-0` | PostgreSQL for tickets (etdb-tickets) |
| `sales-postgres-0` | PostgreSQL for sales (etdb-sales) |
| `redis-0` | Redis cache (booking analytics, activity feed) |
| `mongo-0` | MongoDB (event logs, ticket scan metadata) |
| `cassandra-0` | Cassandra (ticket scan history — keyspace `eventticketingks`) |
| `neo4j-0` | Neo4j (booking recommendations graph) |
| `elasticsearch-0` | Elasticsearch (event full-text search) |
| `rabbitmq-0` | RabbitMQ (saga async events) |

All 17 pods should show `Running` with `1/1` READY. The API gateway is exposed at `localhost:30080`.

```bash
# Quick health check via gateway
curl -s http://localhost:30080/actuator/health | jq .status
# Expected: "UP"
```

---

## 2 — End-to-End Flow (Saga Happy Path)

All requests go through the API gateway at `http://localhost:30080`.

---

### Step 1 — Register a user

```bash
curl -s -X POST http://localhost:30080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Smith",
    "email": "alice@example.com",
    "password": "secret123",
    "phone": "+1-555-0101"
  }'
```

**Expected:** `201 Created`

```json
{
  "token": "<jwt>",
  "expiresIn": 86400
}
```

The response contains no `userId` field. Decode the JWT payload to extract it:

```bash
TOKEN=<token from response>
USER_ID=$(echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin)['uid'])")
echo $USER_ID
```

---

### Step 2 — Login and capture JWT

```bash
TOKEN=$(curl -s -X POST http://localhost:30080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}' \
  | jq -r '.token')

echo $TOKEN
```

**Expected:** `200 OK`, `token` field populated.

---

### Step 3 — Create an event

```bash
EVENT_ID=$(curl -s -X POST http://localhost:30080/api/events \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "name": "Tech Conference 2026",
    "venue": "Convention Centre Hall A",
    "eventDate": "2026-09-15T09:00:00",
    "category": "CONFERENCE",
    "status": "UPCOMING",
    "details": {"organizer": "Team7", "maxAttendees": 500}
  }' | jq -r '.id')

echo $EVENT_ID
```

**Expected:** `201 Created`, `id` field populated.

---

### Step 4 — Add an event session

```bash
SESSION_ID=$(curl -s -X POST http://localhost:30080/api/events/${EVENT_ID}/sessions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "title": "Opening Keynote",
    "speaker": "Dr. Jane Doe",
    "startTime": "2026-09-15T09:00:00",
    "endTime": "2026-09-15T10:30:00",
    "capacity": 200
  }' | jq -r '.id')

echo $SESSION_ID
```

**Expected:** `201 Created`, `id` field populated.

---

### Step 5 — Place a booking

A booking starts in `PENDING` status with no `eventId` yet — the event is assigned at confirm time.

```bash
BOOKING_ID=$(curl -s -X POST http://localhost:30080/api/bookings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "userId": '"${USER_ID}"',
    "contactEmail": "alice@example.com"
  }' | jq -r '.id')

echo $BOOKING_ID
```

**Expected:** `200 OK`, booking with `"status":"PENDING"`.

---

### Step 6 — Add booking items

Items link the booking to a specific session and quantity.

```bash
curl -s -X POST http://localhost:30080/api/bookings/${BOOKING_ID}/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '[
    {
      "sessionId": '"${SESSION_ID}"',
      "sessionTitle": "Opening Keynote",
      "quantity": 2,
      "unitPrice": 75.00,
      "eventOrder": 1
    }
  ]'
```

**Expected:** `200 OK`, booking object now contains `bookingItems` array.

---

### Step 7 — Confirm booking (assigns event, publishes `booking.placed`)

Confirming the booking associates it with the event and transitions status to `CONFIRMED`.
booking-service also publishes a `booking.placed` RabbitMQ event consumed by other services.

```bash
curl -s -X PUT "http://localhost:30080/api/bookings/${BOOKING_ID}/confirm?eventId=${EVENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}"
```

**Expected:** `200 OK`, booking with `"status":"CONFIRMED"`, `eventId` set, `confirmedAt` populated.

---

### Step 8 — Issue a ticket

ticket-service creates a Cassandra-backed ticket record for the booking.

```bash
TICKET_ID=$(curl -s -X POST http://localhost:30080/api/tickets/booking/${BOOKING_ID} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "attendeeName": "Alice Smith",
    "ticketCode": "TKT-2026-001"
  }' | jq -r '.id')

echo $TICKET_ID
```

**Expected:** `201 Created`, ticket object with `"status":"ISSUED"` (or `"VALID"`).

---

### Step 9 — Scan ticket → mark as USED

Record the scan event (stored in Cassandra under keyspace `eventticketingks`):

```bash
curl -s -X POST http://localhost:30080/api/tickets/${TICKET_ID}/scan \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "location": "Gate A",
    "scannedAt": "2026-09-15T08:55:00"
  }'
```

**Expected:** `201 Created` (empty body).

Then mark the ticket as USED via the generic update:

```bash
curl -s -X PUT http://localhost:30080/api/tickets/${TICKET_ID} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"status":"USED"}'
```

**Expected:** `200 OK`, ticket with `"status":"USED"`.

---

### Step 10 — Check-in booking → CHECKED_IN

Use the generic booking update to advance status:

```bash
curl -s -X PUT http://localhost:30080/api/bookings/${BOOKING_ID} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"status":"CHECKED_IN"}'
```

**Expected:** `200 OK`, booking with `"status":"CHECKED_IN"`.

---

### Step 10b — Advance event to ONGOING

`completeBooking` requires the event status to be `ONGOING` or `COMPLETED`. Advance it now:

```bash
curl -s -X PUT http://localhost:30080/api/events/${EVENT_ID}/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{"status":"ONGOING"}'
```

**Expected:** `200 OK`.

> Note: `confirmBooking` (step 7) requires the event to be `UPCOMING`. The status must therefore be
> advanced *after* confirmation but *before* completing the booking.

---

### Step 11 — Complete booking → triggers saga (COMPLETING → PAYMENT_PENDING)

This PUT triggers the saga. booking-service transitions to `COMPLETING`, publishes a
`booking.completed` event on RabbitMQ, and sales-service consumes it to create a pending
`TicketSale` record, after which booking-service transitions to `PAYMENT_PENDING`.

```bash
curl -s -X PUT http://localhost:30080/api/bookings/${BOOKING_ID}/complete \
  -H "Authorization: Bearer ${TOKEN}"
```

**Expected:** `200 OK`, booking with `"status":"COMPLETING"`.

> **Wait ~2–3 seconds** for RabbitMQ event propagation before polling.

```bash
# Poll until PAYMENT_PENDING
curl -s http://localhost:30080/api/bookings/${BOOKING_ID} \
  -H "Authorization: Bearer ${TOKEN}" | jq .status
# Expected: "PAYMENT_PENDING"
```

---

### Step 12 — Process payment → POST /api/sales/booking/{id}

```bash
curl -s -X POST http://localhost:30080/api/sales/booking/${BOOKING_ID} \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "method": "CREDIT_CARD",
    "cardLastFour": "4242"
  }'
```

**Expected:** `201 Created`, `TicketSaleDTO` with `"status":"COMPLETED"` and `amount` populated.

> Pass `?simulateFailure=true` to test the `PAYMENT_FAILED` → `PAYMENT_PENDING` retry path.

---

### Step 13 — Poll booking until PAID

sales-service publishes a `payment.completed` event after processing. booking-service consumes
it and transitions to `PAID`.

> **Wait ~2–3 seconds** for RabbitMQ propagation before polling.

```bash
# Poll until PAID
curl -s http://localhost:30080/api/bookings/${BOOKING_ID} \
  -H "Authorization: Bearer ${TOKEN}" | jq .status
# Expected: "PAID"
```

---

### Saga state machine summary

```
PENDING
  └─ POST /api/bookings/{id}/items        → items attached (still PENDING)
  └─ PUT  /api/bookings/{id}/confirm      → CONFIRMED  (booking.placed published)
  └─ PUT  /api/bookings/{id}              → CHECKED_IN (manual update)
  └─ PUT  /api/bookings/{id}/complete     → COMPLETING
       └─ (RabbitMQ: booking.completed)  → PAYMENT_PENDING  (sales creates TicketSale)
       └─ POST /api/sales/booking/{id}   → (saga continues in sales-service)
            └─ (RabbitMQ: payment.*)     → PAID  |  PAYMENT_FAILED
```

---

### Useful diagnostic commands

```bash
# Tail logs for any service
kubectl logs -n eventticketing -l app=booking-service  --tail=100 -f
kubectl logs -n eventticketing -l app=sales-service    --tail=100 -f
kubectl logs -n eventticketing -l app=ticket-service   --tail=100 -f

# RabbitMQ management UI (port-forward)
kubectl port-forward -n eventticketing rabbitmq-0 15672:15672
# Open http://localhost:15672  guest/guest

# Force restart a crashed pod
kubectl rollout restart deployment/ticket-service -n eventticketing
```
