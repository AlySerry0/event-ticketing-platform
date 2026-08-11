# Event Ticketing Platform

A distributed event-ticketing system built by a 13-engineer team for *Architecture of Massively Scalable Applications* (German University in Cairo, Spring 2026). Delivered across three milestones, taking the system from a shared-database Spring Boot app to a true polyglot-persistence microservices architecture.

**My services:** `booking-service` and `ticket-service` (2 of 6).

## Architecture

Six independently deployable Spring Boot services, each with its own PostgreSQL database:

- **api-gateway** — single entry point, JWT validation
- **user-service** — accounts, authentication (JWT + BCrypt)
- **event-service** — event catalog, full-text search (Elasticsearch)
- **booking-service** — booking lifecycle, saga orchestration, attendance graph (Neo4j)
- **ticket-service** — ticket issuance/validation, scan-event tracking (Cassandra)
- **sales-service** — payments, refunds, promotions

## Cross-cutting infrastructure

- **Inter-service communication:** synchronous [OpenFeign](https://spring.io/projects/spring-cloud-openfeign) calls for reads, asynchronous [RabbitMQ](https://www.rabbitmq.com/) events (with dead-letter queues + idempotent consumers) for the booking → payment saga
- **Polyglot persistence:** PostgreSQL (per-service), MongoDB (event-sourced audit logging), Redis (caching), Neo4j (attendance/recommendation graph), Cassandra (ticket-scan time series)
- **Design patterns:** 7 GoF patterns applied by spec — Strategy, Observer, Chain of Responsibility, Builder, Singleton, Factory, Adapter
- **Deployment:** Docker Compose for local dev, Kubernetes manifests (`k8s/`) for cluster deployment
- **Observability:** Prometheus + Loki

## Milestones

| Milestone | Focus |
|---|---|
| M1 | 5-service Maven multi-module app, shared PostgreSQL, 45 CRUD/feature endpoints |
| M2 | Polyglot persistence (Mongo/Redis/Elasticsearch/Neo4j/Cassandra), JWT auth, 7 design patterns |
| M3 | True microservice isolation — per-service databases, OpenFeign, RabbitMQ sagas, Kubernetes |

## Team

See [`team.json`](./team.json) for the full roster and per-service ownership.

---

*This repository is a standalone copy of the team's coursework, published for portfolio purposes. Only the `main` branch history is included.*
