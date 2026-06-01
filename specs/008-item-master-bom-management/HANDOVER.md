# MES-8 Implementation Handover
**Branch**: `008-item-master-bom-management` | **Date**: 2026-05-31 | **Head**: `9f229b2`

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

**Note on tasks that are DONE but tasks.md still shows `[ ]`:**
- T067–T087 (effectivity + ECO) — fully implemented in PR #12
- T088–T094 (AS5553) — fully implemented in PR #13
- T177–T185, T198–T200 (Phase 11b) — fully implemented in PR #19
- T190–T192 (QUALITY_ENGINEER role seed) — need verification; check `V014__seed_quality_engineer_role.sql` exists

---

## What Remains To Implement

### Next PR: PR 7 — Phase 12 + 13 (BOM + ECO frontend)
**Target branch**: `008-item-master-bom-management` → PR to `Develop`
**CI anchor**: `ng build --configuration=production` + `ng test --watch=false` + `ng lint --max-warnings 0`

**NO BOM or ECO Angular components exist yet.** Implement in this order:

| Step | Task(s) | Description |
|------|---------|-------------|
| 1 | T149 | `BomApiService` Angular service |
| 2 | T150–T152 | BOM list screen |
| 3 | T153 | BOM authoring screen (prerequisite: `DEFAULT_BOM_LINE_COLUMNS` in `features/bom/constants/default-columns.ts`) |
| 4 | T154 | `AddBomLineFormComponent` |
| 5 | T175 | `BomHeaderEditDialogComponent` (uses `UdfApiService` with `BOM_HEADER`) |
| 6 | T176 | `patchHeader()` in `BomApiService` |
| 7 | T155 | `BomExplosionComponent` |
| 8 | T156 | BOM routes in `app.routes.ts` |
| 9 | T186 | CSV/PDF backend endpoints (Apache PDFBox — add to `build.gradle`) |
| 10 | T187 | Wire export buttons in explosion view |
| 11 | T188 | `referenceDesignators` frontend column |
| 12 | T189 | Bottom status bar + unit-effectivity badge |
| 13 | T159–T164 | ECO API service + list + form + detail + approve + routes |
| 14 | T169, T170 | Lint gates |
| 15 | T201, T202 | Angular unit tests: `BomExplosionComponent`, `BomAuthoringComponent` |

**Also verify before starting PR 7:**
- T193/T194 (ModuleKey `BOM_LINE`/`BOM_HEADER`) — backend lib change, may already exist; check `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java`
- V013 migration — needed for BOM header edit fields; check if it exists in `services/work-order-service/src/main/resources/db/migration/`

**After PR 7:** Performance tasks T171/T172 (k6 load tests) and T173 (UdfLibReusabilityIT).

---

## Current Angular Codebase State

### Files that EXIST (do not recreate):

```
frontend/angular/src/app/
├── app.routes.ts                          ← DONE: /item-master/new + /:id/edit + /:id wired
├── layout/
│   ├── index.ts
│   └── shell/app-shell.component.ts      ← DONE (PR #18)
├── shared/
│   ├── grid/
│   │   ├── index.ts
│   │   ├── models/column-def.model.ts
│   │   ├── services/grid-preference.service.ts
│   │   ├── services/user-grid-preference-api.service.ts
│   │   └── components/column-picker/column-picker.component.ts  ← DONE: title "Customise Columns", drag handle always shown
│   ├── theme/
│   │   ├── index.ts
│   │   ├── services/theme.service.ts
│   │   └── components/theme-toggle/theme-toggle.component.ts
│   └── ui/
│       ├── index.ts                       ← exports StatusBadgeComponent + BreadcrumbComponent
│       ├── status-badge/status-badge.component.ts
│       └── breadcrumb/breadcrumb.component.ts  ← DONE (PR #19)
└── features/item-master/
    ├── constants/default-columns.ts       ← DONE: counterfeitRiskLevel + stepPartRef (udf:true) added
    ├── models/item-master.model.ts        ← DONE: all optional fields + stepPartRef on Create/Patch requests
    ├── pipes/
    │   └── classification-label.pipe.ts   ← DONE (PR #19)
    ├── services/
    │   ├── item-master-api.service.ts     ← DONE: clone() method added
    │   └── udf-api.service.ts             ← DONE (PR #18)
    ├── pages/
    │   ├── item-master-list/item-master-list.component.ts    ← DONE: navigation + 4-action menu + pagination text + breadcrumbs
    │   ├── item-master-detail/item-master-detail.component.ts ← DONE: Edit navigates to /:id/edit + breadcrumbs
    │   ├── item-master-create/item-master-create.component.ts ← DONE (PR #19)
    │   └── item-master-edit/item-master-edit.component.ts    ← DONE (PR #19)
    └── components/
        └── item-master-form/item-master-form.component.ts   ← KEEP as-is (still valid, unused now that pages exist — can be removed in a later cleanup PR)
```

