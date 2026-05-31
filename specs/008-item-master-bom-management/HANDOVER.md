# MES-8 Implementation Handover
**Branch**: `008-item-master-bom-management` | **Date**: 2026-05-31 | **Head**: `c0219ec`

---

## Project Context

**Aurora MES** — aerospace/defence Manufacturing Execution System. Java 21 / Spring Boot 3.3 microservices backend, Angular 21 SPA frontend. Monorepo at `C:\Users\mike_\Documents\GitHub\MikeMES`.

**Jira Epic**: MES-8 — Item Master & BOM Management
**Constitution**: v1.2.1 (PATCH amended today — 21 CFR Part 11 aerospace carve-out added to §IV)
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

**Note on backend tasks that are DONE but tasks.md still shows `[ ]`:**
- T067–T087 (effectivity + ECO) — fully implemented in PR #12
- T088–T094 (AS5553) — fully implemented in PR #13
- T190–T192 (QUALITY_ENGINEER role seed) — need verification; check `V014__seed_quality_engineer_role.sql` exists

---

## What Remains To Implement

### Next PR: PR 6b — Phase 11b + 11c (Angular frontend fidelity)
**Target branch**: `008-item-master-bom-management` → PR to `Develop`
**CI anchor**: `ng build --configuration=production` + `ng test --watch=false` + `ng lint --max-warnings 0`

#### Incomplete tasks (all in `specs/008-item-master-bom-management/tasks.md`):

| Task | Description | Status |
|------|-------------|--------|
| T177 | `ClassificationLabelPipe` — maps enum to display label (FR-040) | NOT STARTED |
| T178 | `BreadcrumbComponent` in `shared/ui/breadcrumb/` (FR-043) | NOT STARTED |
| T179 | "Showing X–Y of Z items" pagination text in list (FR-042) | NOT STARTED |
| T180 | `clone()` method in `ItemMasterApiService` + backend endpoint already exists | NOT STARTED |
| T181 | Clone Item in overflow menu → navigate to `/item-master/new?cloneFrom={id}` | NOT STARTED |
| T182 | `ItemMasterCreateComponent` — full-page route `/item-master/new` (FR-033, FR-044–FR-047) | PARTIALLY STARTED (model update only — see below) |
| T183 | `ItemMasterEditComponent` — full-page route `/item-master/:id/edit` (FR-033a, FR-048) | NOT STARTED |
| T184 | Add `stepPartRef` to `ItemMasterDto`, `CreateItemMasterRequest`, `PatchItemMasterRequest` | PARTIALLY DONE — `stepPartRef` added to `ItemMasterDto` only |
| T185 | Column picker: title "Customise Columns", locked rows get drag handles, add `counterfeitRiskLevel`+`stepPartRef` as `udf:true` to default-columns | NOT STARTED |
| T197 | `UdfApiService` Angular service | **ALREADY DONE** — file exists at `services/udf-api.service.ts` |
| T198 | Angular unit test: `ClassificationLabelPipe` | NOT STARTED |
| T199 | Angular unit test: `ItemMasterCreateComponent` | NOT STARTED |
| T200 | Angular unit test: `ItemMasterEditComponent` | NOT STARTED |
| T201 | Angular unit test: `BomExplosionComponent` | NOT STARTED |
| T202 | Angular unit test: `BomAuthoringComponent` | NOT STARTED |

**Also needs updating alongside Phase 11b:**
- `app.routes.ts` — add `/item-master/new` and `/item-master/:id/edit` routes
- `item-master-list.component.ts` — fix Edit/New to navigate to routes (not dialog), add overflow menu actions (View Detail, Edit, Clone Item, Obsolete), pagination text
- `item-master-detail.component.ts` — fix Edit button to navigate to `/item-master/:id/edit` (not dialog)

### After PR 6b: PR 7 — Phase 12 + 13 (BOM + ECO frontend)
**NO BOM or ECO frontend components exist yet.** All T149–T166, T169, T170, T175–T176, T186–T189 are not started.

