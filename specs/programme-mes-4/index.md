# Programme: MikeMES (MES-4)

Full-scope aerospace/defence Manufacturing Execution System.
Breakdown generated: 2026-05-20 · 18 child Epics across 6 phases.

## Phase Overview

| Phase | Focus | Epics |
|-------|-------|-------|
| P1 | Foundation — IAM, platform, audit | MES-5, MES-6, MES-7 |
| P2 | Master data — parts, BOMs, routes, instructions, labour, quality plans, documents | MES-8 – MES-13 |
| P3 | Execution — work orders, shop floor, receiving, inventory | MES-14 – MES-17 |
| P4 | Quality & NCM — inspection recording, nonconformance | MES-18, MES-19 |
| P5 | Supporting — gauge/tool calibration, OSP | MES-20, MES-21 |
| P6 | Integration — ERP/MRP, machine data, customer portals | MES-22 |

## Child Epics

| Key | Phase | Title | Microservice | Status |
|-----|-------|-------|-------------|--------|
| [MES-5](https://artical.atlassian.net/browse/MES-5) | P1 | IAM & Multi-Org Security (Keycloak) | iam-service | To Do |
| [MES-6](https://artical.atlassian.net/browse/MES-6) | P1 | Platform & System Administration | admin-service, platform-service | To Do |
| [MES-7](https://artical.atlassian.net/browse/MES-7) | P1 | System Activity & Audit Logging | audit-service | To Do |
| [MES-8](https://artical.atlassian.net/browse/MES-8) | P2 | Item Master & BOM Management | work-order-service | To Do |
| [MES-9](https://artical.atlassian.net/browse/MES-9) | P2 | Manufacturing Routing | shopfloor-service | To Do |
| [MES-10](https://artical.atlassian.net/browse/MES-10) | P2 | Work Instructions | engineering-service | To Do |
| [MES-11](https://artical.atlassian.net/browse/MES-11) | P2 | Labour Resources & Skills | labour-service | To Do |
| [MES-12](https://artical.atlassian.net/browse/MES-12) | P2 | Quality Inspection Planning (Control Plans) | quality-service | To Do |
| [MES-13](https://artical.atlassian.net/browse/MES-13) | P2 | Document Management | document-service | To Do |
| [MES-14](https://artical.atlassian.net/browse/MES-14) | P3 | Work Orders & Scheduling | work-order-service | To Do |
| [MES-15](https://artical.atlassian.net/browse/MES-15) | P3 | Shop Floor Tracking & Execution | shopfloor-service | To Do |
| [MES-16](https://artical.atlassian.net/browse/MES-16) | P3 | Material Receiving & Inbound Inspection | receiving-service | To Do |
| [MES-17](https://artical.atlassian.net/browse/MES-17) | P3 | Inventory & Materials Management | inventory-service | To Do |
| [MES-18](https://artical.atlassian.net/browse/MES-18) | P4 | Quality Inspection Recording & Results | quality-service | To Do |
| [MES-19](https://artical.atlassian.net/browse/MES-19) | P4 | Nonconformance Management (NCM) | ncm-service | To Do |
| [MES-20](https://artical.atlassian.net/browse/MES-20) | P5 | Gauge & Tool Management | gauge-tool-service | To Do |
| [MES-21](https://artical.atlassian.net/browse/MES-21) | P5 | Outside Processing (OSP) | osp-service | To Do |
| [MES-22](https://artical.atlassian.net/browse/MES-22) | P6 | Inbound/Outbound Integrations | integration-service | To Do |

## Dependency Chain (Blocks links)

```
MES-5 (IAM) ──────────────────────────────────────────────► MES-7, MES-8
MES-6 (Platform) ──────────────────────────────────────────► MES-7
MES-7 (Audit) ─────────────────────────────────────────────► MES-8
MES-8 (Item Master) ───────────────────────────────────────► MES-9, MES-10, MES-11, MES-12, MES-14
MES-9  (Routing)           ────────────────────────────────► MES-14, MES-15
MES-10 (Work Instructions) ────────────────────────────────► MES-14, MES-15
MES-11 (Labour)            ────────────────────────────────► MES-14
MES-12 (Quality Planning)  ────────────────────────────────► MES-14, MES-18
MES-13 (Documents)         ────────────────────────────────► MES-14
MES-14 (Work Orders)  ─────────────────────────────────────► MES-15, MES-21
MES-15 (Shop Floor)   ─────────────────────────────────────► MES-18
MES-16 (Receiving)    ─────────────────────────────────────► MES-17
MES-18 (Quality Recording) ────────────────────────────────► MES-19
```

## Workflow: Epic → Spec → Plan → Tasks → Jira

For each child Epic, run the reverse flow:

```
/speckit-from-jira MES-N   # import Epic → write spec, stop for review
/speckit-plan              # generate implementation plan
/speckit-tasks             # generate task list
/speckit-taskstoissues     # push tasks back to Jira under the Epic
```

## Compliance Register

AS9100D · AS9102 · AS9103 · AS9131 · AS9145 · AS6174 · AS5553 · AS13100 ·
AS9117 · AS9146 · AS9134 · ISA-95 · ISA-88 · MTConnect · IPC-2591 ·
NIST SP 800-171 / CMMC Level 2 · 21 CFR Part 11 / EU Annex 11 ·
ATA Spec 2000 · ISO 14224 · ISO 10303 (STEP) · QIF (ISO 23952) · OAGIS
