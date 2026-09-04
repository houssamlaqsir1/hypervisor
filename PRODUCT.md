# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Two audiences, both durable, and they are not the same people.

**The modeled user** is a railway supervision operator watching infrastructure
for safety incidents. The system defines three roles, and each is a genuinely
different job rather than a permission tier:

- **Operator** (*Supervision Operator*) — works the live alert feed. Acknowledges
  an alert to claim it, resolves it to close it, optionally with a written note.
  This is the role the console is shaped around.
- **Security Manager** (*Viewer*) — read-only. Sees the same feed and history but
  is given no acknowledge or resolve controls; the console tells them so
  explicitly rather than showing disabled buttons.
- **Technical Administrator** (*Admin*) — everything the operator can do, plus
  managing user accounts, camera registrations, and surveillance zones, plus
  deleting alerts from the log.

**The immediate evaluator** is a PFE (end-of-studies) jury watching a roughly
15-minute live demonstration on a laptop, driven by one person, over a projector
or screen-share. They are not operators and will not have seen the system
before, so anything that needs a shift's familiarity to make sense will not land.

## Product Purpose

Centralized event processing and supervision for railway infrastructure.

The system ingests events from two independent sources — an **AI camera system**
and a **3D SIG (geographic/positional) system** — correlates them in real time
through a rule engine, grades the result into an alert, dispatches it to an
external Alert Radio, and pushes it live to an operator console where a person
acknowledges and closes it.

Success is the loop being complete and demonstrable: an event enters, correlation
grades it, an operator sees it without refreshing, acts on it, and the incident
closes with an audit trail of who did what and when.

## Positioning

The correlation engine is the product. Not the cameras, not the map.

Neither an object-detection product nor a GIS product can truthfully make the
claim this one makes: that an AI camera detection and an *independent* SIG
observation of the same place at the same moment were fused into a single graded
alert, carrying the evidence of that agreement — a fusion score, a distance
delta, a time delta, and the identity of both contributing sources. A neighboring
product with one input can grade a detection; it cannot corroborate one.

## Operating Context

- **The demo scene:** one laptop, one driver, ~15 minutes, projected or
  screen-shared to a room seeing it for the first time.
- **The whole stack starts with one command.** `docker compose up --build` brings
  up backend, YOLOv8 detector, MediaMTX relay, and the nginx-served frontend.
- **The live video path is real and physical:** a phone running Larix pushes RTMP
  → MediaMTX republishes as RTSP → the Python YOLOv8 service reads it, detects,
  and posts detections to the backend → the browser watches the same stream over
  HLS with detection boxes drawn on top.
- **A genuinely external live feed:** the backend polls the OpenSky Network for
  real aircraft over Morocco every few seconds as a working SIG source, so the
  fusion path has a second input nobody in the room controls.
- **Three seeded accounts** (`admin`, `operator`, `viewer`) exist so role
  differences can be shown by signing out and back in mid-demo.
- **The 3D globe needs a key.** Cesium requires `VITE_CESIUM_ION_TOKEN` in
  `frontend/.env` at build time; without it the map renders black.
- **Database is remote.** PostgreSQL hosted on Supabase, so the demo depends on
  network access.

## Capabilities and Constraints

### Confirmed functionality

- **Ingestion:** camera events, SIG events, and live webcam detections, each over
  its own REST endpoint.
- **Correlation:** twelve rules, auto-discovered by the engine — intrusion in a
  restricted zone, object on track, escalation, low-confidence anomaly,
  camera/SIG fusion, loitering, fall detection, track proximity, unattended
  baggage, crowd density, night activity, moderate station activity.
- **Alert model:** four severities (LOW, MEDIUM, HIGH, CRITICAL); twelve types;
  a lifecycle of NEW → ACKNOWLEDGED → RESOLVED that records the acting user, the
  timestamp, and an optional resolution note.
- **Live delivery:** STOMP over SockJS pushes new alerts to open consoles without
  a refresh; detection frames stream to the video overlay on the same socket.
- **Analytics:** counts by severity, type, zone and status over a 7/30/90-day
  window, plus CSV export.
- **3D map:** Cesium globe carrying zones, rail geometry, alert pins, and dashed
  links between the camera and SIG points of a fused alert.
- **Zones:** four types (RESTRICTED, TRACK, STATION, NORMAL), each a centre point
  plus a radius, editable by an administrator.
- **Behavior model:** a logistic-regression loitering classifier trained offline
  and loaded at boot.
- **Bilingual:** complete English and French interface, switchable at runtime.

### Fixed terminology