---

## Current Angular Codebase State

### Files that EXIST (do not recreate):

```
frontend/angular/src/app/
├── app.routes.ts                          ← needs /item-master/new and /:id/edit added
├── layout/
│   ├── index.ts
│   └── shell/app-shell.component.ts      ← DONE (PR #18)
├── shared/
│   ├── grid/
│   │   ├── index.ts
│   │   ├── models/column-def.model.ts
│   │   ├── services/grid-preference.service.ts
│   │   ├── services/user-grid-preference-api.service.ts
│   │   └── components/column-picker/column-picker.component.ts  ← needs title fix (T185)
│   ├── theme/
│   │   ├── index.ts
│   │   ├── services/theme.service.ts
│   │   └── components/theme-toggle/theme-toggle.component.ts
│   └── ui/
│       ├── index.ts                       ← needs BreadcrumbComponent export added
│       └── status-badge/status-badge.component.ts
└── features/item-master/
    ├── constants/default-columns.ts       ← needs counterfeitRiskLevel + stepPartRef (udf:true) added
    ├── models/item-master.model.ts        ← stepPartRef added to Dto; STILL NEEDS: fix CreateItemMasterRequest optional fields + stepPartRef, PatchItemMasterRequest stepPartRef
    ├── services/
    │   ├── item-master-api.service.ts     ← needs clone() method added
    │   └── udf-api.service.ts             ← DONE ✅
    ├── pages/
    │   ├── item-master-list/item-master-list.component.ts   ← needs navigation + menu + pagination fixes
    │   └── item-master-detail/item-master-detail.component.ts  ← needs Edit to navigate (not dialog)
    └── components/
        └── item-master-form/item-master-form.component.ts   ← KEEP (still used by list + detail as interim)
```

### Files that DO NOT EXIST yet (need creating):

```
shared/ui/breadcrumb/breadcrumb.component.ts     (T178)
features/item-master/pipes/classification-label.pipe.ts  (T177)
features/item-master/pages/item-master-create/item-master-create.component.ts  (T182)
features/item-master/pages/item-master-edit/item-master-edit.component.ts      (T183)
```

---

## Key Implementation Details

### T177 — ClassificationLabelPipe (FR-040)
Exact mapping required (used in chips, dropdowns, detail views):
```
ASSEMBLY      → "ASSEMBLY"
COTS          → "COTS"
FABRICATED    → "FABRICATED"
PURCHASED_PART → "PURCHASED"
RAW_MATERIAL  → "RAW MATERIAL"
SERVICE       → "SERVICE"
```
Location: `frontend/angular/src/app/features/item-master/pipes/classification-label.pipe.ts`

### T178 — BreadcrumbComponent (FR-043)
- `@Input() crumbs: { label: string; route?: string[] }[]`
- All segments except last are `routerLink`; last is plain text; separator ` / `
- Location: `frontend/angular/src/app/shared/ui/breadcrumb/breadcrumb.component.ts`
- Must be exported from `shared/ui/index.ts`
- Breadcrumb strings per screen:
  - List: `[{label:'Home', route:['/']}, {label:'Materials'}, {label:'Item Master', route:['/item-master']}]` — actually last is current so: `[{label:'Home'}, {label:'Materials'}, {label:'Item Master'}]`
  - Create: `[{label:'Materials'}, {label:'Item Master', route:['/item-master']}, {label:'New Item'}]`
  - Edit: `[{label:'Materials'}, {label:'Item Master', route:['/item-master']}, {label: partNumber + ' Rev ' + revision}]`
  - Detail: same as Edit

