# ADR-0001: Service granularity — one service per ISA-95 functional domain

- **Status**: Accepted
- **Date**: 2026-06-17
- **Relates to**: Constitution §XI (Service Boundary Integrity); Epic MES-111 (`specs/111-service-decomposition/`)
- **Decision driver**: Recurring question of whether individual feature modules (e.g. Work Instructions) should each become their own microservice.

## Context

The question was raised: *should each module/page be its own microservice — e.g. Work Instructions (WI) extracted from `engineering-service` into a standalone service?* It surfaced after a WI fix required rebuilding `engineering-service`, which was not obviously the owning service.

The codebase already has a governing principle for this. **Constitution §XI — Service Boundary Integrity** states:

> "Each backend microservice MUST own exactly the API routes that correspond to its declared ISA-95 functional domain (Technology Stack table). No service may expose endpoints that belong to another domain."
>
> "**One domain, one service**: the mapping between URL path prefix and service name MUST be 1-to-1."

The unit of decomposition is the **ISA-95 functional domain (bounded context)** — not the individual feature or aggregate. *Manufacturing Engineering* is one domain, and it deliberately owns **both** Engineering Change Orders (ECO) and Work Instructions.

This boundary is the result of a real incident, not theory. Per §XI and Epic **MES-111**, `work-order-service` had become a catch-all hosting `ItemMasterController`, `BomController`, `EcoController`, and `UserGridPreferenceController` behind a single `Path=/api/v1/**` predicate. On **2026-06-02**, Kafka consumer churn in that service starved its HTTP worker threads and took down Item Master and BOM screens that have no logical dependency on work-order processing. The remediation re-aligned services to **domains** (extract into `inventory-service`, `engineering-service`, `platform-service`) — it did **not** split each feature into its own service.

## Decision

**Services are bounded by ISA-95 functional domain. Feature modules live inside their owning domain service. Work Instructions stays in `engineering-service` alongside ECO. Service-per-feature ("nano-services") is rejected.**

## Rationale

- **Fault isolation already exists at the right boundary.** The benefit a split would chase — one feature's failure not affecting another — is already delivered at the domain level. WI problems cannot affect `inventory-service`, `quality-service`, or `routing-service` because those are separate domains/services. Splitting WI from ECO adds no isolation that matters: they are the same domain, same team, same release cadence.
- **Domain boundaries are already loosely coupled; intra-domain modules are not.** Cross-service contact is limited to UUID references, a small number of fail-closed REST calls (e.g. `engineering-service → labour-service` skill-gating, 2 s timeout, fail-closed), and Kafka domain events. There are **no cross-service foreign keys or transactions**. Within `engineering-service`, ECO and WI share the `engineering` schema and audit/transaction infrastructure. Within other services the coupling is tighter still (item-master ← BOM FK; certification → skill FK; routes → reference data) — those modules **cannot** be cleanly split without distributed transactions or a saga.
- **Cost of over-decomposition.** Each new service adds a container, a Postgres schema, a Flyway migration history, a deploy pipeline, Kafka topic wiring, an extra network hop on every cross-call, and new eventual-consistency surface — with no offsetting scaling or isolation gain at today's load.

## Extraction triggers (when a future split *would* be justified)

Splitting WI (or any module) into its own service should be reconsidered only when a concrete trigger appears:

- WI media/PDF rendering or MinIO traffic begins to **contend for resources** with ECO.
- WI and ECO **release cadences diverge** enough that atomic, shared deploys become a drag.
- WI develops a **materially different data-volume, traffic, or scaling profile**.
- A distinct **compliance or data-residency boundary** applies to WI but not ECO.

WI is the **cleanest** candidate if that day comes — it already has an isolated gateway route (`/api/v1/work-instructions/**`), an isolated frontend feature (`frontend/angular/src/app/features/work-instructions/`), its own `WorkInstructionEventPublisher`, and no shared foreign key with ECO. There is therefore **no penalty for deferring**: the split stays easy.

## Consequences

- The stack stays at its current set of ISA-95 domain services; no new service is scaffolded.
- New API endpoints land in the service that owns the corresponding ISA-95 domain by default (per §XI).
- To answer "which service owns / do I rebuild for feature X?", see [`docs/dev/service-ownership-map.md`](../dev/service-ownership-map.md).
