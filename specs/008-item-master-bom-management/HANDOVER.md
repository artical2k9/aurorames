# MES-8 Implementation Handover
**Branch**: `008-item-master-bom-management` | **Date**: 2026-06-01 | **Head**: `7fda9e7`

---

## Project Context

**Aurora MES** — aerospace/defence Manufacturing Execution System. Java 21 / Spring Boot 3.3 microservices backend, Angular 21 SPA frontend. Monorepo at `C:\Users\mike_\Documents\GitHub\MikeMES`.

**Jira Epic**: MES-8 — Item Master & BOM Management
**Constitution**: v1.2.1 (21 CFR Part 11 aerospace carve-out added to §IV)
**Branching rule**: Feature branches from `Develop`; PRs target `Develop`; never touch `main`.

---

## What Is Already Merged (DO NOT RE-IMPLEMENT)

All GitHub PRs below have merged to `Develop`. The tasks.md `[ ]` markers for these are stale — the code is live.

| GitHub PR | Plan Phase | Scope | Key tasks |
|-----------|-----------|-------|-----------|
| PR #10 | Phase 1–4 | work-order-service scaffold + Item Master CRUD + UDF framework + grid preferences | T001–T050, T104–T112 |
| PR #11 | Phase 5 | BOM authoring: headers, lines, release, explosion (flat+indented), circular detection, Kafka events | T051–T066, T174 |
| PR #12 | Phase 6+7 | BOM effectivity (date/unit ranges, overlap, gap detection) + ECO domain (CRUD, approve, concurrent warning) | T067–T087 |
| PR #13 | Phase 8 | AS5553 counterfeit fields: explosion alert flag, risk-added Kafka event, risk-level search filter | T088–T094 |
| PR #14 | Phase 9 | AuditTrailIT, ISA-95 comment on ItemMaster entity, compliance spot-check | T095–T103 (partially) |
| PR #15 | Phase 10 | Angular shared grid (ColumnPicker, GridPreferenceService), dark/light theme, Item Master list (basic) | T113–T130, T167 |
| PR #17 | Fix-up | PATCH BOM line endpoint, gateway route, some speckit-analyze remediations | T174 (update) |
| PR #18 | Phase 11 | App shell (nav rail + top bar), Item Master list fidelity, `ItemMasterFormComponent` (interim dialog), `ItemMasterDetailComponent` | T131–T148, T168 |
| PR #19 | Phase 11b | Angular Item Master create/edit full-page routes, BreadcrumbComponent, ClassificationLabelPipe, overflow menu, pagination text, column picker fixes | T177–T185, T198–T200 |
| PR #20 | Phase 12+13 | BOM frontend (BomApiService, BomListComponent, BomAuthoringComponent, BomExplosionComponent) + ECO frontend (EcoApiService, EcoListComponent, EcoFormComponent, EcoDetailComponent) + backend prerequisites (ModuleKey BOM_LINE/BOM_HEADER, V013+V014 migrations, BOM list/delete/patch/export endpoints, ECO list endpoint, Apache PDFBox CSV/PDF export) | T149–T166, T169–T170, T175–T176, T186–T189, T193–T196, T201–T202 |

**Note on tasks that are DONE but tasks.md still shows `[ ]`:**
- T067–T087 (effectivity + ECO backend) — fully implemented in PR #12
- T088–T094 (AS5553) — fully implemented in PR #13
- T149–T166, T169–T170, T175–T176, T186–T189, T193–T196, T201–T202 (Phase 12+13) — fully implemented in PR #20
- T177–T185, T198–T200 (Phase 11b) — fully implemented in PR #19
- T190–T192 (QUALITY_ENGINEER role seed) — need verification; check `V014__seed_quality_engineer_role.sql` exists in `services/work-order-service/src/main/resources/db/migration/`

---

## What Remains To Implement

### Remaining tasks (polish / performance)

