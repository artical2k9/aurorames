<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan at:
specs/111-service-decomposition/plan.md
<!-- SPECKIT END -->

## Branching Strategy

These rules are **mandatory** for all work in this repository. Enforce them before taking any action that touches git.

### Branch hierarchy

```
main          ← production releases only (PR from Develop)
  └── Develop ← integration branch for all development work
        └── NNN-<feature-name>  ← all feature/fix/admin work
```

### Rules

1. **All feature branches must be cut from `Develop`**, never from `main`.
   ```
   git checkout Develop && git pull && git checkout -b NNN-my-feature
   ```

2. **All PRs must target `Develop`**, never `main`. Reject or re-target any PR that points at `main` from a feature branch.

3. **`main` is updated only by a release PR from `Develop`**. This is a deliberate, human-initiated action at release time — not part of the normal development cycle.

4. **All changes must go through `Develop` first**, including hotfixes. There are no direct-to-main paths.

5. **Branches must NOT be deleted after a PR is merged** — on GitHub or locally. Merged branches provide an audit trail and build traceability. Never run `git branch -d` or use the GitHub "Delete branch" button after merge.

### Pre-flight check (run before any git operation that creates or targets a branch)

- Confirm current branch with `git branch --show-current`.
- If on `main` → **stop**. Switch to `Develop` or the correct feature branch; never work directly on `main`.
- If on `Develop` → **stop** for feature work. Cut a new feature branch from `Develop` first.
- Feature branches must match the pattern `\d{3,}-.*` (e.g. `001-iam-multi-org-security-keycloak`).

---

## Spec-kit Pre-flight Checklist

Before running any spec-kit workflow command (`/speckit-plan`, `/speckit-tasks`, `/speckit-taskstoissues`, `/speckit-breakdown`), always run these two checks first:

1. **Skill availability**: Check the `<system-reminder>` available-skills list for the skill name before calling `Skill()`. If the skill is not listed, read the skill definition from `.specify/` and execute the instructions directly — do not attempt `Skill()` and recover after the error.

2. **Feature branch**: Run `git branch --show-current` and confirm the result is a feature branch matching the pattern `\d{3,}-.*` (e.g. `001-iam-multi-org-security-keycloak`). If on `main` or `Develop`, cut a new feature branch from `Develop` before proceeding — **never from `main`**. The `setup-plan.ps1` and `setup-tasks.ps1` scripts will exit with an error if run from `main` or `Develop`.

Correct spec-kit workflow order: **spec → feature branch (cut from `Develop`) → plan → tasks → implement**

---

## Agent Error Log

### Session-start (mandatory)

At the start of every session, read `docs/governance/MES-ERR-001_Index.md`. It is ~50 tokens and summarises all promoted lessons. Do not skip this step.

### Retrospective gate (mandatory before closing any issue as Done)

Before transitioning any Jira issue to Done:
1. Review this session's work for new errors or near-misses.
2. For each new error: add an entry to `docs/governance/MES-ERR-001_Agent_Error_Log.md`.
3. Promote any entry that has a clear root cause and fix: move it to `MES-ERR-001-ARCHIVE.md`, add an index row to `MES-ERR-001_Index.md`, and add/update the corresponding CLAUDE.md rule or memory file.
4. Then transition the issue to Done.

---

## Commit Format

Every commit message **must** follow this format exactly — square brackets around `type` are **literal and required**:

```
[type](MES-NNN): short description [TXXX]

Ref: MES-NNN
Task: TXXX
```

