# Analytics Manager — Frontend

Modern, high-performance React application built for the **Analytics Manager** event-driven analytics platform.

---

## Tech Stack

- **Framework:** React 18
- **Build Tool:** Vite
- **Styling:** Tailwind CSS (Dark theme with custom glassmorphism)
- **Routing:** React Router v6 (`HashRouter` for zero-configuration client-side routing)
- **HTTP Client:** Axios (configured with API error normalization & RFC 7807 problem details)
- **Charts:** Recharts (responsive time series area charts and bar charts)
- **Icons:** Lucide React

---

## Pages & Capabilities

### 1. 🚀 Event Simulator (`#/simulator`)
- **Single Event Generator:** Configure and submit events (`PAGE_VIEW`, `LOGIN`, `PRODUCT_VIEW`, `SEARCH`, `PURCHASE`, `ERROR`).
- **Dynamic Context Fields:** Automatically shows relevant inputs (`page` for page views, `productId` for product views/purchases).
- **Client Randomizer:** Generates distinct client IDs for partition hashing.
- **Burst Load Generator (Hero Demo):**
  - Configurable burst size (10 to 500 events).
  - Configurable delay (0ms to 100ms) to create backpressure and test Kafka consumer lag drain.
  - Live throughput counters (`Sent`, `202 Accepted`, `4xx Rejected`, `Latency`).
- **Idempotency Demo (Duplicate Resend):** Resends the exact same `eventId` to verify database `INSERT IGNORE` and zero double-counting.
- **Poison Pill / Validation Demo:** Submits malformed payloads to verify client/server validation rejection.

### 2. 📊 Analytics Dashboard (`#/dashboard`)
- **KPI Summary Cards:**
  - Total Processed Events (Idempotent total from `events`)
  - Velocity (Events in last minute from `event_agg_minute`)
  - Active Clients (Clients active in last 5 minutes from `client_stats`)
  - Dead-Letter Queue (Failed events count from `failed_events`)
- **Real-time Charts:**
  - **Throughput Over Time:** Minute-by-minute area chart of ingested events over the last 30 minutes.
  - **Events by Type:** Color-coded distribution bar chart.
- **Data Tables:**
  - Top 5 Visited Pages (from `page_stats`).
  - Recent 10 Processed Events with status pills.
- **Controls:** Auto-refresh toggle (default 5s) and manual refresh button.

### 3. 📜 Event History (`#/events`)
- Paginated table of all persisted events with server-side pagination and sorting.
- Multi-field filters: Event Type, Client ID, and Date Range (`from` / `to`).
- **Event Detail Modal:** Click any event row to view:
  - Raw JSON envelope.
  - Metadata breakdown.
  - Kafka partition & offset assignments.
  - End-to-end processing latency calculation (`processedAt - receivedAt`).

### 4. 🧭 Top Navigation & Live Health
- Dynamic Actuator health indicator polling `/actuator/health` every 10s.
- Direct links to Grafana (`:3000`) and Prometheus (`:9090`).

---

## Getting Started

### Prerequisites
- Node.js 18+ / 20+ / 22+
- Running backend on `http://localhost:8080` (or configured via `VITE_API_BASE_URL`)

### Development Server
```bash
# In the frontend directory
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173) in your browser.

### Production Build
```bash
npm run build
```
The optimized static bundle will be generated in `dist/`.
