# Analytics Manager — Product Requirements Document

*Version 1.0 · Single source of truth for development · Scope: one developer, local/VM environment*

**How to read this document.** Every feature is tagged **\[MVP\]**, **\[OPTIONAL\]** or **\[FUTURE\]**. Concrete choices are labelled **Decision**. Unverified beliefs are labelled **Assumption**. Compromises are labelled **Trade-off**. Anything not tagged MVP must not be claimed on a resume until it is actually built.

---

## 1. Product Overview

**Analytics Manager** is an event-driven analytics platform. A React app generates user/client events (page views, logins, purchases, errors, etc.) and sends them over REST to a Spring Boot backend. The backend validates each event and publishes it to Apache Kafka. A Kafka consumer (in the same Spring Boot application, in a separate package and consumer group) processes events asynchronously, persists them to PostgreSQL, and maintains pre-aggregated analytics. Spring Boot Actuator and Micrometer expose metrics to Prometheus, and Grafana visualizes system health and event throughput in real time.

The product has two faces:

1. **Analytics product** — an Event Simulator, a Dashboard and an Event History page.
2. **Engineering showcase** — a Grafana dashboard that lets you generate load from React and watch Kafka, the consumer and the database react live.

**Decision: modular monolith, not microservices.** One Spring Boot application with clean internal package boundaries (`ingestion`, `consumer`, `analytics`, `common`). Producer and consumer are logically separated (different packages, different consumer group, no shared mutable state) and could be extracted later, but running them in one JVM keeps the project debuggable and demoable by one person.

## 2. Problem Statement

Synchronous request/response systems that write every event straight to a database become a bottleneck under bursty traffic and couple the user-facing API to database availability. Teams need to ingest high-volume events reliably, process them asynchronously, avoid losing or double-counting events, and see in real time whether the pipeline is healthy.

Analytics Manager demonstrates a pragmatic answer: accept events quickly, buffer them durably in Kafka, process them at the consumer's own pace with retries and a dead-letter topic, guarantee idempotent results, and expose the whole pipeline through metrics.

**Personal problem (learning goal):** build a project that shows real, explainable understanding of Kafka, event-driven design and observability, beyond a tutorial-level "send a message, print a message" demo.

## 3. Goals

| # | Goal | Success measure |
| --- | --- | --- |
| G1 | Ingest events through REST and publish to Kafka reliably | 100% of accepted (HTTP 202) events are acknowledged by the broker |
| G2 | Process events asynchronously with a Kafka consumer | Events appear in PostgreSQL and aggregates within seconds |
| G3 | Guarantee no double counting | Replaying/duplicating an event leaves analytics unchanged |
| G4 | Handle failure explicitly | Retries with backoff, then DLQ; no consumer crash loops |
| G5 | Provide real analytics | Summary, by-type, over-time, by-client, top pages |
| G6 | Full observability | Prometheus scrapes the app; Grafana shows throughput, latency, errors, lag |
| G7 | Demoable in 5–10 minutes | Scripted demo scenario (Section 25) works from a clean start |
| G8 | Interview-ready | Every design choice has a documented reason and trade-off |

## 4. Non-Goals

- Not a production analytics SaaS (no multi-tenancy, billing, SLAs).
- No Kubernetes, no cloud services, no service mesh.
- No microservices split in V1.
- No Kafka Streams, Schema Registry, Avro/Protobuf in V1 (listed as FUTURE).
- No exactly-once semantics across Kafka and PostgreSQL; the project uses **at-least-once delivery plus idempotent processing**, which is the standard practical approach and is explained in Section 12.
- Authentication is not required in V1.
- No frontend direct connection to Kafka, ever.
- No real-time WebSocket push in V1 (dashboard polls).

## 5. Target Users

| User | Description | Primary need |
| --- | --- | --- |
| **Developer/Demonstrator (primary)** | You, presenting to interviewers | Reliable, visual demo of the pipeline and failure handling |
| **Interviewer/Reviewer** | Reads README, watches demo, reads code | Clear architecture, clean code, sensible trade-offs |
| **Simulated analyst** | Persona using the dashboard | See what clients are doing and whether events are processed |
| **Simulated operator** | Persona using Grafana | See system health, lag, failures |

## 6. User Stories

**Event generation**

- US-1 \[MVP\]: As a user, I can pick an event type, client ID and page, and submit an event so I can simulate client behavior.
- US-2 \[MVP\]: As a user, I see immediately whether my event was accepted, rejected for validation errors, or failed to publish.
- US-3 \[MVP\]: As a user, I can send a **burst** of N events (e.g. 100) with randomized data to demonstrate throughput.
- US-4 \[OPTIONAL\]: As a user, I can resubmit the same event ID to demonstrate duplicate handling.
- US-5 \[OPTIONAL\]: As a user, I can send a deliberately malformed/poison event to demonstrate the DLQ (via a dev-only toggle).

**Analytics**

- US-6 \[MVP\]: As an analyst, I see total events, events per minute, events by type, active clients and failed events on a dashboard.
- US-7 \[MVP\]: As an analyst, I can browse and filter event history by type, client and time range, with pagination.
- US-8 \[MVP\]: As an analyst, I can open a single event by ID and see its processing status.
- US-9 \[MVP\]: As an analyst, I see recent events and top pages.

**Operations**

- US-10 \[MVP\]: As an operator, I see HTTP rate/latency/errors, processed vs failed events, processing latency and consumer lag in Grafana.
- US-11 \[MVP\]: As an operator, I can check health endpoints for the app, database and Kafka.
- US-12 \[MVP\]: As an operator, I can see events that ended in the DLQ and why.

## 7. Functional Requirements

**Ingestion**

- FR-1 \[MVP\] `POST /api/events` validates and publishes a single event to Kafka.
- FR-2 \[MVP\] Server generates `eventId` (UUID v4) if absent; client-supplied `eventId` must be a valid UUID (enables duplicate testing).
- FR-3 \[MVP\] Server stamps `receivedAt`, `source` and schema `version`.
- FR-4 \[MVP\] Response is returned only after the broker acknowledges the write (bounded by a timeout).
- FR-5 \[OPTIONAL\] `POST /api/events/batch` accepts up to 100 events in one request.