**Correct:** `[feat](MES-8): add ItemMaster JPA entity and Flyway migration [T027]`
**Wrong:** `feat(MES-8): add ItemMaster JPA entity and Flyway migration`
**Wrong:** `[feat](MES-8): add ItemMaster JPA entity` (missing task ID)

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`

**Rules:**
- The ticket key **must** appear in the headline — this is what the GitHub for Jira app scans to link commits to issues. Use `Ref: MES-NNN` in the footer (not `Closes`).
- The task ID **must** appear as `[TXXX]` at the end of the headline and as `Task: TXXX` in the footer — this links every commit to a specific row in `tasks.md` for full implementation traceability without requiring Jira sub-issues.
- One task = one commit. If a task requires multiple commits, each still references the same task ID.
- If a commit genuinely covers multiple tasks (trivially related setup steps only), list all: `[T001, T002]` and `Task: T001, T002`.

---

## Pre-PR Checklist — Must Pass Before Raising Any PR

Before calling `gh pr create`, confirm **all** of the following:

- [ ] Commit messages follow `[type](MES-NNN): short description` format exactly
- [ ] Branch name matches pattern `NNN-<feature-name>` (e.g. `001-iam-multi-org-security-keycloak`)
- [ ] PR targets `Develop` — never `main`
- [ ] PR is scoped to a single ticket — no bundled unrelated changes
- [ ] No debug code, `System.out.println`, stray `TODO`, or commented-out code included
- [ ] Security considerations reviewed (Constitution §VII — Keycloak-only auth, no secrets in source)
- [ ] `./gradlew check` passes locally with zero failures (lint + unit tests)
- [ ] Tests written for all new/changed logic — coverage not decreased from baseline
- [ ] If any new `services/*/src/main/java` or `libs/*/src/main/java` directory was created, its paths are added to both `sonar.sources` and `sonar.tests` in `sonar-project.properties`
- [ ] **Deployment steps documented** in the PR description (see below — mandatory for all PRs)
- [ ] **Usage Cost section completed** — run `.\scripts\feature-cost.ps1` and paste output into PR description
- [ ] **Pre-retrospective verification completed:**
  - [ ] Identified relevant categories in `docs/governance/MES-ERR-001_Index.md` for this ticket's scope
  - [ ] Spot-checked each category's lessons against the code written
  - [ ] Any violations logged immediately to `MES-ERR-001_Agent_Error_Log.md`

---

## PR Deployment Steps — Mandatory for All PRs

Every PR **must** include a `## Deployment Steps` section in the description. A PR without deployment steps cannot be merged.

**What to include by change type:**

For **infrastructure / Docker Compose changes:**
- Service restart: `docker compose -f docker/compose-infra.yml restart <service>`
- Environment variable additions: list each var with generation instruction; confirm updated in both `.env` and `.env.example`
- Healthcheck verification: `docker compose -f docker/compose-infra.yml ps`

For **database migration changes (Flyway):**
- List each migration file added (e.g. `V3__add_role_privilege_table.sql`)
- Flyway applies automatically on Spring Boot startup — note the expected log line
- Verify: check service startup logs for `Successfully applied N migration(s)`

For **Keycloak realm changes (`keycloak/mes-realm.json`):**
- Re-import via Docker: `docker compose -f docker/compose-infra.yml restart keycloak`
- OR list specific admin console steps if the change is post-import config (e.g. client secret rotation)
- Post-import secret steps: list any secrets that must be set manually after import

For **Spring Boot service changes:**
- List services to restart and the mechanism (Docker container restart or JAR replacement)
- Note any config changes required in `.env`

For **governance / config-only changes:**
- Brief summary of which files changed and why
- How to verify the changes are in effect
- Deployment Steps section is still required — use a "No runtime deployment needed" note with a verify step

**Format:**

```markdown
## Deployment Steps

### Step 1: Restart affected services
\`\`\`bash
docker compose -f docker/compose-infra.yml restart keycloak
\`\`\`

### Step 2: Verify
\`\`\`bash
docker compose -f docker/compose-infra.yml ps   # all services healthy
curl -sf http://localhost:8080/health/ready      # Keycloak ready
\`\`\`
```

---

## Per-PR Spend Reporting — Mandatory

Before raising any PR, run `.\scripts\feature-cost.ps1` from the repo root and paste the output as the `## Usage Cost` section in the PR description.

The script calculates the cost of all Claude Code sessions since the branch diverged from `Develop`.

**Why:** Tracks AI token spend per feature to enable cost visibility by model and ticket.

**Setup:** See [`docs/dev/codeburn-setup.md`](docs/dev/codeburn-setup.md) for the one-time global install.

