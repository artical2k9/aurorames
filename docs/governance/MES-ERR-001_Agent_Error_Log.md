# MES Agent Error Log — Live

> **Purpose:** Non-promoted errors only. When an error is promoted (CLAUDE.md rule added + archive entry + index row), move it out of this file and mark the promotion date.
>
> **Promotion gate:** An error is ready to promote when: (a) the root cause is fully understood, (b) the fix has been applied and verified, and (c) a CLAUDE.md rule or memory entry would prevent recurrence.
>
> **Format per entry:**
> ```
> ## ERR-MES-NNN — <short title>
> **Date:** YYYY-MM-DD  **Category:** <category>  **Status:** Open | Promoted YYYY-MM-DD
> **Symptom:** What the agent observed.
> **Root cause:** Why it happened.
> **Fix applied:** What was done to resolve it.
> **Rule:** The rule that prevents recurrence (copied to CLAUDE.md or memory on promotion).
> ```

<!-- Add new errors below this line. Oldest at the top, newest at the bottom. -->

## ERR-MES-020 — `gradlew` missing execute bit breaks Linux CI
**Date:** 2026-05-21  **Category:** CI — Permissions  **Status:** Open
**Symptom:** Both Java and SonarCloud CI jobs failed immediately with `Permission denied` (exit 126) on `./gradlew` on ubuntu-latest runner.
**Root cause:** `gradlew` was committed from Windows where the POSIX execute bit is not tracked by the filesystem. Git stored the file as mode `100644` instead of `100755`. Linux runners cannot execute it.
**Fix applied:** `git update-index --chmod=+x gradlew` — sets the execute bit in the Git index without changing file content. Committed and pushed; CI re-ran and passed.
**Rule:** After scaffolding or copying a Gradle project on Windows, always run `git update-index --chmod=+x gradlew` before pushing. Verify with `git ls-files --stage gradlew` — must show `100755`.

## ERR-MES-021 — Branch protection requires GitHub Pro on private repos
**Date:** 2026-05-21  **Category:** CI — GitHub  **Status:** Open
**Symptom:** `gh api repos/.../branches/Develop/protection --method PUT` returned HTTP 403: "Upgrade to GitHub Pro or make this repository public to enable this feature."
**Root cause:** GitHub's branch protection rules API (required status checks, enforce_admins) is gated behind GitHub Pro for private repositories. The free plan does not support this via API or UI.
**Fix applied:** Not resolvable without plan upgrade. Documented as a known gap. Options: (a) upgrade to GitHub Pro, (b) make the repo public, (c) rely on convention + CI visibility without enforcement.
**Rule:** Branch protection rules cannot be set programmatically on private repos under GitHub Free. Do not attempt `gh api .../branches/.../protection` — it will 403. Note the limitation in the PR and raise it with the repo owner.

## ERR-MES-022 — Keycloak 25+ ROPC fails on new realms without `setDirectGrantFlow`
**Date:** 2026-05-21  **Category:** Testing — Keycloak  **Status:** Open
**Symptom:** `KeycloakSupportTest.createUser_andFetchToken_returnsJwt()` failed with `NullPointerException: Cannot invoke "JsonNode.asText()" because the return value of "JsonNode.get(String)" is null`. All integration tests that use `fetchToken()` with ROPC fail the same way.
**Root cause:** Keycloak 25+ ships a new realm with the "direct grant" flow disabled by default. Creating a realm via the Admin API without calling `realm.setDirectGrantFlow("direct grant")` means the ROPC token endpoint returns `{"error":"access_denied"}` with no `access_token` field. `JsonNode.get("access_token")` returns null → NPE.
**Fix applied:** (1) Added `realm.setDirectGrantFlow("direct grant")` to realm creation. (2) Switched from `setCredentials()` in create body to `resetPassword(temporary=false)` after creation — this both sets the password and removes `UPDATE_PASSWORD` from required actions. (3) Added `email`, `emailVerified=true`, `firstName`, `lastName` to test users — Keycloak 26 (used by `dasniko/testcontainers-keycloak:3.4.0`) enables User Profile by default; users missing required profile fields get `UPDATE_PROFILE` as a required action, which blocks ROPC even after `resetPassword()`. (4) Added null-check in every `fetchToken()` with the raw response body in the exception message.
**Rule:** When creating test users for Keycloak 26+ ROPC: (a) `realm.setDirectGrantFlow("direct grant")` on realm create; (b) `user.setEmail/setEmailVerified(true)/setFirstName/setLastName` — User Profile is on by default in KC 26 and requires these fields; (c) call `resetPassword(cred)` with `temporary=false` after creating the user instead of embedding credentials in the create body; (d) null-check `access_token` in `fetchToken()` and include the raw Keycloak response in the error message for diagnostics.

## ERR-MES-023 — Hibernate Envers rejects audited relation to non-audited entity
**Date:** 2026-05-21  **Category:** Backend — Hibernate Envers  **Status:** Open
**Symptom:** All integration tests that load the Spring ApplicationContext failed with `EnversMappingException: An audited relation from com.mikemes.iam.domain.RolePrivilegeAssignment.privilege to a not audited entity com.mikemes.iam.domain.Privilege! Such a mapping is possible but requires using @Audited(targetAuditMode = NOT_AUDITED).`
**Root cause:** `RolePrivilegeAssignment` is annotated `@Audited` at the class level. Hibernate Envers inherits this audit configuration to all `@ManyToOne` relations, including `privilege`. Because `Privilege` has no `@Audited` annotation, Envers rejects the mapping at startup, preventing the entire ApplicationContext from loading.
**Fix applied:** Added `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)` to the `privilege` field in `RolePrivilegeAssignment`. This tells Envers to record only the FK reference (ID) in the audit table without requiring `Privilege` to be audited itself.
**Rule:** When a class is `@Audited` and has a `@ManyToOne` relation to an entity that is NOT `@Audited`, annotate that field with `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`. Without this, Envers fails at startup with `EnversMappingException` — not at query time.

## ERR-MES-019 — ESLint flat config rejects `processor: angular.processInlineTemplates`
**Date:** 2026-05-20  **Category:** Frontend — ESLint  **Status:** Promoted 2026-05-20
**Symptom:** `ng lint` failed: `Config (unnamed): Key "processor": Expected an object or a string.` when `processor: angular.processInlineTemplates` was set in `eslint.config.js`.
**Root cause:** ESLint v9 flat config requires the `processor` field to be either a registered string (`"plugin/name"`) or a plain object with `preprocess`/`postprocess` methods. `angular.processInlineTemplates` as exported by `@angular-eslint/eslint-plugin` v21 is neither — ESLint rejects it.
**Fix applied:** Removed the `processor` line entirely. All project components use `templateUrl`, so inline template extraction is not needed; external `.html` files are linted in the separate HTML config block.
**Rule:** In Angular ESLint flat config, do not set `processor: angular.processInlineTemplates` — it is rejected. The inline template processor is only needed for components with inline `template:` strings; if all components use `templateUrl`, omit the processor entirely.