| Task(s) | Description | Notes |
|---------|-------------|-------|
| T171, T172 | k6 load tests for Item Master and BOM endpoints | Polish — not a CI gate |
| T173 | `UdfLibReusabilityIT` — verifies UDF lib works with multiple modules | Polish |

These are the only tasks from the original plan not yet delivered. No blocking frontend or backend work remains for MES-8 spec compliance.

---

## Current Angular Codebase State

### Key files that EXIST (do not recreate):

```
frontend/angular/src/app/
├── app.routes.ts                          ← DONE: all routes wired (item-master, bom, eco)
├── layout/shell/app-shell.component.ts   ← DONE: nav rail has Dashboard, Item Master, BOM, ECO
├── shared/grid/                          ← DONE: ColumnPicker, GridPreferenceService
├── shared/theme/                         ← DONE: ThemeService, ThemeToggleComponent
├── shared/ui/                            ← DONE: StatusBadgeComponent, BreadcrumbComponent
└── features/
    ├── item-master/                      ← DONE: list, detail, create, edit pages + all services/pipes
    ├── bom/                              ← DONE (PR #20)
    │   ├── constants/default-columns.ts
    │   ├── models/bom.model.ts
    │   ├── services/bom-api.service.ts
    │   ├── pages/bom-list/bom-list.component.ts
    │   ├── pages/bom-authoring/bom-authoring.component.ts
    │   ├── pages/bom-explosion/bom-explosion.component.ts
    │   ├── components/add-bom-line-form/add-bom-line-form.component.ts
    │   └── components/bom-header-edit-dialog/bom-header-edit-dialog.component.ts
    └── eco/                              ← DONE (PR #20)
        ├── models/eco.model.ts
        ├── services/eco-api.service.ts
        ├── pages/eco-list/eco-list.component.ts
        ├── pages/eco-detail/eco-detail.component.ts
        └── components/eco-form/eco-form.component.ts
```

### Routes wired in `app.routes.ts`:
- `/item-master` → ItemMasterListComponent
- `/item-master/new` → ItemMasterCreateComponent
- `/item-master/:id/edit` → ItemMasterEditComponent
- `/item-master/:id` → ItemMasterDetailComponent
- `/item-master/:itemId/boms` → BomListComponent
- `/boms/:bomId` → BomAuthoringComponent
- `/boms/:bomId/explosion` → BomExplosionComponent
- `/ecos` → EcoListComponent
- `/ecos/:ecoId` → EcoDetailComponent

---

## Key Technical Notes

### Backend — work-order-service (`/api/v1/`)
All endpoints are live on Develop:
- Item Master: CRUD + obsolete + clone + UDF fields
- BOM: create, get, list-by-item, lines CRUD, release, explode (flat/indented), patch-header, CSV/PDF download
- ECO: create, get, list (paginated), approve
- UDF fields: `GET /api/v1/udf/fields?module={MODULE_KEY}`
- Grid prefs: `GET/PUT /api/v1/grid-preferences/{moduleKey}`

### Backend — migrations applied (Flyway)
V001–V014 all applied. V013 adds BOM header edit fields; V014 mirrors them into `bill_of_materials_aud` (required by Envers schema-validation — **lesson ERR-MES-057**: always update `_aud` table in same migration as entity).

### Angular — testing
- Vitest runner: use `toBe(true/false)`, `vi.spyOn` from `'vitest'` — NOT Jasmine matchers
- Never use class getters returning new array/object literals for template-bound inputs (NG0100)

### Error log
33 promoted lessons in `docs/governance/MES-ERR-001_Index.md`. Key new lessons from this session:
- ERR-MES-055: `tasks.md` stale markers can't distinguish done vs skipped
- ERR-MES-056: speckit tools analyse documents, not code — manual controller reads required
- ERR-MES-057: `@Audited` entity column additions require same columns in `_aud` table
- ERR-MES-058: pre-PR retrospective gate is a technical blocker, not a formality

---

## Commit Format (mandatory)

```
[type](MES-8): short description [TXXX]

Ref: MES-8
Task: TXXX
```

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`
