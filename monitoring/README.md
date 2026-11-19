# Observability Stack (Prometheus & Grafana)

This directory contains the complete observability setup for the **Analytics Manager** event pipeline, fulfilling all requirements from Section 16 of the PRD.

---

## Architecture Overview

```
                                      +---------------------------------------------+
                                      |            Spring Boot Application          |
                                      |  - Custom business metrics (Micrometer)     |
                                      |  - Kafka & HikariCP metrics                 |
                                      |  - /actuator/prometheus                     |
                                      +----------------------+----------------------+
                                                             ^
                                                             | Scrapes every 5s
                                      +----------------------+----------------------+
                                      |                  Prometheus                 |
                                      |  - Retention & real-time metric evaluation   |
                                      +----------------------+----------------------+
                                                             ^
                                                             | Datasource
                                      +----------------------+----------------------+
                                      |                   Grafana                   |
                                      |  - Dashboard: Analytics Manager — Pipeline  |
                                      +---------------------------------------------+
```

---

## Option 1: Quickstart with Docker Compose

If you have Docker running on your host machine:

```powershell
# From the project root
docker compose -f monitoring/docker-compose.yml up -d
```

- **Prometheus UI:** [http://localhost:9090](http://localhost:9090)
- **Grafana UI:** [http://localhost:3000](http://localhost:3000) (User: `admin`, Password: `admin`)
- The **"Analytics Manager — Pipeline"** dashboard is automatically provisioned and ready on load.

---

## Option 2: VirtualBox Ubuntu VM

If Prometheus and Grafana are installed directly inside your VirtualBox Ubuntu VM:

1. **Prometheus Configuration:**
   Copy `monitoring/prometheus/prometheus.yml` to your VM's Prometheus config path (e.g. `/etc/prometheus/prometheus.yml`).
   Inside the VM, the host machine is accessible via the NAT gateway `10.0.2.2:8080`.
   ```bash
   sudo systemctl restart prometheus
   ```

2. **Grafana Dashboard:**
   - Open Grafana in your browser (`http://localhost:3000` with port forwarding or `http://<VM_IP>:3000`).
   - Navigate to **Dashboards** $\rightarrow$ **New** $\rightarrow$ **Import**.
   - Upload or paste `monitoring/grafana/dashboards/analytics-pipeline.json`.

---

## Grafana Dashboard Panels

The provisioned dashboard (`Analytics Manager — Pipeline`) features 22 real-time panels organized into 7 rows:

1. **System Overview:**
   - App Health (`up`)
   - Total Published (Acked by broker)
   - Total Processed (Persisted to database)
   - Duplicates Skipped (`INSERT IGNORE`)
   - DLQ / Failed Events
   - Max Consumer Lag Gauge
2. **Pipeline Throughput (Hero Row):**
   - **Hero Chart:** Published Rate vs Processed Rate (visualizes asynchronous buffering and draining)
   - Processed Events by Type (stacked timeseries)
3. **Latency Profiles:**
   - End-to-End Latency: p50, p95, p99 quantiles (`receivedAt` to DB persisted)
   - Consumer Processing Time: p50, p95 quantiles per record
4. **Consumer Health & Idempotency:**
   - Consumer Lag Over Time
   - Duplicate Events Rate
   - Dead-Letter Queue (DLQ) Routing Rate
5. **HTTP Ingestion API & Actuator:**
   - Request Rate by Status (202, 400, 422, 503)
   - p95 Latency by URI
   - 4xx & 5xx Error Rates
6. **Failures & Error Handling:**
   - Producer Publish Failures by Reason (`timeout`, `broker_error`)
   - Consumer Processing Failures by Reason (`validation`, `db`, `unknown`)
7. **JVM & Database Infrastructure:**
   - JVM Heap Used vs Max
   - HikariCP Database Connections (Active, Idle, Pending)
   - JVM Threads (Live, Peak)