### Files that DO NOT EXIST yet (all in PR 7 scope):

```
features/bom/
├── constants/default-columns.ts               (T153 prerequisite)
├── services/bom-api.service.ts                (T149)
├── pages/
│   ├── bom-list/bom-list.component.ts         (T150–T152)
│   ├── bom-authoring/bom-authoring.component.ts (T153)
│   └── bom-explosion/bom-explosion.component.ts (T155)
├── components/
│   ├── add-bom-line-form/add-bom-line-form.component.ts (T154)
│   └── bom-header-edit-dialog/bom-header-edit-dialog.component.ts (T175)
features/eco/
├── services/eco-api.service.ts                (T159)
├── pages/
│   ├── eco-list/eco-list.component.ts         (T160)
│   ├── eco-form/eco-form.component.ts         (T161)
│   ├── eco-detail/eco-detail.component.ts     (T162)
│   └── eco-approve/eco-approve.component.ts   (T163)
```

---

## Key Technical Notes for PR 7

### Angular testing
- Project uses **Vitest** runner (`@angular/build:vitest` in `angular.json`)
- Use `toBe(true/false)`, `vi.spyOn` — NOT Jasmine's `toBeTrue()`, `spyOn()`
- Import `{ vi }` from `'vitest'` for mocks
- For components with HTTP calls, prefer `vi.fn().mockReturnValue(of(...))` service mocks over `HttpTestingController` — avoids NG0100 from async state changes during `detectChanges()`
- **Never** use a class getter returning a new array/object literal for template-bound inputs — use a class property updated on data load (NG0100 in dev mode)

### BomApiService (T149)
Backend endpoints already exist (merged in PR #11/17). Base path: `/api/v1/bom`.
Key methods needed: `listHeaders()`, `getHeader(id)`, `createHeader()`, `getLines(headerId)`, `addLine()`, `patchLine()`, `deleteLine()`, `release(id)`, `explode(id)`, `patchHeader()` (T176).

### UdfApiService (T197)
Already exists at `features/item-master/services/udf-api.service.ts`. Do not recreate.
Method: `listFields(moduleKey: string)`. Use `'BOM_HEADER'` for BOM header edit dialog.

### Route ordering (critical)
`/item-master/new` already correctly placed before `/item-master/:id` in `app.routes.ts`.
For BOM routes, follow the same pattern: `/bom/new` before `/bom/:id`.

### Backend clone endpoint
Already merged (PR #17). `POST /api/v1/item-master/{id}/clone` — no backend work needed.

### V013 migration
Needed for BOM header edit fields (`reason_for_revision`, `production_line`, `bom_type`, `effectivity_type`, `custom_fields`). Verify existence before creating frontend edit form.

### Performance tasks
T171/T172 (k6 load tests) and T173 (UdfLibReusabilityIT) are polish items — address after PR 7.

### Constitution v1.2.1
Already committed. Do not re-amend.

---

## Commit Format (mandatory)

```
[type](MES-8): short description [TXXX]

Ref: MES-8
Task: TXXX
```

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`