**Processing**

- FR-6 \[MVP\] Consumer reads from `analytics-events`, validates, persists the raw event and updates aggregates **in one DB transaction**.
- FR-7 \[MVP\] Duplicate events (same `eventId`) are detected and skipped without changing aggregates.
- FR-8 \[MVP\] Transient failures retry with exponential backoff; exhausted/non-retryable failures go to `analytics-events-dlq`.
- FR-9 \[MVP\] A DLQ listener persists failed events to `failed_events` with error details.

**Analytics**

- FR-10 \[MVP\] Summary, by-type, over-time, by-client, top-pages and recent-events endpoints (Section 15).
- FR-11 \[MVP\] Event history with filters and pagination.

**Observability and operations**

- FR-12 \[MVP\] Actuator health/metrics/prometheus endpoints with custom business metrics.
- FR-13 \[MVP\] Structured logs with `eventId` and `clientId` in the logging context (MDC).
- FR-14 \[OPTIONAL\] Dev-only replay endpoint to reprocess DLQ events.

## 8. System Architecture

### 8.1 Component diagram (logical)

```
[React + Tailwind]  --HTTP/JSON-->  [Spring Boot App]
   (host)                              |  ingestion module  --produce-->  [Kafka: analytics-events]
                                       |                                        |
                                       |  consumer module  <--consume-----------+
                                       |        |  (retry -> analytics-events-dlq)
                                       |        v
                                       |   [PostgreSQL]  <--read--  analytics module (REST)
                                       |
                                       +--/actuator/prometheus-->  [Prometheus]  -->  [Grafana]
                                                                   (Ubuntu VM)       (Ubuntu VM)
```

### 8.2 Deployment topology (V1)

| Component | Where it runs | Port |
| --- | --- | --- |
| React dev server (Vite) | Host machine | 5173 |
| Spring Boot app | Host machine (IDE) | 8080 |
| PostgreSQL | **Decision:** Ubuntu VM (keeps data tier with infra; alternative: host) | 5432 |
| Apache Kafka (KRaft, single broker) | Ubuntu VM | 9092 |
| Prometheus | Ubuntu VM | 9090 |
| Grafana | Ubuntu VM | 3000 |
| kafka-exporter (consumer lag) \[OPTIONAL\] | Ubuntu VM | 9308 |

**Decision: Kafka in KRaft mode** (no ZooKeeper). It is the current standard, needs one fewer process and is easier to explain.

### 8.3 Host ↔ VM networking (critical)

This is the most common failure point and a strong interview topic.

**VM network setup.** Use **VirtualBox/VMware with a Host-Only adapter plus NAT** (or a single Bridged adapter). Host-Only gives the VM a stable address such as `192.168.56.10`, and the host appears to the VM as `192.168.56.1`. Assign a static VM IP so configs don't break.

**Kafka listeners.** A Kafka client connects to the bootstrap address, then receives the *advertised* listener addresses in metadata and reconnects to those. If the broker advertises `localhost`, a client on the host will try to connect to itself and fail. Required `server.properties` (single node, KRaft):

```
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
listeners=EXTERNAL://0.0.0.0:9092,CONTROLLER://localhost:9093
advertised.listeners=EXTERNAL://192.168.56.10:9092
listener.security.protocol.map=EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=EXTERNAL
controller.listener.names=CONTROLLER
num.partitions=3
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
```

Spring: `spring.kafka.bootstrap-servers=192.168.56.10:9092`.

**Prometheus scraping the host app.** Prometheus runs in the VM and must reach the Spring Boot app on the host:

- Scrape target: `192.168.56.1:8080` with `metrics_path: /actuator/prometheus`.
- Spring Boot must bind to all interfaces (`server.address=0.0.0.0`, the default).
- The host firewall must allow inbound 8080 from the VM subnet.

**Grafana** (in the VM) uses `http://localhost:9090` as its Prometheus data source, and you open Grafana from the host browser at `http://192.168.56.10:3000`.

**Troubleshooting checklist** (include in README): `ping`, `nc -zv 192.168.56.10 9092`, `kafka-topics.sh --bootstrap-server ...`, `curl http://192.168.56.1:8080/actuator/health` from the VM, VM firewall (`ufw status`), and Prometheus *Targets* page showing UP.

### 8.4 Request/event lifecycle

1. React `POST /api/events`.
2. Controller validates the DTO (Bean Validation) → service enriches (eventId, receivedAt, source, version).
3. Producer sends to Kafka with `key=clientId`, waits for the broker ack (timeout \~3 s).
4. API returns `202 Accepted` with `eventId` and `status: ACCEPTED`.
5. Consumer polls, deserializes, validates, then in **one transaction**: inserts into `events` (unique `event_id`) and upserts `event_agg_minute`. Offset is committed only after the transaction commits.
6. Failures → retry with backoff → DLQ.
7. Dashboard and history read from PostgreSQL.

**Decision: the producer does not write to the database.** Writing to both Kafka and PostgreSQL in the request path is the *dual-write problem* (one can succeed while the other fails). Only the consumer writes. Consequence: a freshly accepted event is not yet visible in the database; the UI treats `GET /api/events/{id}` returning 404 as `PENDING` for a short period (see Section 14).

**Trade-off:** eventual consistency between ingestion and analytics (typically under 1–2 seconds locally) in exchange for decoupling and resilience.

## 9. Event Architecture

### 9.1 Event envelope

```json
{
  "eventId": "3f2b8c1e-5a0d-4e7b-9c1a-2d6f4b8e1a90",
  "eventType": "PRODUCT_VIEW",
  "clientId": "client-1042",
  "occurredAt": "2026-11-16T10:15:30.123Z",
  "receivedAt": "2026-11-16T10:15:30.201Z",
  "source": "react-simulator",
  "version": 1,
  "page": "/products/sku-778",
  "productId": "sku-778",
  "metadata": { "userAgent": "Chrome", "searchTerm": null, "amount": null }
}
```