### T182 — ItemMasterCreateComponent (FR-033, FR-044, FR-045, FR-046, FR-047)
Full-page route `/item-master/new`. Key requirements:
- **Two-column layout**: left "Core Identification", right "Traceability & Compliance"
- Left: Part Number*, Revision*, Description* (textarea), Unit of Measure* (dropdown), Classification* (dropdown), Make/Buy Code* (two independent toggle buttons)
- Right: Traceability Method* (dropdown), Shelf Life toggle + conditional Shelf Life Days field, CAGE Code, Counterfeit Risk Level dropdown, Verification Required toggle, STEP Part Reference text input (placeholder "e.g. S000-BRKT-001")
- Below (full width): "User-Defined Fields" section — subtitle "N fields configured for ITEM_MASTER module" + dynamic UDF fields from `UdfApiService.listFields('ITEM_MASTER')`
- Breadcrumb: Materials / Item Master / New Item
- Page title: "New Item" | subtitle: "* Required fields"
- Actions: Cancel (→ /item-master) + Save Item (calls api.create(), on success → /item-master/:id)
- Reads `cloneFrom` query param → if set, calls api.getById(cloneFrom) to pre-fill all fields except partNumber and revision
- 422 server errors → inline `p-message` per field violation
- **Make/Buy (FR-046)**: Two independent `p-button` toggles. Track `makeActive: boolean` and `buyActive: boolean`. Map: MAKE→(T,F), BUY→(F,T), EITHER→(T,T). Invalid if both false — validation error "At least one of Make or Buy must be selected".

### T183 — ItemMasterEditComponent (FR-033a, FR-048)
Full-page route `/item-master/:id/edit`. Same two-column layout. Differences:
- Part Number, Revision, Traceability Method → read-only display (not inputs)
- Page title: "{partNumber} / Rev {revision}"
- Status badge inline next to title
- "Obsolete this item" text-link button — hidden when status is OBSOLETE or user lacks `item-master:records:manage` privilege; calls api.obsolete() then refreshes
- Cancel → navigate to `/item-master/:id` (detail page)
- Save Changes → api.patch(), on success navigate to `/item-master/:id`

### T184 — model.ts remaining changes needed
`CreateItemMasterRequest` needs optional fields:
```typescript
cageCode?: string;
shelfLifeControlled?: boolean;
shelfLifeDays?: number;
counterfeitRiskLevel?: CounterfeitRiskLevel;
verificationRequired?: boolean;
approvedSuppliers?: string[];
stepPartRef?: string;
customFields?: Record<string, unknown>;
```
`PatchItemMasterRequest` needs:
```typescript
traceabilityMethod?: TraceabilityMethod;
stepPartRef?: string;
```

### T185 — Column picker + default-columns
**`column-picker.component.ts`**:
- Header title: "Columns" → "Customise Columns"
- Locked rows: always show drag handle (but disable drag action) — change `*ngIf="!col.locked"` on drag handle to always show; `[cdkDragDisabled]="col.locked ?? false"` already correct

**`default-columns.ts`**:
Add at end:
```typescript
{ key: 'counterfeitRiskLevel', label: 'Counterfeit Risk Level', visible: false, order: 9,  udf: true },
{ key: 'stepPartRef',          label: 'STEP Part Reference',    visible: false, order: 10, udf: true },
```

