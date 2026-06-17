# Service ownership map

Which backend service owns a feature — and therefore which one to **rebuild/restart** when a change or fix touches it. Services are bounded by ISA-95 functional domain (see [ADR-0001](../architecture/ADR-0001-service-granularity.md) and Constitution §XI); a single service can own several feature modules.

Source of truth: gateway predicates in `services/gateway-service/src/main/resources/application.yml` and service definitions in `docker/compose-infra.yml`. Keep this table in sync when routes change.

| Gateway path prefix(es) | Owning service | Schema | Port | Frontend feature folder(s) |
|---|---|---|---|---|
| `/api/v1/item-master/**`, `/api/v1/boms/**`, `/api/v1/udf/**` | `inventory-service` | `inventory` | 8096 | `item-master/`, `bom/` |
| `/api/v1/ecos/**`, `/api/v1/work-instructions/**` | `engineering-service` | `engineering` | 8097 | `eco/`, `work-instructions/` |
| `/api/v1/routes/**`, `/api/v1/routing/**` | `routing-service` | `routing` | 8100 | `routing/` |
| `/api/v1/inspection-plans/**` | `quality-service` | `quality` | 8099 | `inspection-plans/` |
| `/api/v1/labour/**` | `labour-service` | `labour` | 8098 | `labour/` |
| `/api/v1/work-orders/**` | `work-order-service` | `work_order` | 8095 | _(work orders)_ |
| `/api/v1/users/**` | `platform-service` | `platform` | 8090 | `settings/`, `master-data/` (preferences, UOM) |
| `/api/iam/**` | `iam-service` | `iam` | 8085 | `iam/` (users, roles, privileges) |
| `/api/audit/**` | `audit-service` | `audit` | 8091 | — |
| `/api/admin/**` | `admin-service` | — | 8888 | — |
| `/api/platform/**` | `platform-service` | `platform` | 8090 | — |

Gateway itself: `gateway-service`, port 8082 (all `/api/**` traffic enters here; the Angular dev proxy forwards `/api` → `http://localhost:8082`).

## Rebuild / restart a service after a backend change

```bash
# Rebuild the owning service image and restart only that container:
docker compose -f docker/compose-infra.yml up -d --build <service>

# Example — a Work Instructions fix lives in engineering-service:
docker compose -f docker/compose-infra.yml up -d --build engineering-service

# Verify health (replace with the service's port):
curl -sf http://localhost:8097/actuator/health
```

> Note: rebuilding the *wrong* service is the most common cause of "my fix didn't take." Work Instructions = `engineering-service`, **not** a service named after the feature. Use the table above to confirm the owner before rebuilding.