### 9.2 Field rules

| Field | Required | Rules | Set by |
| --- | --- | --- | --- |
| `eventId` | Yes | UUID; generated by server if absent | Client or server |
| `eventType` | Yes | Enum (below) | Client |
| `clientId` | Yes | 1–64 chars, `[A-Za-z0-9_-]+` | Client |
| `occurredAt` | Yes (default: now) | ISO-8601 UTC; not more than 5 min in future, not older than 30 days | Client (defaulted) |
| `receivedAt` | Yes | Server timestamp | Server |
| `source` | Yes | Short string, default `react-simulator` | Server/client |
| `version` | Yes | Integer schema version, `1` in V1 | Server |
| `page` | Conditional | Required for `PAGE_VIEW`, `PRODUCT_VIEW`, `SEARCH`; max 255 chars | Client |
| `productId` | Conditional | Required for `PRODUCT_VIEW`, `PURCHASE`; max 64 chars | Client |
| `metadata` | No | JSON object, max 20 keys, max 2 KB serialized; string/number/boolean values only | Client |

### 9.3 Event types and per-type rules

| Type | Required extra fields |
| --- | --- |
| `PAGE_VIEW` | `page` |
| `LOGIN` | none |
| `PRODUCT_VIEW` | `page`, `productId` |
| `SEARCH` | `page`; `metadata.searchTerm` recommended |
| `PURCHASE` | `productId`; `metadata.amount` recommended |
| `ERROR` | `metadata.errorCode` recommended |

**Extensibility decision.** `eventType` is stored as `VARCHAR` (not a database enum), validated in code against a registry of `EventTypeValidator` strategies. Adding a type means adding one enum constant plus one validator class; no table migration, no Kafka change. The generic `metadata` JSONB column absorbs type-specific data. `version` supports evolving the schema; the consumer rejects unknown versions to the DLQ.

### 9.4 Design choices

- **Two timestamps:** `occurredAt` (business time) and `receivedAt` (ingestion time). Time-series analytics use `occurredAt`; processing latency uses `receivedAt` → persisted time.
- **JSON on the wire** (Spring Kafka `JsonSerializer`/`JsonDeserializer` with type headers disabled and a fixed target type). **Trade-off:** no compile-time schema enforcement; Avro + Schema Registry is a FUTURE item.
- **Poison-pill protection:** wrap the value deserializer with `ErrorHandlingDeserializer` so undeserializable bytes become a handled error (DLQ) instead of an infinite loop.

## 10. Frontend Requirements

**Stack:** React 18, Vite, Tailwind CSS, React Router, Axios, a small charting library (Recharts) **\[MVP\]**. Polling via `setInterval` or React Query refetch every 3–5 s.

### 10.1 Pages

**Event Simulator (`/simulator`) \[MVP\]**

- Form: client ID (text + "random" button), event type (select), page (text, shown when relevant), product ID (shown when relevant), timestamp (default now, editable), metadata (key-value editor or JSON textarea).
- Per-type conditional fields mirror the backend rules (client-side validation is a convenience; the backend is authoritative).
- Actions: **Send Event**, **Send Burst** (N events, configurable 10–500, random types/clients, configurable delay), **Resend last event** (duplicate demo) \[OPTIONAL\], **Send invalid event** \[OPTIONAL, dev only\].
- Feedback: toast with `eventId` and status; running counters (sent / accepted / rejected / failed) for the current burst.

**Analytics Dashboard (`/dashboard`) \[MVP\]**

- KPI cards: total events, events in last minute, active clients (last 5 min), failed events (DLQ count).
- Charts: events per minute (line, last 30 min), events by type (bar or donut).
- Tables: top 5 pages, recent 10 events with status badge.
- Auto-refresh every 5 s, with a visible "last updated" timestamp.

**Event History (`/events`) \[MVP\]**

- Table with columns: eventId, type, clientId, page, occurredAt, status. Filters: type, clientId, from/to. Server-side pagination and sort by time. Row click opens a detail drawer or `/events/:id`.
- Status values: `PENDING` (accepted, not yet visible), `PROCESSED`, `FAILED` (in DLQ).

**Navigation:** simple top bar with the three pages. A small "API health" indicator calls `/actuator/health`. **\[OPTIONAL\]**

### 10.2 Frontend rules

- The frontend talks **only** to the Spring Boot REST API (base URL from `VITE_API_BASE_URL`).
- Handle loading, empty, error and retry states for every data view.
- Display backend validation errors per field using the standard error body (Section 14).
- Suggested structure is in Section 21.

## 11. Backend Requirements

**Stack:** Java 17+ (recommend 21 LTS), Spring Boot 3.x, Spring Web, Spring Validation, Spring Kafka, Spring Data JPA (Hibernate), PostgreSQL driver, Flyway for migrations, Actuator, Micrometer Prometheus registry, Lombok optional, Testcontainers and JUnit 5 for tests, springdoc-openapi for Swagger UI **\[OPTIONAL but recommended\]**.

### 11.1 Layered design

- **Controller layer:** HTTP concerns only: DTO validation, status codes, mapping.
- **Service layer:** business logic, enrichment, orchestration, transaction boundaries.
- **Producer component:** wraps `KafkaTemplate`, owns publish metrics and timeout behavior.
- **Consumer component:** `@KafkaListener` with manual acknowledgement, delegates to a transactional `EventProcessingService`.
- **Repository layer:** Spring Data JPA plus a few native/JPQL aggregation queries.
- **Cross-cutting:** global `@RestControllerAdvice`, MDC logging filter, configuration classes.

### 11.2 Key implementation decisions

