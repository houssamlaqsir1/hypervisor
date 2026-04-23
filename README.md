# Hypervisor — Railway Alarm & Centralization System

End-of-studies (PFE) project: a centralized event-processing system for railway
infrastructure. It ingests events from an **AI Camera System** and a **3D SIG
System**, correlates them in real time, and dispatches safety alerts to an
external **Alert Radio**. Operators monitor everything from a React dashboard.

## Stack

| Layer     | Tech                                                    |
|-----------|---------------------------------------------------------|
| Backend   | Spring Boot 4 / Java 21, Spring Data JPA, Spring Security, Spring WebSocket (STOMP), Lombok |
| Database  | PostgreSQL (Supabase cloud)                             |
| Frontend  | React 19 + TypeScript + Vite, React Router, Leaflet, STOMP/SockJS |
| Deploy    | Docker + docker-compose (provided)                      |

## Architecture

```
 AI Camera  ─POST /api/camera-events─┐
                                     ▼
 3D SIG     ─POST /api/sig-events──▶ Hypervisor (Spring Boot)
                                     │
                                     ├── CorrelationEngine (rule chain)
                                     │      ├─ IntrusionInRestrictedZoneRule
                                     │      ├─ ObjectOnTrackRule
                                     │      ├─ EscalationRule
                                     │      └─ LowConfidenceAnomalyRule
                                     │
                                     ├── AlertService ──▶ PostgreSQL
                                     │        │
                                     │        ├─▶ AlertRadioClient ──▶ Alert Radio (mock)
                                     │        └─▶ AlertBroadcaster (WS /topic/alerts)
                                     │
 React HMI (REST + WebSocket) ◀──────┘
```

### Backend package layout

```
com.oncf.hypervisor
├── config/         SecurityConfig, WebSocketConfig, CorsProperties, AlertRadioProperties, CorrelationProperties
├── domain/         CameraEvent, SigEvent, Alert, Zone + enums
├── repository/     JPA repositories
├── dto/            Request / Response records
├── mapper/         HypervisorMapper (entity ↔ dto)
├── service/
│   ├── CameraEventService, SigEventService, AlertService, ZoneService, SimulationService
│   ├── correlation/   CorrelationEngine, CorrelationRule, rules/*
│   └── external/      AlertRadioClient
├── websocket/      AlertBroadcaster
├── controller/     REST + simulation + mock-radio
├── exception/      GlobalExceptionHandler, ApiError, NotFoundException
└── bootstrap/      SeedDataLoader (inserts default zones)
```

### Frontend layout

```
frontend/src
├── api/          client, alerts, zones, simulation
├── hooks/        useLiveAlerts (STOMP)
├── types/        api.ts
├── components/   Sidebar, AlertRow
└── pages/        DashboardPage, MapPage, SimulatorPage, HistoryPage
```

## Database schema (auto-managed via `ddl-auto: update`)

- **zones**: id, name, type (RESTRICTED/TRACK/STATION/NORMAL), description, center_lat, center_lon, radius_m
- **camera_events**: id, camera_id, event_type, label, confidence, latitude, longitude, raw_payload, occurred_at, received_at
- **sig_events**: id, source_id, latitude, longitude, zone_id (FK), metadata, occurred_at, received_at
- **alerts**: id, severity, type, message, latitude, longitude, zone_id (FK), camera_event_id (FK), sig_event_id (FK), created_at, dispatched, dispatched_at

## API contract