---

## Angular Change Detection Rules — Mandatory for All New Components

**ERR-MES-059:** Angular dev mode runs change detection twice per tick. Any `.subscribe()` callback that mutates a template-bound property between those two passes will throw `NG0100: ExpressionChangedAfterItHasBeenCheckedError`.

### Rule — applies to every new Angular component that performs HTTP calls

Whenever you write a `.subscribe()` call that assigns to any component property used in the template, you **must**:

1. Import and inject `ChangeDetectorRef`:
   ```typescript
   import { ChangeDetectorRef, ... } from '@angular/core';
   // ...
   private readonly cdr = inject(ChangeDetectorRef);
   ```

2. Call `this.cdr.detectChanges()` as the **last line** of both the `next:` and `error:` callbacks:
   ```typescript
   this.api.load().subscribe({
     next: data => {
       this.items = data;
       this.loading = false;
       this.cdr.detectChanges();   // ← mandatory
     },
     error: () => {
       this.items = [];
       this.loading = false;
       this.cdr.detectChanges();   // ← mandatory
     },
   });
   ```

### Where this applies

| Hook / method | Required? |
|---|---|
| `ngOnInit` subscribe | ✅ Always |
| `constructor` subscribe | ✅ Always |
| `ngAfterViewInit` subscribe | ✅ Always |
| `save()` / `create()` / `delete()` error callback | ✅ Always (sets `serverError`, `saving`) |
| `save()` / `create()` next callback that navigates away | ✅ Still add it before `router.navigate()` |
| `valueChanges` form subscriptions (no property mutation) | ❌ Not needed |

### Pre-PR check for frontend PRs

Before raising any PR that includes Angular component files:
- Grep the PR diff for `.subscribe(` in component files
- Confirm every `next:` and `error:` callback that assigns to `this.xxx` also ends with `this.cdr.detectChanges()`
- If `cdr` is not injected and a subscribe exists → the component is incomplete

---

## Keycloak Protocol Mapper Rules — Mandatory for All New Clients

**ERR-MES-060:** Keycloak 25 stopped auto-including `sub` in access tokens. Every client added to `keycloak/mes-realm.json` **must** include an explicit `sub` mapper in its `protocolMappers` array:

```json
{
  "name": "sub",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-usermodel-property-mapper",
  "consentRequired": false,
  "config": {
    "userinfo.token.claim": "false",
    "user.attribute": "id",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "claim.name": "sub",
    "jsonType.label": "String"
  }
}
```

### Rule — never call `jwt.getSubject()` without a null fallback

`jwt.getSubject()` returns null when the `sub` claim is missing. Any value passed to a `NOT NULL` column will cause a 409; any value passed to `Optional.of()` will NPE.

**Always use the null-safe fallback chain:**

```java
// Option A — use the shared helper (lib-common-security)
new JwtClaimsExtractor(jwt).nullSafeSubject()  // sub → preferred_username → "unknown"

// Option B — inline in controllers that don't use JwtClaimsExtractor
private static String subjectOf(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub != null && !sub.isBlank()) {
        return sub;
    }
    String username = jwt.getClaimAsString("preferred_username");
    return (username != null && !username.isBlank()) ? username : "unknown";
}
```

### Pre-PR check for backend PRs

Before raising any PR that includes Java service changes:
- `grep -r "getSubject()" services/ libs/ --include="*.java"` — every match must use the null-safe pattern above.
- `grep -r "Optional.of(auth.getName())" services/ libs/ --include="*.java"` — no matches allowed; must be `Optional.of(name != null ? name : "system")`.

---

## CI Verification — Mandatory Before Merge

**"CI pipeline GREEN" has a specific meaning — it cannot be approximated.**

Before merging any PR, run:

```bash
gh pr checks <pr-number>
```

Read the **full output**. Every check must show `pass`, `skipped`, or `neutral`. No `pending` or `fail` anywhere.

**SonarCloud dual-check — both must be green:**