| Decision | Choice | Reason |
| --- | --- | --- |
| Database migrations | Flyway (no `ddl-auto=update`) | Reproducible schema, interview-worthy |
| DTO vs entity | Separate request/response DTOs and JPA entities | Avoid leaking persistence model |
| IDs | UUID `event_id` (business key) plus `BIGSERIAL id` (PK) | Efficient joins, unique business key for dedup |
| Time | `Instant` in code, `TIMESTAMPTZ` in DB, UTC everywhere | Avoids timezone bugs |
| Error format | RFC 7807 style `ProblemDetail` | Standard, consistent |
| Configuration | `application.yml` with profiles (`dev`, `docker`) and env-var overrides | Same code in host and Docker modes |
| Consumer concurrency | `concurrency=3` (matches partitions) | Shows partition-parallelism cleanly |

### 11.3 Producer behavior (detailed)

1. Validate the request DTO; reject with `400` on failure.
2. Run per-type validator; reject with `422` on semantic failure. *(Alternative: both as 400; pick one and document it. Decision: `400` for structural/field errors, `422` for cross-field type rules.)*
3. Enrich event.
4. `kafkaTemplate.send(topic, clientId, event)` returns a `CompletableFuture`; wait with `get(3, SECONDS)`.
5. Success → `202`. Timeout or broker error → `503` with `Retry-After`, increment `events.publish.failed`.

**Producer configuration:** `acks=all`, `enable.idempotence=true`, `retries` high with `delivery.timeout.ms=10000`, `max.in.flight.requests.per.connection=5` (safe with idempotence), `linger.ms=5`, `compression.type=lz4`.

**Trade-off:** waiting for the ack makes each request slightly slower (a few ms locally) but means `202` truly means "Kafka has it". The alternative (fire-and-forget) is faster but can silently lose events; rejected.

### 11.4 Consumer behavior (detailed)

1. Poll batch; for each record (record-level processing in V1) start a timer.
2. Parse and validate; an invalid payload is a **non-retryable** error → straight to DLQ.
3. `@Transactional` `EventProcessingService.process(event)`:
   - `INSERT INTO events ... ON CONFLICT (event_id) DO NOTHING`; if zero rows were inserted it is a duplicate, so skip the aggregates and count `events.duplicates`.
   - Otherwise upsert the minute aggregate (and client/page aggregates if used).
4. After the transaction commits, acknowledge the offset (`AckMode.RECORD`, `enable.auto.commit=false`).
5. On exception: the `DefaultErrorHandler` applies backoff and retries; at exhaustion the `DeadLetterPublishingRecoverer` publishes to `analytics-events-dlq` with exception headers, then the offset is committed so the partition is not blocked.

**Decision: process record by record, not in batches** (clearer semantics, easier idempotency and metrics). Batch listening is a FUTURE optimization.

## 12. Kafka Design

### 12.1 Topics

| Topic | Purpose | Partitions | Replication | Retention | Key |
| --- | --- | --- | --- | --- | --- |
| `analytics-events` | Main event stream | **3** | 1 (single broker) | 7 days | `clientId` |
| `analytics-events-dlq` | Events that failed processing | **1** | 1 | 14 days | original key |

**Why 3 partitions:** enough to demonstrate parallel consumption and key-based distribution without being excessive. The partition count can be increased later but never decreased, and increasing it changes key-to-partition mapping, which is worth knowing for interviews.