### T180 — clone() in ItemMasterApiService
```typescript
clone(id: string): Observable<ItemMasterDto> {
  return this.http.post<ItemMasterDto>(`${this.base}/${id}/clone`, {});
}
```
Backend endpoint `POST /api/v1/item-master/{id}/clone` already exists (merged in PR #17 fix-up).

### List component changes needed (T179, T181)
1. Replace `openCreateDialog()` with `router.navigate(['/item-master/new'])`
2. Replace `openEditDialog(id)` with `router.navigate(['/item-master', id, 'edit'])`
3. `showRowMenu()` — replace current "Obsolete only" menu with all 4 actions per FR-041:
   - View Detail → `router.navigate(['/item-master', item.id])`
   - Edit → `router.navigate(['/item-master', item.id, 'edit'])`
   - Clone Item → `router.navigate(['/item-master/new'], { queryParams: { cloneFrom: item.id } })`
   - Obsolete → existing logic
4. Add pagination text "Showing X–Y of Z items" below toolbar (T179/FR-042)
5. Import and use `ClassificationLabelPipe` for chip `[value]` binding
6. Import and use `BreadcrumbComponent`
7. Remove `ItemMasterFormComponent` import (no longer needed when both routes work)

### Detail component changes needed
1. `openEdit()` → `this.router.navigate(['/item-master', this.itemId, 'edit'])`
2. Remove `showEditDialog` state and the `@if (showEditDialog)` dialog block
3. Remove `ItemMasterFormComponent` import
4. Add `BreadcrumbComponent` with correct crumbs

### app.routes.ts changes needed
```typescript
{
  path: 'item-master/new',
  loadComponent: () =>
    import('./features/item-master/pages/item-master-create/item-master-create.component')
      .then(m => m.ItemMasterCreateComponent),
},
{
  path: 'item-master/:id/edit',
  loadComponent: () =>
    import('./features/item-master/pages/item-master-edit/item-master-edit.component')
      .then(m => m.ItemMasterEditComponent),
},
```
**Important**: `/item-master/new` must come BEFORE `/item-master/:id` in the routes array or Angular will match "new" as an ID.

---

## After Phase 11b: Phase 12 + 13 (PR 7)

No BOM or ECO Angular components exist at all. Implement in this order:
1. **T197 (done)**, then **T149** `BomApiService`
2. **T150–T152** BOM list screen
3. **T153** BOM authoring screen (prerequisite: `DEFAULT_BOM_LINE_COLUMNS` in `features/bom/constants/default-columns.ts`)
4. **T154** AddBomLineFormComponent
5. **T175** BomHeaderEditDialogComponent (uses `UdfApiService` with `BOM_HEADER`)
6. **T176** `patchHeader()` in BomApiService
7. **T155** BomExplosionComponent
8. **T156** BOM routes in app.routes.ts
9. **T186** CSV/PDF backend endpoints (Apache PDFBox — add to build.gradle)
10. **T187** Wire export buttons in explosion view
11. **T188** referenceDesignators frontend column
12. **T189** bottom status bar + unit-effectivity badge
13. **T159–T164** ECO API service + list + form + detail + approve + routes
14. **T169, T170** lint gates

---

## Commit Format (mandatory)

```
[type](MES-8): short description [TXXX]

Ref: MES-8
Task: TXXX
```

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`

---

## Key Decisions / Gotchas

1. **`ItemMasterFormComponent` (dialog)** — keep it; list and detail still reference it. It gets replaced when the new create/edit page routes are wired up and the dialog calls are switched to navigation.

2. **Route ordering** — `/item-master/new` MUST appear before `/item-master/:id` in routes array.

3. **UdfApiService** — already exists, do not recreate. Method is `listFields(moduleKey: string)`.

4. **Backend clone endpoint** — already merged (PR #17). No backend work needed for T180.

5. **stepPartRef backend** — already in `ItemMaster` entity and `ItemMasterDto` (Java). Frontend models just needed `stepPartRef` added to TypeScript interfaces.

6. **T192 (QUALITY_ENGINEER 403 scenario in AS5553IT)** — backend test, not frontend. May already exist from PR #13; needs verification.

7. **T193/T194 (ModuleKey BOM_LINE/BOM_HEADER)** — backend lib change, may already exist from PR #11/17; needs verification against `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java`.

8. **V013 migration** — needed for BOM header edit fields (reason_for_revision, production_line, bom_type, effectivity_type, custom_fields). Check if this already exists in `services/work-order-service/src/main/resources/db/migration/`.

9. **Performance tasks T171/T172** (k6 load tests) and **T173** (UdfLibReusabilityIT) are remaining polish items; address after PR 7.

10. **Constitution v1.2.1** — already committed. Do not re-amend.