| Check name | Source | Authoritative? |
|---|---|---|
| `SonarCloud Code Analysis` | SonarCloud native PR decoration | ✅ Yes — shows quality gate result and issue list |
| `SonarCloud Analysis` | GitHub Actions (`sonarcloud.yml`) | ✅ Yes — blocks on quality gate via `sonar.qualitygate.wait=true` |

Both must be `pass` or `skipped` before merge. If `SonarCloud Code Analysis` shows issues, open sonarcloud.io for the full violation list before writing any fix.

**Never:**
- Grep for absence of "fail" as a substitute for reading every check
- Merge with any check in `pending` state
- Skip a failing advisory check without explicitly understanding and accepting it

**When a SonarCloud quality gate fails:** Enumerate every issue in the report (Vulnerabilities, Security Hotspots, Code Smells, Duplication) before writing any fix. Fix all violations in a single commit — partial fixes waste CI minutes and create PR noise.

---

---

## Keycloak Docker Hostname Rules — Mandatory for All Compose Environments

**ERR-MES-066:** KC without `KC_HOSTNAME` derives the `iss` claim from the incoming `Host` header. Browser tokens carry `localhost:8080`; backend services in Docker access KC at `keycloak:8080`. Spring Security 6.5+ validates the `iss` claim from OIDC discovery **even when only `jwk-set-uri` is configured**. The hostname mismatch causes 401 on every request.

### Rule — always set KC_HOSTNAME in Docker Compose

Every Keycloak service in every compose file (dev, CI, prod) **must** include:

```yaml
KC_HOSTNAME: localhost            # or the public hostname for prod
KC_HOSTNAME_PORT: "8080"          # or the public port
KC_HOSTNAME_STRICT: "false"
KC_HOSTNAME_STRICT_BACKCHANNEL: "false"
```

### Verify after KC restart

```bash
curl -s http://localhost:8080/realms/mes/.well-known/openid-configuration | python3 -m json.tool | grep issuer
# Must print: "issuer": "http://localhost:8080/realms/mes"
```

If `issuer` contains `keycloak:` or any Docker-internal hostname, the KC container is misconfigured.

---

## Auth Fix Verification Standard — Mandatory

**ERR-MES-067:** Verifying an auth fix by decoding a JWT or curling KC directly is **insufficient**. That tests token issuance only. The actual failure path is: Angular → dev proxy → gateway (Docker) → service. The gateway validates the `iss` claim; the service validates `org_id`. Only the full path catches both.

### Rule — after any auth-related change, verify via the full stack

1. Log in through the Angular app (or equivalent `curl` to the gateway dev proxy port **8082**, not directly to KC port 8080).
2. Make the specific API call that was failing (e.g. `GET http://localhost:8082/api/iam/users`).
3. Confirm HTTP 200.

A 200 from the gateway is the only passing criterion. A valid JWT payload is a prerequisite, not a pass.

---

## JWT Issuer Validation in Integration Tests — Mandatory

**ERR-MES-068:** Setting `spring.security.oauth2.resourceserver.jwt.issuer-uri` to empty string in IT overrides disables all issuer validation. This masks KC_HOSTNAME misconfigurations — the test suite passes while every browser request 401s.

### Rule — IT tests must NOT disable issuer validation

Replace:
```java
// WRONG — disables issuer check entirely
registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
```

With:
```java
// CORRECT — point to Testcontainers KC issuer; Spring Security validates normally
registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
    () -> keycloak.getAuthServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/certs");
```

The Testcontainers KC issues tokens with its own issuer URL; the `jwk-set-uri` must match. Do not additionally set `issuer-uri` to a different value — let Spring Security derive it from OIDC discovery (which will match the Testcontainers KC issuer automatically in Spring Boot 3.5+).

### Pre-PR check for backend PRs touching security

Before raising any PR that changes `application.yml` JWT config, `compose-infra.yml` KC environment, or any `*IT.java` that overrides JWT properties:
- Grep `grep -rn "issuer-uri.*\"\"" services/ --include="*.java"` — every match is a test that masks issuer validation; replace per rule above.
- Confirm KC compose entry has `KC_HOSTNAME` set.