**Replication (explain, don't fake).** A real deployment uses `replication.factor=3` and `min.insync.replicas=2`. The V1 environment has one broker, so RF=1 and `acks=all` effectively means "the leader wrote it". Document this honestly: the *configuration* is production-correct (`acks=all`, idempotent producer) but durability is limited by the single broker. **\[FUTURE\]** a 3-broker KRaft cluster in Docker Compose to demonstrate RF=3 and leader failover.

Topics are created explicitly (a `NewTopic` bean or a startup script), with `auto.create.topics.enable=false` to avoid accidental topics.

### 12.2 Message keys and ordering

- **Key = `clientId`.** All events from one client land in the same partition, preserving per-client order (e.g. LOGIN before PURCHASE). No global ordering is promised, which is a standard Kafka trade-off.
- **Trade-off:** a very hot client could create a skewed partition. Acceptable for this scope; mention in interviews as a known key-skew consideration.
- Aggregates are commutative (counts), so cross-partition ordering does not affect correctness.

### 12.3 Consumer group and offsets

- Group `analytics-consumer-group` with 3 listener threads, so each owns one partition. Starting a second app instance triggers a **rebalance** (demoable in the optional scaling demo).
- `auto.offset.reset=earliest` for the group so a new group can replay history.
- **Offsets** are committed manually after the DB transaction commits. A crash between commit and ack causes redelivery, which is safe because processing is idempotent.
- Tune `max.poll.interval.ms` and `max.poll.records` (e.g. 100) so slow DB calls do not trigger rebalances.
- The DLQ consumer uses a **separate group** (`analytics-dlq-group`).

### 12.4 Delivery semantics

**At-least-once delivery + idempotent consumer = effectively-once results.**

- Producer side: `acks=all` + idempotent producer prevent loss and producer-retry duplicates within a session.
- Consumer side: the offset is committed after the DB write, so a failure leads to reprocessing rather than loss; `UNIQUE(event_id)` plus `ON CONFLICT DO NOTHING` makes reprocessing harmless.
- Duplicates can also come from the client resubmitting the same `eventId`; the same mechanism handles them.

### 12.5 Retry and dead-letter handling

- **Retries:** `ExponentialBackOff` with initial 1 s, multiplier 2, max 3 retries (about 1 s, 2 s, 4 s). Retries are **in-place blocking retries** on the listener thread.
- **Non-retryable exceptions** (deserialization error, validation error, unknown schema version) are registered via `addNotRetryableExceptions` and go straight to the DLQ.
- **Retryable exceptions:** transient DB errors, timeouts.
- **DLQ record** contains the original payload plus headers (`kafka_dlt-exception-message`, original topic/partition/offset). A `DlqConsumer` persists it into `failed_events` and increments `events.dlq`.
- **Trade-off:** blocking retries stall that partition during backoff. Non-blocking retry topics (`@RetryableTopic`) avoid this but add topics and complexity. **\[FUTURE\]**.

### 12.6 V1 vs later

| Item | V1 \[MVP\] | Later |
| --- | --- | --- |
| Topics | main + DLQ | per-type topics or retry topics |
| Brokers | 1 (KRaft) | 3-broker cluster |
| Serialization | JSON | Avro + Schema Registry |
| Retry | blocking exponential | non-blocking `@RetryableTopic` |
| Processing | per-record | batch listener, Kafka Streams |
| Consumer scaling | 3 threads in one app | multiple instances |

## 13. Database Design

**Decision:** PostgreSQL 15+, schema managed by Flyway (`V1__init.sql`, `V2__...`). All timestamps are `TIMESTAMPTZ` in UTC.

### 13.1 Tables

**`events`** — one row per successfully processed event (written only by the consumer)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGSERIAL PK | Internal key |
| `event_id` | UUID NOT NULL | **UNIQUE** business key; the dedup guard |
| `event_type` | VARCHAR(50) NOT NULL | Not a DB enum (extensibility) |
| `client_id` | VARCHAR(64) NOT NULL |  |
| `occurred_at` | TIMESTAMPTZ NOT NULL | Business time |
| `received_at` | TIMESTAMPTZ NOT NULL | API ingestion time |
| `processed_at` | TIMESTAMPTZ NOT NULL DEFAULT now() | Consumer persist time |
| `source` | VARCHAR(50) NOT NULL |  |
| `schema_version` | INT NOT NULL |  |
| `page` | VARCHAR(255) | Nullable |
| `product_id` | VARCHAR(64) | Nullable |
| `metadata` | JSONB | Nullable, max about 2 KB |
| `kafka_partition`, `kafka_offset` | INT, BIGINT | Traceability and debugging |

**`event_agg_minute`** — pre-aggregated counts. PK `(bucket_start, event_type)`; columns `bucket_start TIMESTAMPTZ`, `event_type VARCHAR(50)`, `event_count BIGINT`. Updated with `INSERT ... ON CONFLICT (bucket_start, event_type) DO UPDATE SET event_count = event_agg_minute.event_count + 1`.

**`client_stats`** — PK `client_id`; `total_events BIGINT`, `first_seen`, `last_seen`. Same upsert pattern.

**`page_stats`** — PK `page`; `view_count BIGINT`. Same upsert pattern.

**`failed_events`** — written by the DLQ listener

| Column | Type | Notes |
| --- | --- | --- |
| `id` | BIGSERIAL PK |  |
| `event_id` | UUID | Nullable (unparseable payloads have none) |
| `payload` | TEXT | Raw payload as received |
| `error_class`, `error_message` | VARCHAR, TEXT | From DLQ headers |
| `original_topic`, `original_partition`, `original_offset` | VARCHAR, INT, BIGINT | **UNIQUE** together so DLQ replays never duplicate rows |
| `failed_at` | TIMESTAMPTZ |  |

### 13.2 Indexing strategy

| Index | Purpose |
| --- | --- |
| `UNIQUE (event_id)` | Duplicate prevention plus `GET /api/events/{eventId}` lookups |
| `(occurred_at DESC)` | Recent events, time-range scans |
| `(event_type, occurred_at DESC)` | History filter by type |
| `(client_id, occurred_at DESC)` | History filter by client, active-clients query |
| `(received_at)` | Active-clients/latency windows (optional if `occurred_at` is used) |
| GIN on `metadata` | **\[FUTURE\]** only if metadata querying is added |

**Rules:** do not index every column (writes slow down); verify important queries with `EXPLAIN ANALYZE` and note one before/after example in the README (strong interview material).

### 13.3 Duplicate prevention

Three layers, in order of importance:

1. **Database `UNIQUE (event_id)`** is the final authority and is concurrency-safe.
2. **`INSERT ... ON CONFLICT DO NOTHING`** with an affected-row check, so the duplicate is detected without throwing an exception that would poison the transaction.
3. **Aggregates updated only when the insert affected 1 row**, within the same transaction, so counts never drift.

### 13.4 Retention and growth

**\[OPTIONAL\]** a scheduled job deleting raw events older than 30 days (aggregates are kept). **\[FUTURE\]** time-based table partitioning.

## 14. API Specification

**Conventions:** base path `/api`, JSON only, UTC ISO-8601 timestamps, camelCase fields. Errors use one format everywhere (RFC 7807 `ProblemDetail`):

```
{ "type": "about:blank", "title": "Validation failed", "status": 400,
  "detail": "Request validation failed", "timestamp": "2026-11-16T10:15:30Z",
  "errors": [ { "field": "clientId", "message": "must not be blank" } ] }
```

### 14.1 `POST /api/events` \[MVP\]

**Purpose:** validate, enrich and publish one event.

**Request body:** the fields in Section 9.2 (`eventType`, `clientId` required; `eventId`, `occurredAt`, `page`, `productId`, `metadata`, `source` optional or conditional).

**Response `202 Accepted`:** `{ "eventId": "uuid", "status": "ACCEPTED", "receivedAt": "..." }` plus a `Location: /api/events/{eventId}` header.

| Status | When |
| --- | --- |
| 202 | Broker acknowledged the event |
| 400 | Malformed JSON, missing/invalid field, unknown `eventType`, bad UUID, metadata too large |
| 415 | Content-Type is not `application/json` |
| 422 | Per-type rule violated (e.g. `PRODUCT_VIEW` without `productId`) |
| 429 | **\[OPTIONAL\]** rate limit exceeded |
| 503 | Kafka unavailable or publish timed out (`Retry-After: 5`) |

### 14.2 `POST /api/events/batch` \[OPTIONAL\]

Body: `{ "events": [ ... ] }`, maximum 100. Returns `202` with `{ "accepted": n, "rejected": [ { "index": i, "errors": [...] } ] }`. Rejected items are not published; valid items are. Useful for the burst feature and for load-testing the producer.

### 14.3 `GET /api/events` \[MVP\]

**Purpose:** paginated event history.

**Query params:** `eventType`, `clientId`, `from`, `to` (ISO-8601), `page` (default 0), `size` (default 20, max 100), `sort` (default `occurredAt,desc`).

**Response `200`:** `{ "content": [ EventDto ], "page": 0, "size": 20, "totalElements": 1234, "totalPages": 62 }`.

**Statuses:** 200, 400 (bad filter/range, `from` after `to`, `size` above 100).

### 14.4 `GET /api/events/{eventId}` \[MVP\]

**Purpose:** event detail plus processing status.

**Response `200`:** full `EventDto` with `status`: `PROCESSED` (found in `events`) or `FAILED` (found in `failed_events`, includes `errorMessage`). **`404`** if neither exists yet.

**Frontend rule:** after submitting, poll this endpoint for up to about 10 s; `404` within that window is shown as `PENDING`; after it, as `NOT FOUND (possibly delayed)`. **`400`** on a non-UUID id.

### 14.5 `GET /api/events/failed` \[MVP\]

Paginated list from `failed_events` (`page`, `size`). Returns `eventId`, `errorClass`, `errorMessage`, `failedAt`, `payload`. Statuses: 200, 400.

### 14.6 Analytics endpoints \[MVP\]

| Endpoint | Params | Response (shape) |
| --- | --- | --- |
| `GET /api/analytics/summary` | none | `{ totalEvents, eventsLastMinute, eventsLast5Minutes, activeClients, failedEvents, avgProcessingLatencyMs }` |
| `GET /api/analytics/events-by-type` | `from`, `to` (default last 24 h) | `[ { eventType, count } ]` |
| `GET /api/analytics/events-over-time` | `from`, `to`, `interval` = minute or hour (default minute, last 30 min) | `[ { bucketStart, count } ]`, optionally `byType` |
| `GET /api/analytics/clients` | `limit` (default 10, max 100), `sort` = count or lastSeen | `[ { clientId, totalEvents, firstSeen, lastSeen } ]` |
| `GET /api/analytics/top-pages` | `limit` (default 5, max 50) | `[ { page, views } ]` |

All return `200` or `400` (invalid range/interval/limit). The maximum queryable range is 7 days to protect the database.

### 14.7 Operational endpoints

`GET /actuator/health` (with `/liveness` and `/readiness` groups), `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`. **\[OPTIONAL\]** `GET /swagger-ui.html` via springdoc.

**REST design notes:** events are created with `POST` on the collection and return `202` (not `201`) because creation is asynchronous; `Location` points at the future resource. Analytics are read-only `GET` resources under `/api/analytics`.

## 15. Analytics Requirements

**Principle:** reads must be cheap. Heavy counting happens **once, at consume time**, into aggregate tables; the API reads small pre-aggregated tables or indexed recent windows rather than scanning `events`.

| Metric | How computed | When | Source |
| --- | --- | --- | --- |
| Total events | `SUM(event_count)` over `event_agg_minute` | Aggregated at consume time, summed at read | `event_agg_minute` |
| Events by type | `GROUP BY event_type` over minute buckets in range | Same | `event_agg_minute` |
| Events over time / per minute | Buckets read directly; hourly view uses `date_trunc('hour', bucket_start)` | Same | `event_agg_minute` |
| Events by client | Pre-aggregated | Aggregated at consume time | `client_stats` |
| Top pages | Pre-aggregated | Aggregated at consume time | `page_stats` |
| Failed events | `COUNT(*)` | Read-time, small table | `failed_events` |
| Active clients (last 5 min) | `COUNT(DISTINCT client_id) WHERE received_at > now() - interval '5 min'` | **Synchronous query at read time** | `events` (indexed) |
| Recent events | `ORDER BY occurred_at DESC LIMIT 10` | **Synchronous query at read time** | `events` |
| Processing latency | `processed_at - received_at` (avg over last 5 min) in the API; p50/p95/p99 via Prometheus histogram | Read-time and metrics | `events`, Micrometer |

**Decisions**

- Aggregates are updated **inside the consumer's DB transaction** with the raw insert. This is "asynchronous" relative to the HTTP request and "synchronous" relative to event persistence, so the aggregate and raw table can never disagree.
- No scheduled batch jobs, caches or materialized views in V1.
- **Trade-off:** minute granularity limits time-series resolution; adequate for a dashboard. Late-arriving events (old `occurredAt`) are placed in their own historical bucket by `occurredAt`, which is correct and shows event-time semantics.
- Consistency check **\[OPTIONAL\]**: an integration test asserting `SUM(event_count) = COUNT(events)` after a load run.

## 16. Observability Requirements

### 16.1 Metrics to expose

**Custom business metrics (Micrometer; names shown in Prometheus form)**

| Metric | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `analytics_events_published_total` | Counter | `eventType` | Acked by broker |
| `analytics_events_publish_failed_total` | Counter | `reason` | Publish timeouts/errors |
| `analytics_events_consumed_total` | Counter | none | Records received by the listener |
| `analytics_events_processed_total` | Counter | `eventType` | Persisted successfully |
| `analytics_events_duplicates_total` | Counter | none | Skipped duplicates |
| `analytics_events_failed_total` | Counter | `reason` | Processing failures (`validation`, `db`, `unknown`) |
| `analytics_events_dlq_total` | Counter | none | Records sent to the DLQ |
| `analytics_event_processing_seconds` | Timer (histogram) | none | Consumer handling time per record |
| `analytics_event_end_to_end_seconds` | Timer (histogram) | none | `receivedAt` to persisted |

**Built-in metrics (auto-configured)**

- HTTP: `http_server_requests_seconds_count/sum/bucket` (rate, error rate and latency by URI/status).
- Kafka client metrics via Micrometer: producer (`kafka_producer_*` record send rate, errors) and consumer (`kafka_consumer_fetch_manager_records_lag_max`, `records_consumed_total`).
- JVM: heap, GC, threads. Datasource: `hikaricp_connections_active/pending`.

**Config:** expose `health,info,metrics,prometheus`; enable `management.metrics.distribution.percentiles-histogram.http.server.requests=true` and for the custom timers; tag all metrics with `application=analytics-manager`.

**Consumer lag.** The lag-max Kafka client metric is the V1 approach. **\[OPTIONAL\]** run `kafka-exporter` in the VM for exact per-partition `kafka_consumergroup_lag`.

**Cardinality rule:** tags must be low-cardinality (event type, reason). Never tag by `clientId` or `eventId`.

### 16.2 Prometheus configuration

```
global:
  scrape_interval: 5s
scrape_configs:
  - job_name: analytics-manager
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['192.168.56.1:8080']
```

**Decision:** 5 s interval for responsive demos (15 s is the default in real systems).

### 16.3 Grafana dashboard: "Analytics Manager — Pipeline"

Provision it as JSON (committed to the repo) so it is reproducible.

| Row | Panels | Example PromQL |
| --- | --- | --- |
| **Overview** | App up, Total processed (stat), Failed/DLQ (stat), Consumer lag (gauge), Events/s now | `up{job="analytics-manager"}`; `sum(analytics_events_processed_total)`; `max(kafka_consumer_fetch_manager_records_lag_max)` |
| **Pipeline throughput** | Published/s vs Processed/s (overlay, the hero chart); processed by type (stacked) | `sum(rate(analytics_events_published_total[1m]))`; `sum by (eventType)(rate(analytics_events_processed_total[1m]))` |
| **Latency** | End-to-end p50/p95/p99; processing p95 | `histogram_quantile(0.95, sum by (le)(rate(analytics_event_end_to_end_seconds_bucket[1m])))` |
| **Consumer health** | Lag over time; duplicates/s; DLQ/s | `max(kafka_consumer_fetch_manager_records_lag_max)`; `rate(analytics_events_dlq_total[1m])` |
| **HTTP** | Request rate by status; p95 latency by URI; 4xx/5xx rate | `sum by (status)(rate(http_server_requests_seconds_count[1m]))` |
| **Failures** | Publish failures; processing failures by reason | `sum by (reason)(rate(analytics_events_failed_total[1m]))` |
| **JVM / DB** | Heap used, GC pause, threads, Hikari active/pending | `hikaricp_connections_active` |

**\[FUTURE\]** Alert rules (consumer lag above 1000 for 2 min, 5xx rate above 5%, DLQ increase) with Grafana alerting.

**Demo linkage:** the "Published/s vs Processed/s" panel plus the lag gauge are the proof that the pipeline is asynchronous: during a burst, lag rises, then drains to zero as processed catches up.

## 17. Reliability & Failure Handling

### 17.1 Failure scenarios

**1. Kafka is unavailable**

- *Producer:* `max.block.ms=2000` and `delivery.timeout.ms=10000` bound the wait. The API returns `503` with `Retry-After`; `analytics_events_publish_failed_total` increments; the frontend shows an error and lets the user retry. The failed request is **not** buffered locally in V1.
- *Consumer:* the listener container logs and automatically reconnects; no events are lost because Kafka holds the data. It resumes from the committed offset.
- *Health:* a custom `KafkaHealthIndicator` (AdminClient `describeCluster` with a short timeout) reports DOWN in readiness.
- **Trade-off:** no transactional outbox in V1, so events rejected during an outage depend on the client retrying. **\[FUTURE\]** outbox pattern.

**2. Consumer processing fails**

- *Permanent errors* (invalid payload, unknown event type/version, bad field values): not retried, published to the DLQ immediately, offset committed.
- *Transient errors* (timeouts, optimistic failures): retried with exponential backoff (1 s, 2 s, 4 s), then DLQ.
- The DLQ listener stores the record in `failed_events`; `GET /api/events/failed` and the failed-events dashboard card expose it.
- Poison pills are handled by `ErrorHandlingDeserializer`, so the consumer never loops on a bad record.

**3. Database is unavailable**

- Connection-level exceptions (`CannotCreateTransactionException`, `DataAccessResourceFailureException`) are classified as **infrastructure failures**, not event failures. They must **not** go to the DLQ, because the DLQ listener also needs the database.
- **Decision:** use `DefaultErrorHandler.setBackOffFunction` to apply a capped exponential backoff (max 30 s, effectively unlimited attempts) for those exceptions. The partition is held, Kafka buffers new events, consumer lag grows (visible in Grafana), and processing resumes when the database returns. Keep each backoff step below `max.poll.interval.ms` (set to 5 min) to avoid rebalances. **Assumption:** verify this behavior with a test that stops PostgreSQL mid-run.
- *API read endpoints* return `503` through the global handler while the database is down; `POST /api/events` **continues to work** because ingestion does not touch the database. This is a key advantage to highlight.
- Health: the DB health indicator reports DOWN.

**4. Duplicate events are received**

- Same `eventId` submitted twice via REST: both are published (the producer does not check the DB), the consumer inserts the first and skips the second via `ON CONFLICT DO NOTHING`; aggregates are unchanged; `analytics_events_duplicates_total` increments.
- Kafka redelivery after a crash: same mechanism.

**5. Invalid events are submitted**

- Rejected at the API with `400`/`422` and a field-level error body; never reach Kafka.
- Events that bypass the API (or arrive malformed) are rejected at the consumer and routed to the DLQ.

### 17.2 Transaction boundaries

- One `@Transactional` method per event covers `events` insert plus aggregate upserts. All succeed or none.
- Offset acknowledgement happens **after** commit and outside the transaction.
- Producer sends are never inside a DB transaction (avoids the dual-write trap).
- Isolation: default READ COMMITTED; upserts are atomic row operations, so concurrent consumer threads are safe.
- Aggregate hot rows (same minute and type) are contended by 3 threads; acceptable at demo scale. **Trade-off:** row-lock contention caps aggregate throughput; batching is a FUTURE fix.

### 17.3 Health checks, logging, shutdown

- **Health groups:** liveness (app alive); readiness (DB + Kafka). Custom indicators described above.
- **Logging:** SLF4J/Logback; MDC fields `eventId`, `clientId`, `partition`, `offset`; INFO for lifecycle and summaries, WARN for retries/duplicates, ERROR for DLQ routing with stack trace. Never log full payloads at INFO. **\[OPTIONAL\]** JSON log format.
- **Graceful shutdown:** `server.shutdown=graceful`; the Kafka listener container finishes the in-flight record and commits before stopping; the producer is flushed on close.

## 18. Security

Basic, appropriate-for-portfolio controls. Everything beyond this list is out of scope.

| Control | V1 decision | Tag |
| --- | --- | --- |
| CORS | Allow only the configured frontend origin (`http://localhost:5173` in dev), methods `GET, POST, OPTIONS`, `Content-Type` header; origin set through a property | MVP |
| Input validation | Bean Validation on DTOs, per-type validators, enum/UUID parsing, field length limits | MVP |
| Request size | Reject bodies above 16 KB; `metadata` capped at 2 KB and 20 keys | MVP |
| Sanitization | Treat all strings as data: parameterized JPA queries only (no string-built SQL); strip control characters; React escapes output by default and `dangerouslySetInnerHTML` is forbidden | MVP |
| Actuator exposure | Expose only `health`, `info`, `metrics`, `prometheus`; document that real deployments must protect them | MVP |
| Secrets | DB password and hosts from environment variables; no secrets committed; `.env.example` provided | MVP |
| Rate limiting | Per-IP token bucket (e.g. Bucket4j), configurable, **disabled in the dev profile** because the burst demo needs high rates; enabled-by-profile demo returns `429` | OPTIONAL |
| Authentication | API key header (`X-API-Key`) for ingestion, or JWT with Spring Security | FUTURE |
| Kafka security | PLAINTEXT on the host-only network; TLS/SASL documented as the production path | FUTURE |

**Decision:** authentication is not in V1. It adds a login flow and token handling without improving the event-driven architecture, which is the project's purpose. State this clearly in the README so it reads as a deliberate scoping choice.

## 19. Non-Functional Requirements

*All numbers are **targets for a local development/demo environment, not guarantees**. Assumed hardware: a modern laptop, a 2-vCPU/4 GB Ubuntu VM.*

| Category | Requirement / target |
| --- | --- |
| **Performance** | `POST /api/events` p95 under 100 ms. Sustained ingestion of at least 200 events/s for 60 s on the demo hardware. End-to-end (`receivedAt` to persisted) p95 under 2 s under normal load. Analytics `GET` endpoints p95 under 300 ms with 100k stored events. |
| **Reliability** | Every `202` response means the broker acknowledged the event. After a clean broker restart, no acknowledged event is lost. Zero double-counted events in duplicate/redelivery tests. |
| **Fault tolerance** | Consumer recovers automatically within 60 s of Kafka or PostgreSQL returning. Ingestion API survives a database outage. A single poison message never halts the consumer. |
| **Scalability** | Design allows horizontal consumer scaling up to the partition count (3). Verified manually with 2 instances in the optional demo; no claim beyond that. |
| **Maintainability** | One-command build and run; layered package structure; at least 70% line coverage on service/consumer code (target); Flyway migrations; README with setup and troubleshooting. |
| **Observability** | All metrics in Section 16 visible in Grafana within 10 s of activity; every request and event traceable by `eventId` in logs. |
| **Security** | Section 18 controls implemented; no secrets in the repo. |
| **Extensibility** | Adding a new event type requires changing at most three places: the enum, one validator class and (optionally) frontend form config; no schema migration or Kafka change. |
| **Portability** | Runs from either host mode (current) or Docker Compose (Phase 9) through configuration only. |

## 20. Testing Strategy

### 20.1 Tools

| Need | Tool |
| --- | --- |
| Unit tests | JUnit 5, Mockito, AssertJ |
| Controller tests | `@WebMvcTest` + MockMvc |
| Repository/DB tests | `@DataJpaTest` with **Testcontainers PostgreSQL** (not H2, so SQL such as `ON CONFLICT` and JSONB behaves as in production) |
| Kafka tests | **Testcontainers Kafka** for realistic integration; `spring-kafka-test` `@EmbeddedKafka` for fast tests |
| Async assertions | Awaitility |
| Full-stack integration | `@SpringBootTest` + Testcontainers (Kafka + PostgreSQL) + `TestRestTemplate`/MockMvc |
| API testing | MockMvc in code; a Postman/`.http` collection for manual testing |
| Frontend | Vitest + React Testing Library |
| Load | k6 or a simple script posting events **\[OPTIONAL\]** |

### 20.2 What to test

| Layer | Test | V1 priority |
| --- | --- | --- |
| Service | Enrichment (eventId/timestamps), per-type validation rules, duplicate result handling | **Essential** |
| Controller | 202 on valid, 400/422 on invalid, 415, error body shape, 503 when producer fails (mocked) | **Essential** |
| Producer | Sends with `key=clientId`; failure becomes 503 and increments the failure metric | **Essential** |
| Consumer | Valid event persists; aggregates update; duplicate does not change aggregates; invalid payload goes to the DLQ | **Essential** |
| Repository | Unique constraint, upsert counters, history filters and pagination | **Essential** |
| End-to-end | POST an event, then await that it appears in DB and analytics (Testcontainers) | **Essential** (one happy-path test) |
| Retry/DLQ | Forced exception leads to N retries, then DLQ record, then `failed_events` row | **Essential** (completes Phase 8) |
| Metrics | Counters increment after processing (`MeterRegistry` assertions) | Recommended |
| Concurrency | Many threads inserting the same `eventId` yield exactly one row | Recommended |
| DB-down behavior | Stop the container mid-test and verify consumer pause and recovery | Optional |
| Frontend | Form validation, API error rendering | Optional |
| Performance | Load test run and results recorded in the README | Optional |

**Rule:** every bug found while building becomes a regression test.
