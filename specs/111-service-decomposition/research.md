# Research: MES-111 Service Decomposition

## D1 — Cross-service call: BomService → EcoService.addOutputBom()

**Decision**: Replace direct Java call with `bom.released` Kafka domain event.

**Rationale**: `BomService.releaseBom()` in `inventory-service` calls `EcoService.addOutputBom()` which will be in `engineering-service`. Direct REST calls across services create runtime coupling (circuit breaker complexity, latency). The Constitution (§VIII) mandates Kafka for cross-service state changes. Publishing `bom.released` event and consuming in `engineering-service` is the correct pattern — already done for `BomEventPublisher`.

**Alternatives considered**:
- OpenFeign REST client from `inventory-service` to `engineering-service`: rejected — introduces synchronous latency and hard runtime dependency; ECO linkage is not time-critical
- Shared database query: rejected — violates §VIII "no shared schema queries across service boundaries"

**Implementation note**: `bom.released` event payload must include `bomId`, `orgId`, `ecoId` (nullable). `engineering-service` consumer checks `ecoId != null` before calling `addOutputBom`. Idempotent: `addOutputBom` uses upsert (`INSERT ... ON CONFLICT DO NOTHING` or equivalent).

---

## D2 — Schema strategy for migrated tables

**Decision**: Each new service creates its own named PostgreSQL schema (`inventory`, `engineering`) starting from Flyway V001. No cross-schema queries.

**Rationale**: The Constitution mandates one schema per service. The existing `work_order` schema stays intact for backward compatibility during the migration window; tables are not physically moved (PostgreSQL ALTER TABLE SET SCHEMA could be used but risks disrupting existing Flyway history). New services re-create tables via their own Flyway scripts. For UDF and grid preference data: a one-time seed/migration script copies development data.

**Alternatives considered**:
- Rename existing tables with `ALTER TABLE ... SET SCHEMA`: rejected — breaks `work-order-service` Flyway migration history and requires coordinated cutover
- Shared schema with service-level schema prefix: rejected — violates Constitution isolation principle

**Schema assignments**:
| Service | Schema | Tables owned |
|---|---|---|
| `inventory-service` | `inventory` | `item_master`, `bill_of_materials`, `bom_line`, `udf_field_definition` (ITEM_MASTER/BOM keys), `revinfo`, audit tables |
| `engineering-service` | `engineering` | `engineering_change_order`, `eco_affected_item`, `eco_output_bom`, `udf_field_definition` (ECO key), `revinfo`, audit tables |
| `platform-service` | `platform` | `user_grid_preferences` (added as new migration) |
| `work-order-service` | `work_order` | Retains existing tables (not deleted yet); future: `work_order` table for actual Work Orders |

---

## D3 — UDF ownership after split

**Decision**: UDF is a per-service embedded library feature, not a shared service. Each service that uses UDF embeds `mes-udf-lib` and registers its own module keys in its own schema.

**Rationale**: `mes-udf-lib` is already a Gradle shared library with `UdfAutoConfiguration`. Each service registers its `UdfFieldDefinition` rows for its own module keys on startup. There is no need for a central UDF service — UDF is a domain-specific metadata concern.

**Module key assignments after split**:
- `inventory-service`: `ITEM_MASTER`, `BOM_LINE`, `BOM_HEADER`
- `engineering-service`: `ECO`
- `work-order-service` (future): `WORK_ORDER`

**UDF data migration**: Development seed data for ITEM_MASTER/BOM/ECO fields can be re-seeded via `V007__seed_udf_fields.sql` in each new service. No critical production UDF data to preserve.

---

## D4 — UserGridPreference placement

**Decision**: Migrate `UserGridPreference` to `platform-service` under `platform` schema.

**Rationale**: Grid preferences are a cross-domain user personalisation concern, not specific to inventory or work-orders. `platform-service` already owns multi-org framework and system config. Preference endpoint: `GET/PUT /api/v1/users/preferences/grid/{module}`.

**Gateway routing**: Add `Path=/api/v1/users/**` → `platform-service` before the residual `work-order-service` route.

**Migration**: `platform-service` adds `V00X__create_user_grid_preferences.sql` creating the same table structure as currently in `work_order` schema. Dev data is trivially re-created by user interaction (column pickers auto-save).

---

## D5 — Port assignments for new services

**Decision**:
- `inventory-service`: port `8096`
- `engineering-service`: port `8097`

**Rationale**: Follows existing port sequence (`platform-service=8090`, `audit-service=8091`, `work-order-service=8095`). No conflicts with existing allocations.

---

## D6 — Gateway cut-over order

**Decision**: Add new service routes BEFORE the residual `work-order-service` route. Spring Cloud Gateway matches routes in declaration order.

**Cut-over sequence**:
1. Deploy `inventory-service` and `engineering-service`
2. Add their routes to gateway config (above the `work-order-service` catch-all)
3. Verify new routes serve traffic correctly
4. Remove migrated controllers from `work-order-service`
5. Update gateway to either remove `work-order-service` route entirely OR narrow it to `Path=/api/v1/work-orders/**` for future use

**Risk**: During step 2–3, both old and new services receive no traffic for the same paths (gateway routes new service first). This requires deploying new services in a healthy state before updating gateway.

---

## D7 — Envers audit tables in new schemas

**Decision**: Each new service recreates Envers audit tables (`revinfo`, `*_aud`) in its own schema. The `@Audited` entities migrated from `work-order-service` are unchanged; only the schema prefix changes.

**Rationale**: Envers schema validation requires audit tables to exist in the same schema as the entity tables. Since each service has its own schema, each service creates its own Envers tables.

**Note**: Existing audit history in `work_order` schema is NOT migrated — it is preserved for compliance but not accessible via the new services. The existing `work-order-service` instance retains full audit access to `work_order.*_aud` tables.

---

## D8 — Existing integration test strategy

**Decision**: Each new service creates its own `BaseIntegrationTest` with Testcontainers PostgreSQL + Kafka, following the pattern in `work-order-service`. Existing test classes (`ItemMasterControllerIT`, `BomControllerIT`, `EcoControllerIT`) are moved to the appropriate new service and updated for the new package names and schema.

**Package root assignments**:
- `inventory-service`: `com.mes.inventory`
- `engineering-service`: `com.mes.engineering`

---

## D9 — Kafka topic ownership

**Decision**: Kafka topics retain the same names (`item-master.created`, `item-master.obsoleted`, `bom.released`, `eco.approved`). Publishers move to the owning service:

| Topic | Publisher (new) | Consumer (new) |
|---|---|---|
| `item-master.created` | `inventory-service` | TBD (future receiving-service) |
| `item-master.obsoleted` | `inventory-service` | TBD |
| `bom.released` | `inventory-service` | `engineering-service` (new — adds output BOM to ECO) |
| `eco.approved` | `engineering-service` | TBD (future workflow triggers) |

**Rationale**: Topic names are public contracts between services; renaming would require coordinating all consumers. Keeping names unchanged minimises migration blast radius.