---

## `@ConditionalOnMissingBean` in `@Import`-ed Configuration — Mandatory

**ERR-MES-069:** `@ConditionalOnMissingBean` is only reliable in Spring Boot auto-configuration classes (loaded via `AutoConfiguration.imports`). In manually `@Import`-ed `@Configuration` classes it evaluates before `@TestConfiguration` beans are registered, causing the condition to fire incorrectly and crash the `ApplicationContext` in every IT that provides a `@Primary` override.

### Rule — never add `@ConditionalOnMissingBean`-guarded beans to `@Import`-ed classes

If a shared bean (e.g. `JwtDecoder`) needs runtime default behaviour with test-override support:
- **Rely on Spring Boot auto-configuration** — it processes after all user-defined beans and honours `@ConditionalOnMissingBean` reliably.
- **Do not** add the bean to a class loaded via `@Import` (e.g. `MESSecurityAutoConfiguration`).

### Pre-PR check for shared lib changes

Before adding any `@Bean` with `@ConditionalOnMissingBean` to a class that is `@Import`-ed:
- Confirm the class is in `spring.factories` / `AutoConfiguration.imports` (auto-config path).
- If loaded via `@Import`, remove the condition — tests cannot safely override it.

---

## Private Controller Methods with Branch Logic — Mandatory

**ERR-MES-070:** Private static methods in controllers that contain `if`/ternary branches are invisible to test authors — IT tests only exercise the happy path, leaving fallback branches uncovered. This caused a SonarCloud quality gate failure at 73.3% (threshold 80%).

### Rule — delegate branching logic to a tested shared helper

Before writing a private helper method with branching logic in a controller or service:

1. Grep `libs/` for an equivalent utility: `grep -r "nullSafeSubject\|orgId\|subjectOf" libs/ --include="*.java"`
2. If a tested helper exists, delegate to it — it inherits full branch coverage from its own unit tests.
3. If no helper exists, put the new logic in a `final` utility class under `libs/` with dedicated unit tests. Never write it as a private method inside a controller.

### Pre-PR check for new controller code

Before raising any PR that adds private methods to a controller:
- Grep the diff for `private static` or `private` methods with `if`/ternary branches.
- For each match: confirm a unit test covers all branches — either directly or via a shared utility's tests.

---

## New Module Privilege Registration — Mandatory

**ERR-MES-075:** `PrivilegeRegistryClient.register()` calls `PrivilegeService.registerManifest()` which only upserts rows in `iam.privilege`. Before this fix, it never granted privileges to any role — including `SYSTEM_ADMIN`. The result: every new `@RequiresPrivilege` key was catalogued but unreachable by any user until manually granted.

### Fix — `registerManifest()` auto-grants to SYSTEM_ADMIN

As of 2026-06-08, `PrivilegeService.registerManifest()` auto-grants every registered privilege to `SYSTEM_ADMIN` if not already held. No separate deployment step is needed when adding new `@RequiresPrivilege` keys.

**How it works:**
- Loads existing SYSTEM_ADMIN grants once before the item loop (no N+1)
- Idempotent: re-registering an existing key re-grants it if accidentally revoked
- Only SYSTEM_ADMIN is auto-granted; all other roles require explicit assignment via the Role management UI

### Pre-PR check for new services or new @RequiresPrivilege keys

When adding any new `@RequiresPrivilege("x:y:z")` annotation:
1. Confirm the key is included in the module's privilege registration manifest (the `ApplicationReadyEvent` handler or equivalent).
2. No Flyway migration or manual grant is needed for SYSTEM_ADMIN — the auto-grant runs on service startup.
3. If the service is already deployed and the key was added in this PR: restart `iam-service` so `registerManifest()` runs again and auto-grants the new key.

---

# Compact

Retain:
- Active phase/task.
- Completed vs pending requirements.
- Code changes by file.
- Architectural decisions and rationale.
- Open defects, blockers, and rejected approaches.

Discard:
- Logs, diagnostics, tool output.
- Intermediate investigation notes.
- Repeated discussion and superseded plans.