These words are load-bearing in the domain, the database and the API, and future
work should not rename them casually: *hypervisor* (the system as a whole),
*severity* vs *status* (how bad vs how far along — two separate axes on every
alert), *zone*, *SIG* (the geographic/positional source), *fusion* (specifically
camera + SIG corroboration), *correlation engine*.

### Technical constraints

- Spring Boot 4 / Java 21, Spring Data JPA, Spring Security with JWT, STOMP
  WebSocket. Schema is auto-managed (`ddl-auto: update`).
- React 19 + TypeScript + Vite, React Router, Cesium/Resium, hls.js, sonner.
  No CSS framework — the design system is hand-written CSS custom properties.
- Python YOLOv8 detector as a separate service; MediaMTX as the media relay.
- Docker Compose for deployment; nginx serves the built bundle and proxies
  `/api` and `/ws` so everything is same-origin.
- **Alert message text is composed by the backend's correlation rules and stored
  on the alert.** It arrives already written and is *not* translated by the
  frontend. Any future work on wording has to happen in the rules, not the UI.

### Explicitly undecided

- Whether the mock Alert Radio receiver is ever replaced by a real external API.
- Whether SIG geometry moves from centre-plus-radius circles to true polygons
  (PostGIS).
- Whether the system is actually deployed to ONCF after the defense.

## Brand Commitments

- **Name:** Hypervisor. In-product subtitle: "ONCF Security Platform" /
  "Plateforme de sûreté ONCF".
- **ONCF is the study subject, not a partner.** There is no sanctioned branding,
  no logo licence, and no endorsement. Future work must not introduce ONCF
  logotypes or visual identity, claim or imply approval, or present the system as
  an official ONCF product.
- **Own mark:** a shield over rail track, used as the favicon and as the in-app
  brand mark. It is the project's own, and it is what should grow if an identity
  is ever needed.
- **English leads.** English is the language of the defense and the report, and
  is the interface default. French is a fully supported second language, not a
  courtesy translation, and must not be allowed to break layouts or go stale.

## Evidence on Hand

**Real, and safe to show as real:**

- The running system. Live YOLOv8 detection from a physical phone camera through
  the RTMP → RTSP → detector → backend → browser path.
- Live OpenSky Network aircraft positions over Morocco — genuinely external data
  arriving during the demo.
- A trained loitering classifier, `loitering-v1-synthetic`: 3000 samples,
  reported test accuracy 0.99875, threshold 0.6.

**Invented, and must never be presented as real:**

- Every camera, zone, site name, coordinate and stored alert in the database.
  The Casablanca, Rabat, Fes, Kenitra, Marrakech, Oujda and Signal Box placements
  are illustrative, not real ONCF installations.
- The three seeded user accounts and their job titles.

**Absent, and must not be fabricated:**

- ONCF endorsement, partnership, or any deployment history.
- Field-measured accuracy, uptime, false-positive rates, or operator testimony.
  The loitering model's 0.99875 was measured on *synthetic* data and must always
  be qualified as such — quoting it bare would be the single easiest way to make
  this project look dishonest.
- Customers, pricing, licensing, or benchmarks against other systems.

## Product Principles

1. **The loop is the thesis.** Ingestion → correlation → alert → operator action
   → closure must stay visible and complete end to end. No stage may be reduced
   to a stub for demo convenience; the completeness *is* the claim.

2. **Demoed on a laptop, built for a control room.** Optimize for the 15-minute
   screen-shared session, but never make a choice that would have to be undone to
   put this in front of a real operator on a real shift.

3. **Corroboration over detection.** When a surface has to choose what to show,
   show that two independent sources agreed and the evidence for it. That is the
   claim nothing neighboring can copy.

4. **Nothing invented may look real.** Fabricated data, synthetic model scores,
   and the ONCF relationship all stay qualified. The interface must never lend
   borrowed authority to content that has not earned it.

5. **Legible to someone seeing it for the first time.** The room has not worked a
   shift here. Severity, status, and what happens next must be readable without
   prior familiarity or a legend.

## Accessibility & Inclusion

No formal standard was established for this project. Two commitments already in
the code are safety properties rather than styling, and should be preserved:

- **Severity and live state are never carried by color alone.** Severity badges
  name the level in words; live/connected states carry a dot and a label as well
  as a hue. A red/green color-blind viewer, or a projector with poor color
  reproduction, must still be able to rank alerts.
- **`prefers-reduced-motion` is honored throughout**, including animation delays,
  so no content is ever left invisible waiting for a suppressed animation.
