# Analytics Manager — Event-Driven Analytics Platform

[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft%20Mode-231F20.svg?logo=apachekafka)](https://kafka.apache.org/)
[![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4-4479A1.svg?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![React 18](https://img.shields.io/badge/React-18.3-61DAFB.svg?logo=react&logoColor=black)](https://react.dev/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Scrape%205s-E6522C.svg?logo=prometheus&logoColor=white)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-22%20Panels-F46800.svg?logo=grafana&logoColor=white)](https://grafana.com/)

**Analytics Manager** is a production-grade, event-driven analytics platform built with **Spring Boot 3**, **Apache Kafka (KRaft mode)**, **MySQL 8.4**, and **React 18**. 

It demonstrates clean distributed systems patterns: **Zero Dual-Write**, **At-Least-Once Delivery with Idempotent Consumer**, **Dead-Letter Queue (DLQ) Quarantine**, and **Full Observability** via custom Actuator metrics, Prometheus, and Grafana.

---

## Architecture Overview

```
                                      +------------------------------------+
                                      |       React 18 Frontend UI         |
                                      |  - Event Simulator (/simulator)    |
                                      |  - Real-time Analytics (/dashboard)|
                                      |  - Paginated Audit Log (/events)   |
                                      +-----------------+------------------+
                                                        |
                                                  (HTTP POST /api)
                                                        |
                                                        v
                                      +------------------------------------+
                                      |     Spring Boot 3 REST Producer    |
                                      |  - Structural & per-type validation|
                                      |  - Zero Dual-Write (No DB touches) |
                                      |  - acks=all, enable.idempotence    |
                                      |  - Partition key = clientId        |
                                      +-----------------+------------------+
                                                        |
                                                (Kafka Producer API)
                                                        |
                                                        v
+-------------------------------------------------------------------------------------------------------------+
|                                              Apache Kafka (KRaft)                                           |
|                                                                                                             |
|      Topic: analytics-events (3 Partitions)                  Topic: analytics-events-dlq (1 Partition)      |
|      [ Partition 0 ]  [ Partition 1 ]  [ Partition 2 ]       [ Poison Pill & Permanent Failures ]           |
+------------------------------+----------------------------------------------+-------------------------------+
                               |                                              |
                        (3 Consumer Threads)                           (Dedicated Group)
                               v                                              v
+---------------------------------------------------------------+    +----------------------------------------+
|             Spring Kafka Consumer Component                   |    |           DLQ Consumer Worker          |
|  - Manual immediate offset commit (outside DB tx)             |    |  - Catches unparseable poison pills    |
|  - Atomic INSERT IGNORE on unique(event_id)                   |    |  - Records error stack & original metadata|
|  - Affected rows = 1: Updates pre-aggregates                  |    |  - Inserts into failed_events table    |
|  - Affected rows = 0: Duplicate skipped, 0 double-counts      |    +-------------------+--------------------+
+------------------------------+--------------------------------+                        |
                               |                                                         |
                               +----------------------------+----------------------------+
                                                            |
                                                   (JDBC / HikariCP)
                                                            |
                                                            v
                                      +------------------------------------+
                                      |         MySQL 8.4 Database         |
                                      |  - events (raw immutable logs)     |
                                      |  - event_agg_minute (rollups)      |
                                      |  - client_stats & page_stats       |
                                      |  - failed_events (DLQ records)     |
                                      +-----------------+------------------+
                                                        |
                                                        | /actuator/prometheus
                                                        v
                                      +------------------------------------+
                                      |             Prometheus             |
                                      |  - Scrapes metrics every 5 seconds |
                                      +-----------------+------------------+
                                                        |
                                                        | Datasource proxy
                                                        v
                                      +------------------------------------+
                                      |        Grafana 11 Dashboard        |
                                      |  - Hero: Published/s vs Processed/s|
                                      |  - Max Consumer Lag Gauge          |
                                      |  - Duplicate & DLQ Rates           |
                                      +------------------------------------+
```

---

## Key Engineering Decisions & Trade-Offs

| Decision | Choice | Reason & Trade-Off |
| :--- | :--- | :--- |
| **Architecture** | Modular Monolith | Keeps producer and consumer in distinct packages (`ingestion`, `consumer`, `analytics`, `common`) within one deployable JVM. Prevents premature microservice operational overhead while maintaining strict logical decoupling. |
| **Dual-Write Prevention** | Producer writes **only** to Kafka | If the REST API wrote to both MySQL and Kafka, a failure on either side creates inconsistent state. Ingestion acknowledges HTTP `202 Accepted` strictly after broker ack. |
| **Deduplication** | Database-level unique constraint (`event_id`) | In-memory sets (e.g. Redis bloom filters) can lose state or evict keys. MySQL unique index + atomic `INSERT IGNORE` guarantees idempotency across consumer crashes and redeliveries. |
| **Partition Key** | `clientId` | Ensures all events for a given client land on the same partition, guaranteeing strict chronological ordering per tenant. |
| **Failure Recovery** | Exponential backoff $\rightarrow$ DLQ | Transient errors retry at 1s, 2s, 4s. Poison pills bypass retries straight to `analytics-events-dlq` and `failed_events` table without halting consumer partitions. |
| **Database Down** | Partition pause with capped backoff | Database outages are infrastructure failures, not payload failures. Events stay in Kafka; consumer lag rises safely; zero events are dropped or routed to DLQ. |

---

## Project Structure

```text
Analytics_Manager/
├── backend/                               # Spring Boot 3 Modular Monolith
│   ├── src/main/java/com/analytics/manager/
│   │   ├── ingestion/                     # REST Producer (Controllers, Validators, KafkaProducerService)
│   │   ├── consumer/                      # Kafka Listener (Idempotent processing, DLQ Consumer, Config)
│   │   ├── analytics/                     # Analytics APIs & Event History Query Services
│   │   └── common/                        # Entities, Repositories, Health Indicators, DTOs
│   ├── src/main/resources/
│   │   ├── application.yml                # Configuration & Actuator health group bindings
│   │   └── db/migration/V1__init.sql      # Flyway schema migration script
│   └── pom.xml
├── frontend/                              # React 18 + Vite + Tailwind CSS SPA
│   ├── src/
│   │   ├── pages/                         # SimulatorPage, DashboardPage, EventHistoryPage
│   │   ├── components/                    # Navbar, Toast notifications, KPI cards
│   │   ├── api/client.js                  # Axios client with RFC 7807 problem detail handling
│   │   └── App.jsx
│   └── package.json
└── monitoring/                            # Observability Stack
    ├── prometheus/prometheus.yml          # Scrape configuration (5s interval)
    ├── grafana/
    │   ├── dashboards/analytics-pipeline.json  # 22-panel production dashboard
    │   └── provisioning/                  # Auto-loaded datasources and dashboards
    └── docker-compose.yml                 # One-command Prometheus + Grafana runner
```

---

## Quickstart Guide

### Prerequisites
- **Java 17+**
- **Node.js 18+** & `npm`
- **Docker Desktop** (for Prometheus & Grafana)
- **MySQL 8.4** & **Apache Kafka** (running locally or in a VirtualBox VM with port forwards `3307` and `9092`)

---

### 1. Start Observability (Prometheus & Grafana)

Run the pre-configured monitoring containers from the project root:

```powershell
docker compose -f monitoring/docker-compose.yml up -d
```

- **Grafana UI:** [http://localhost:3000](http://localhost:3000) (Credentials: `admin` / `admin`)
- **Prometheus UI:** [http://localhost:9090](http://localhost:9090)
- The **"Analytics Manager — Pipeline"** dashboard is pre-loaded automatically.

---

### 2. Start the Spring Boot Backend

Set your database password and launch the application:

```powershell
cd backend
$env:DB_PASSWORD="your_mysql_password"
./mvnw.cmd spring-boot:run
```

- **API Base:** [http://localhost:8080](http://localhost:8080)
- **Liveness Probe:** [http://localhost:8080/actuator/health/liveness](http://localhost:8080/actuator/health/liveness)
- **Readiness Probe:** [http://localhost:8080/actuator/health/readiness](http://localhost:8080/actuator/health/readiness) (Checks both MySQL & Kafka cluster status)
- **Prometheus Metrics:** [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

---

### 3. Start the React Frontend

In a new terminal window:

```powershell
cd frontend
npm install
npm run dev
```

Open **[http://localhost:5173](http://localhost:5173)** in your browser.

---

## 5-Minute Scripted Demo (Interview Walkthrough)

### Scenario 1: High-Throughput Burst & Asynchronous Decoupling
1. In the **Simulator** (`#/simulator`), set the **Burst Load Generator** to `100 events` with `0 ms` delay.
2. Click **"Launch 100 Event Burst"**.
3. **Observation:**
   - The UI shows 100 events accepted in milliseconds via `HTTP 202 Accepted`.
   - In Grafana ([http://localhost:3000](http://localhost:3000)), open the **Hero Panel** (*Published/s vs Processed/s*). Watch the blue spike (Kafka Producer) occur instantly, while the green curve (Consumer + MySQL) drains smoothly without dropping records.
   - The **Max Consumer Lag Gauge** rises and drains back to zero.

### Scenario 2: Exact-Once Results with Idempotent Delivery
1. On the **Simulator** page, click **"Resend Last Event (Duplicate Demo)"**.
2. **Observation:**
   - The same `eventId` is published to Kafka.
   - Spring Boot logs show:
     ```text
     WARN ... Duplicate event skipped: eventId=c7a3... clientId=client-web-01
     ```
   - On the **Dashboard** (`#/dashboard`), verify that the **Total Processed Events** and pre-aggregated KPIs remain identical (zero double-counting).

### Scenario 3: Dead-Letter Queue (DLQ) & Poison Pill Isolation
1. In the **Simulator**, click **"Send Invalid Payload"**.
2. **Observation:**
   - The REST layer validates the event structure and rejects it immediately with an RFC 7807 `400 Bad Request` ProblemDetail.
   - When a consumer encounters a malformed payload or unparseable JSON, Spring Kafka routes the record to `analytics-events-dlq`.
   - The **DLQ Consumer** stores it in `failed_events`. The **Dead-Letter Queue** counter on the dashboard increments, proving zero consumer partition stalls.

---

## API Specification

### Ingestion API
- `POST /api/events` — Publishes single event to Kafka. Returns `202 Accepted` with `Location: /api/events/{eventId}`.
- `POST /api/events/batch` — Bulk ingestion for up to 100 events per request.

### Analytics & Queries
- `GET /api/analytics/summary` — High-level platform KPIs (`totalEvents`, `eventsLastMinute`, `activeClientsLast5Min`, `failedEventsCount`).
- `GET /api/analytics/events-by-type` — Event breakdown across domains (`PAGE_VIEW`, `LOGIN`, `PURCHASE`, etc.).
- `GET /api/analytics/events-over-time` — Pre-aggregated minute rollups for the last 30 minutes.
- `GET /api/analytics/top-pages` — Top visited paths from `page_stats`.
- `GET /api/events` — Paginated, filtered event audit logs (supports `eventType`, `clientId`, `from`, `to`).
- `GET /api/events/{eventId}` — Query individual event lifecycle state (`PROCESSED`, `FAILED`, `PENDING`).

---

## Automated Test Suite

Run all unit, repository, and integration tests:

```powershell
cd backend
./mvnw.cmd test
```

| Test Class | Scope | Scenarios Covered |
| :--- | :--- | :--- |
| `EventValidationServiceTest` | Unit | Structural UUID format, mandatory fields, conditional per-type business rules. |
| `EventIngestionControllerTest` | WebMvc | 202 Accepted on valid, 400/422 RFC 7807 on invalid, 503 fallback on broker timeout. |
| `KafkaProducerConfigTest` | Integration | Validates producer timeouts: $linger + request \le delivery$ invariant. |
| `AnalyticsEventsConsumerTest` | Unit | Consumer delegation, MDC context attachment, manual immediate offset commit. |
| `EventProcessingServiceTest` | Unit | Atomic `INSERT IGNORE`, duplicate detection, pre-aggregate rollups. |
| `DlqConsumerTest` | Unit | Deserialization failure recovery, isolation in `failed_events`, DLQ metrics. |
| `AnalyticsServiceTest` | Unit | Summary KPIs, rollups, and active client calculation. |
| `EventHistoryServiceTest` | Unit | Multi-parameter filtering, sorting, and pagination bounds. |
| `KafkaHealthIndicatorTest` | Unit | Broker reachability check via Kafka AdminClient (cluster ID, nodes, controller). |
| `AnalyticsManagerApplicationTests` | Integration | Full Spring Boot context bootstrap with Flyway schema migration against MySQL. |

---

## Custom Micrometer Metrics

Exposed under `/actuator/prometheus` and visualized in Grafana:

- `analytics_events_published_total{eventType}` — Broker-acknowledged events.
- `analytics_events_publish_failed_total{reason}` — Producer timeouts or errors.
- `analytics_events_consumed_total` — Records received by listener threads.
- `analytics_events_processed_total{eventType}` — Persisted and aggregated events.
- `analytics_events_duplicates_total` — Skipped duplicate events.
- `analytics_events_failed_total{reason}` — Processing errors (`validation`, `db`, `unknown`).
- `analytics_events_dlq_total` — Poison pills and non-retryable records routed to DLQ.
- `analytics_event_processing_seconds` — Consumer handling duration per record (histogram).
- `analytics_event_end_to_end_seconds` — Latency from API `receivedAt` to DB commit (histogram).