| Method | Path                                   | Body                       | Notes                                          |
|--------|----------------------------------------|----------------------------|------------------------------------------------|
| POST   | `/api/camera-events`                   | `CameraEventRequest`       | Runs correlation, returns event + generated alerts |
| POST   | `/api/sig-events`                      | `SigEventRequest`          | Runs correlation, returns event + generated alerts |
| GET    | `/api/alerts?severity&since&limit`     | —                          | Filter/paginate alerts                         |
| GET    | `/api/alerts/{id}`                     | —                          |                                                |
| GET    | `/api/alerts/stats`                    | —                          | Totals + counts per severity                   |
| GET    | `/api/zones`                           | —                          |                                                |
| POST   | `/api/simulation/camera`               | `SimulationRequest?`       | Generates a random camera event                |
| POST   | `/api/simulation/sig`                  | `SimulationRequest?`       | Generates a random SIG event                   |
| POST   | `/api/simulation/scenario/intrusion`   | `{ zoneId }`               | Scripted multi-event intrusion scenario        |
| POST   | `/api/alert-radio/receive`             | `AlertDto`                 | Mock external Alert Radio receiver             |
| WS     | `/ws`  subscribe `/topic/alerts`       | —                          | Live alert push (STOMP over SockJS)            |

### Example payload — `POST /api/camera-events`

```json
{
  "cameraId": "CAM-101",
  "eventType": "INTRUSION",
  "label": "person",
  "confidence": 0.92,
  "latitude": 33.5905,
  "longitude": -7.6023,
  "occurredAt": "2026-04-21T14:32:00Z",
  "rawPayload": { "track": 7, "snapshotUrl": "..." }
}
```

### Example response

```json
{
  "event": { "id": 42, "cameraId": "CAM-101", "eventType": "INTRUSION", "confidence": 0.92, "latitude": 33.5905, "longitude": -7.6023, "...": "..." },
  "alerts": [
    {
      "id": 77,
      "severity": "HIGH",
      "type": "INTRUSION",
      "message": "Intrusion detected in restricted zone 'Technical Depot' (camera CAM-101, confidence 0.92)",
      "zoneId": 3,
      "zoneName": "Technical Depot",
      "createdAt": "2026-04-21T14:32:00.512Z",
      "dispatched": true
    }
  ]
}
```

## Correlation rules (the "brain")

| Rule                             | Trigger                                                                       | Severity |
|----------------------------------|-------------------------------------------------------------------------------|----------|
| IntrusionInRestrictedZoneRule    | Camera type ∈ {HUMAN_DETECTED, INTRUSION} AND confidence ≥ 0.7 AND inside RESTRICTED zone | HIGH     |
| ObjectOnTrackRule                | Camera type = OBJECT_DETECTED inside TRACK zone                               | CRITICAL |
| EscalationRule                   | ≥ 3 camera events in same zone within 5 minutes                               | CRITICAL |
| LowConfidenceAnomalyRule         | Confidence < 0.5 OR type = ANOMALY, outside any zone                          | LOW      |

Thresholds are configurable in `application.yaml` under `hypervisor.correlation.*`.
Adding a new rule = one class implementing `CorrelationRule`, annotated with
`@Component @Order(n)`. The engine picks it up automatically.

## Running it

### 1. Backend

```bash
cd backend
./mvnw spring-boot:run
```

Requires the environment variables in `application.yaml` or a reachable Supabase.
Default configuration reads credentials from env vars `DB_URL`, `DB_USER`, `DB_PASSWORD`
(with the current Supabase values as fallbacks).

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`. Vite proxies `/api` and `/ws` to `http://localhost:8080`.

### 3. Docker (optional, both at once)

```bash
docker compose up --build
```

## Step-by-step implementation plan

1. **Schema + entities** — already wired. On first boot, `SeedDataLoader` inserts
   five demo zones around Casa-Voyageurs.
2. **Verify ingestion** — POST a camera event with `curl` or via the simulator page
   and watch the logs: you should see correlation, persistence, WebSocket broadcast,
   and the mock radio receiver log line.
3. **UI smoke test** — open the dashboard, open the simulator, click the three
   simulation buttons and watch alerts flow in live (no refresh).
4. **Extend correlation** — add a new rule (e.g., "SIG reports track maintenance
   while a camera reports movement in that zone"). Drop a class in `service/correlation/rules`
   and it plugs in automatically.
5. **Harden for PFE defense** — add JWT auth, push real SIG geometry (polygons
   via PostGIS), replace the mock radio with the real external API once documented.

## Security note

The provided Supabase credentials are for a demo database. For a production-grade
PFE deliverable, move them to `.env` or a secrets manager and rotate them.
