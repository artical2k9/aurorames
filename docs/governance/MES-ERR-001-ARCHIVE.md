# MES Agent Error Log — Archive

> Full RCA for all promoted errors. Index is in `MES-ERR-001_Index.md`.

---

## ERR-MES-001 — `cd` in Bash tool triggers safety gate
**Date:** 2026-05-20  **Category:** Shell — Bash tool  **Status:** Promoted 2026-05-20

**Symptom:** `cd "C:\...\MikeMES" && ls` was blocked by the safety hook with message "Compound command contains cd with output redirection".

**Root cause:** The safety hook fires on any `cd + &&` or `cd + redirection` pattern in a Bash tool call, regardless of intent, to prevent path-traversal bypasses.

**Fix applied:** Replaced all `cd + command` patterns with direct tool calls: Glob for listing, Read for file contents, absolute paths for Bash commands.

**Rule:** Never use `cd "path" && <command>` in the Bash tool. The working directory is already the project root (`C:\Users\mike_\Documents\GitHub\MikeMES`). Use Glob for listing, Read for file contents, and absolute paths or tool-native parameters for everything else. See also: `feedback-no-cd-in-bash.md`.

---

## ERR-MES-002 — `gradle` not in PATH; host `java -version` shows wrong JDK
**Date:** 2026-05-20  **Category:** Build  **Status:** Promoted 2026-05-20

**Symptom:** Running `gradle -version` failed with "command not found". Running `java -version` showed Java 25 (host JVM), not the toolchain JVM (Java 21) specified in `build.gradle`.

**Root cause:** `gradle` standalone is not installed; the project uses the Gradle wrapper (`./gradlew`). The host JVM is unrelated to the toolchain JVM that Gradle downloads and uses for compilation and tests.

**Fix applied:** All subsequent build commands use `./gradlew`. Host `java -version` output is ignored for project version questions.

**Rule:** Always use `./gradlew <task>` — never `gradle <task>`. Do not interpret `java -version` on the host as the project's Java version; the toolchain is declared in `build.gradle` and managed by Gradle.

---

## ERR-MES-003 — `Map<?, ?>` breaks `containsKey(String)` in integration test
**Date:** 2026-05-20  **Category:** Java — generics  **Status:** Promoted 2026-05-20

**Symptom:** Compile error in `PrivilegeControllerIT`: `error: no suitable method found for containsKey(String)` — type `CAP#1 extends Object` is not assignable from `String`.

**Root cause:** `Map<?, ?>` uses wildcard capture types. The compiler cannot prove that `String` satisfies `CAP#1 extends Object`, so `containsKey(String)` is rejected even though it would work at runtime.

**Fix applied:** Cast `response.getBody()` to the concrete type `Map<String, List<String>>` with `@SuppressWarnings("unchecked")` and `Objects.requireNonNull()`:
```java
@SuppressWarnings("unchecked")
Map<String, List<String>> body =
    (Map<String, List<String>>) Objects.requireNonNull(response.getBody());
assertThat(body).containsKey("ADMIN");
```

**Rule:** When extracting a typed body from `ResponseEntity<?>`, always cast to the concrete parameterised type. Never use wildcard types (`Map<?, ?>`) when you need to call typed methods. Pair the cast with `@SuppressWarnings("unchecked")` and `Objects.requireNonNull()` (satisfies SpotBugs `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`). See also: `feedback-unchecked-cast-pattern.md`.

---

## ERR-MES-004 — PowerShell syntax in Bash tool call
**Date:** 2026-05-20  **Category:** Shell — tool mix  **Status:** Promoted 2026-05-20

**Symptom:** Bash tool call containing `$null` or `| Select-String` failed with "ambiguous redirect" or "command not found".

**Root cause:** The Bash tool runs under a POSIX shell, not PowerShell. PowerShell operators are invalid POSIX syntax.

**Fix applied:** Replaced shell calls with dedicated tools: Grep for content search, Glob for file search, Read for file content, PowerShell tool for PS-specific syntax.

**Rule:** Never mix PowerShell operators (`$null`, `Select-String`, `Get-ChildItem`, pipeline objects) into a Bash tool call. Use: Glob (file search), Grep (content search), Read (file read), PowerShell tool (PS-specific), Bash tool (POSIX-only). See also: `feedback-shell-tool-selection.md`.

---

## ERR-MES-005 — Jira transition IDs differ by issue type (seed from homeweb ERR-20260331-001)
**Date:** (seed)  **Category:** Jira  **Status:** Promoted (seed)

**Symptom:** `transitionJiraIssue` call rejected; transition ID valid for Story was not valid for Bug.

**Root cause:** Jira transition IDs are scoped to workflow + issue type. The same named transition (e.g., "Done") has different IDs for Story vs Bug.

**Fix applied:** Always call `getTransitionsForJiraIssue` for the specific issue before transitioning; extract the ID from the response.

**Rule:** Never reuse a transition ID from memory or a previous issue. Call `getTransitionsForJiraIssue(<issue-key>)` first and use the ID from the response.

---

## ERR-MES-006 — Missing retrospective gate before Jira Done (seed from homeweb ERR-20260510-004)
**Date:** (seed)  **Category:** Jira  **Status:** Promoted (seed)

**Symptom:** Issue moved to Done without running the plan's retrospective gate; post-merge learnings were lost.

**Root cause:** Retrospective gate step was skipped when closing out the issue.

**Fix applied:** Added retrospective gate check to the workflow.

**Rule:** Before transitioning any issue to Done: run the retrospective gate. Log errors to `MES-ERR-001_Agent_Error_Log.md`, promote to archive and index, then close.

---

## ERR-MES-007 — Partial static analysis fix (seed from homeweb ERR-20260405-003)
**Date:** (seed)  **Category:** Static analysis  **Status:** Promoted (seed)

**Symptom:** SpotBugs/Checkstyle violation fixed in one location but same pattern exists elsewhere; build fails on second pass.

**Root cause:** Fixed the surfaced violation without searching for all instances of the same pattern.

**Fix applied:** After fixing any static analysis violation, grep the codebase for the same pattern and fix all instances.

**Rule:** When a static analysis violation surfaces, grep the entire codebase for the same pattern and fix all instances in the same commit. Never close a static analysis task until `./gradlew check` passes with zero violations.

---

## ERR-MES-008 — Sleep-polling CI (seed from homeweb ERR-20260405-004)
**Date:** (seed)  **Category:** CI  **Status:** Promoted (seed)

**Symptom:** Agent issued repeated `sleep + gh run list` calls to poll CI status; burned context and blocked progress.

**Root cause:** No event-driven mechanism was used; agent fell back to polling.

**Fix applied:** Use Monitor tool or `run_in_background` + notification instead.

**Rule:** Never sleep-poll CI. Use the Monitor tool to stream CI output, or use `run_in_background` and wait for the completion notification. Do not issue `sleep` + check loops.

---

## ERR-MES-009 — PR merged before all checks passed (seed from homeweb ERR-20260506-002)
**Date:** (seed)  **Category:** CI  **Status:** Promoted (seed)

**Symptom:** PR merged while a non-required check was still failing; defect slipped to Develop.

**Root cause:** Only required checks were verified; advisory checks were ignored.

**Fix applied:** Inspect all check statuses before marking PR ready.

**Rule:** Before merging any PR, verify that all checks (required and advisory) pass. A failing advisory check is a warning that must be understood and accepted explicitly, not ignored.

---

## ERR-MES-010 — `git checkout <hash> -- file` auto-stages (seed from homeweb ERR-20260409-006)
**Date:** (seed)  **Category:** Git  **Status:** Promoted (seed)

**Symptom:** After `git checkout <hash> -- file`, the file appeared in the staging area unexpectedly.

**Root cause:** `git checkout <tree-ish> -- <path>` always stages the result; it is not a working-tree-only operation.

**Fix applied:** Either committed the staged file immediately or ran `git restore --staged <file>` to unstage.

**Rule:** `git checkout <hash> -- <file>` auto-stages the result. After using it, either commit immediately or explicitly unstage with `git restore --staged <file>`.

---

## ERR-MES-011 — Merge conflict resolution procedure (seed from homeweb ERR-20260417-005)
**Date:** (seed)  **Category:** Git  **Status:** Promoted (seed)

**Symptom:** Merge conflict left partially resolved; subsequent operations failed.

**Root cause:** Conflict markers not fully removed; `git add` not run after resolution.

**Fix applied:** Three-step procedure: resolve all markers → `git add <files>` → `git commit`.

**Rule:** Merge conflict resolution is three steps: (1) resolve all `<<<<<<<`/`=======`/`>>>>>>>` markers, (2) `git add <resolved-files>`, (3) `git commit`. Do not skip step 2 — unstaged resolved files are not included in the merge commit.

---

## ERR-MES-012 — Dirty working tree before branch switch (seed from homeweb ERR-20260510-002)
**Date:** (seed)  **Category:** Git  **Status:** Promoted (seed)

**Symptom:** `git checkout <branch>` failed or silently carried uncommitted changes to the new branch.

**Root cause:** Working tree had uncommitted modifications when branch switch was attempted.

**Fix applied:** Stashed or committed changes before switching.

**Rule:** Always run `git status` before switching branches. If the tree is dirty, either commit (`git commit`) or stash (`git stash`) before switching. Never switch with uncommitted changes.

---

## ERR-MES-013 — Edit tool fails if old_string not verbatim (seed from homeweb ERR-20260417-006)
**Date:** (seed)  **Category:** Edit tool  **Status:** Promoted (seed)

**Symptom:** Edit tool call failed with "old_string not found in file" on a governance markdown file.

**Root cause:** File content had changed (whitespace, line endings, or prior edit) since the agent last read it; the old_string was stale.

**Fix applied:** Re-read the file with the Read tool immediately before the Edit call.

**Rule:** Always use the Read tool immediately before an Edit call on any governance or markdown file. Never assume the file content matches what was read earlier in the session.

---

## ERR-MES-014 — Governance file edited on two branches simultaneously (seed from homeweb ERR-20260510-005)
**Date:** (seed)  **Category:** Governance  **Status:** Promoted (seed)

**Symptom:** Merge conflict in a governance file because two feature branches both modified it.

**Root cause:** Governance files (CLAUDE.md, constitution, error log) are single-writer — only one branch should touch them at a time.

**Fix applied:** Serialised the edits; one branch merged first, then the other was rebased.

**Rule:** Governance files (`CLAUDE.md`, `docs/governance/**`, `specs/**/constitution.md`) are single-writer. Never edit the same governance file on two open branches simultaneously. If two branches need changes to the same governance file, merge the first branch before starting the second.

---

## ERR-MES-015 — `docker compose logs` with container name (seed from homeweb ERR-20260416-004)
**Date:** (seed)  **Category:** Docker  **Status:** Promoted (seed)

**Symptom:** `docker compose logs mikemes-keycloak` returned no output (container name, not service name).

**Root cause:** `docker compose logs` takes the **service name** from `compose-infra.yml`, not the `container_name` value.

**Fix applied:** Used `docker compose logs keycloak` (service name).

**Rule:** `docker compose logs <service>` requires the service name as defined in the compose file (e.g., `keycloak`, `postgres`, `kafka`), not the `container_name` value. Always reference the compose file when unsure.

---

## ERR-MES-016 — New env var added to compose but not `.env.example` (seed from homeweb ERR-20260411-002)
**Date:** (seed)  **Category:** Docker / Config  **Status:** Promoted (seed)

**Symptom:** Developer pulled latest, ran `docker compose up`, and got an "unset mandatory variable" error because `.env.example` had not been updated to document the new var.

**Root cause:** New env var was added to `compose-infra.yml` but the corresponding documentation entry in `.env.example` was omitted.

**Fix applied:** Added the missing entry to `.env.example` in the same commit.

**Rule:** Any new environment variable introduced in a compose file MUST be documented in `.env.example` (with a comment explaining its purpose) in the same PR/commit. These two files are always updated together.

---

## ERR-MES-017 — Pattern-based bug fixed at one site only (seed from homeweb ERR-20260409-007)
**Date:** (seed)  **Category:** Code pattern  **Status:** Promoted (seed)

**Symptom:** Build passed after fixing a bug, but the same bug pattern existed in two other locations. Subsequent PR review caught them.

**Root cause:** After finding a bug, only the reported instance was fixed.

**Fix applied:** Grepped the codebase for all instances of the pattern and fixed them all.

**Rule:** When any bug is fixed, immediately grep the entire codebase for the same pattern and fix all instances in the same commit. Close the task only after `./gradlew check` passes with zero instances remaining.

---

## ERR-MES-018 — WinSCP text mode corrupts binary files (seed from homeweb ERR-20260518-006)
**Date:** (seed)  **Category:** Deployment  **Status:** Promoted (seed)

**Symptom:** PNG transferred via WinSCP rendered as broken image in browser (HTTP 200 but browser `error` event). Diagnosis: PNG magic bytes `89 50 4E 47` were intact but 1-byte `0x0D` was stripped throughout the file.

**Root cause:** WinSCP defaults to Automatic/Text transfer mode, which strips `CR` (`0x0D`) bytes — a Unix line-ending normalisation that corrupts any binary file containing `0x0D`.

**Fix applied:** Changed WinSCP session to Binary mode. Also: when downloading files via GitHub API (base64-encoded), strip embedded newlines before decoding — `base64 -d` on raw API output fails if newlines are present.

**Rule:** WinSCP transfers for MikeMES deployments MUST use Binary mode for all non-plaintext files (images, keystores, compiled artifacts, etc.). Set Transfer → Transfer settings → Transfer mode → Binary. Also: when base64-decoding GitHub API file downloads, strip newlines first: `echo "$content" | tr -d '\n' | base64 -d > outfile`.

---

## ERR-MES-019 — ESLint flat config rejects `processor: angular.processInlineTemplates`
**Date:** 2026-05-20  **Category:** Frontend — ESLint  **Status:** Promoted 2026-05-20

**Symptom:** `ng lint` failed with `Config (unnamed): Key "processor": Expected an object or a string.` when `eslint.config.js` included `processor: angular.processInlineTemplates` inside a `tseslint.config(...)` block.

**Root cause:** ESLint v9 flat config requires the `processor` field to be either a registered string (`"plugin/processor-name"`) or a plain object with `preprocess`/`postprocess` methods. The `processInlineTemplates` export from `@angular-eslint/eslint-plugin` v21 does not conform to either shape — ESLint rejects it at config parse time.

**Fix applied:** Removed the `processor` line entirely from `eslint.config.js`. All components in this project use `templateUrl` (external `.html` files), so there are no inline templates to extract. External HTML files are linted by the separate HTML config block that sets `languageOptions: { parser: angularTemplateParser }`.

**Rule:** In Angular ESLint flat config (`eslint.config.js`), do not set `processor: angular.processInlineTemplates` — ESLint v9 rejects it. The inline template processor is only required when components use the `template:` property (inline templates); if all components use `templateUrl`, omit the processor entirely.

---

## ERR-MES-020 — `gradlew` missing execute bit breaks Linux CI
**Date:** 2026-05-21  **Category:** CI — Permissions  **Status:** Promoted 2026-05-22

**Symptom:** Both Java and SonarCloud CI jobs failed immediately with `Permission denied` (exit 126) on `./gradlew` on ubuntu-latest runner.

**Root cause:** `gradlew` was committed from Windows where the POSIX execute bit is not tracked by the filesystem. Git stored the file as mode `100644` instead of `100755`. Linux runners cannot execute it.

**Fix applied:** `git update-index --chmod=+x gradlew` — sets the execute bit in the Git index without changing file content. Committed and pushed; CI re-ran and passed.

**Rule:** After scaffolding or copying a Gradle project on Windows, always run `git update-index --chmod=+x gradlew` before pushing. Verify with `git ls-files --stage gradlew` — must show `100755`.

---

## ERR-MES-021 — Branch protection API requires GitHub Pro on private repos
**Date:** 2026-05-21  **Category:** CI — GitHub  **Status:** Promoted 2026-05-22

**Symptom:** `gh api repos/.../branches/Develop/protection --method PUT` returned HTTP 403: "Upgrade to GitHub Pro or make this repository public to enable this feature."

**Root cause:** GitHub's branch protection rules API (required status checks, enforce_admins) is gated behind GitHub Pro for private repositories. The free plan does not support this via API or UI.

**Fix applied:** Documented as a known gap. Options: (a) upgrade to GitHub Pro, (b) make the repo public, (c) rely on convention + CI visibility without enforcement.

**Rule:** Branch protection rules cannot be set programmatically on private repos under GitHub Free. Do not attempt `gh api .../branches/.../protection` — it will 403. Note the limitation in the PR and raise it with the repo owner.

---

## ERR-MES-022 — Keycloak 25+ ROPC fails on new realms without `setDirectGrantFlow`
**Date:** 2026-05-21  **Category:** Testing — Keycloak  **Status:** Promoted 2026-05-22

**Symptom:** `KeycloakSupportTest.createUser_andFetchToken_returnsJwt()` failed with `NullPointerException: Cannot invoke "JsonNode.asText()" because the return value of "JsonNode.get(String)" is null`. All integration tests that use `fetchToken()` with ROPC failed the same way.

**Root cause:** Keycloak 25+ ships a new realm with the "direct grant" flow disabled by default. Creating a realm via the Admin API without calling `realm.setDirectGrantFlow("direct grant")` means the ROPC token endpoint returns `{"error":"access_denied"}` with no `access_token` field.

**Fix applied:** (1) Added `realm.setDirectGrantFlow("direct grant")` to realm creation. (2) Switched from `setCredentials()` in create body to `resetPassword(temporary=false)` after creation. (3) Added full user profile fields (`email`, `emailVerified=true`, `firstName`, `lastName`) — KC 26 User Profile is on by default and blocks ROPC without them. (4) Added null-check in `fetchToken()` with raw response body in the exception message.

**Rule:** When creating test users for Keycloak 26+ ROPC: (a) `realm.setDirectGrantFlow("direct grant")` on realm create; (b) set `email`/`emailVerified(true)`/`firstName`/`lastName` — User Profile is on by default in KC 26; (c) call `resetPassword(cred)` with `temporary=false` after creating the user; (d) null-check `access_token` in `fetchToken()` and include the raw Keycloak response in the error message.

---

## ERR-MES-023 — Hibernate Envers rejects audited relation to non-audited entity
**Date:** 2026-05-21  **Category:** Backend — Hibernate Envers  **Status:** Promoted 2026-05-22

**Symptom:** All integration tests that load the Spring ApplicationContext failed with `EnversMappingException: An audited relation from RolePrivilegeAssignment.privilege to a not audited entity Privilege! Such a mapping is possible but requires using @Audited(targetAuditMode = NOT_AUDITED).`

**Root cause:** `RolePrivilegeAssignment` is `@Audited` at class level. Envers inherits this to all `@ManyToOne` relations including `privilege`. Because `Privilege` has no `@Audited` annotation, Envers rejects the mapping at startup.

**Fix applied:** Added `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)` to the `privilege` field in `RolePrivilegeAssignment`.

**Rule:** When a class is `@Audited` and has a `@ManyToOne` to a non-`@Audited` entity, annotate that field with `@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)`. Without this, Envers fails at startup with `EnversMappingException` — not at query time.

---

## ERR-MES-024 — KC admin calls made without response-code assertion; mapper registration failed silently
**Date:** 2026-05-21  **Category:** Testing — Keycloak  **Status:** Promoted 2026-05-22

**Symptom:** IT tests returned 401 UNAUTHORIZED for 9+ CI runs after switching to `oidc-hardcoded-claim-mapper`. `org_id` was never injected into any JWT. No error appeared in test output or Spring logs.

**Root cause:** `r2.getStatus()` was never called after `createMapper(orgMapper)`. KC25 returned a non-201 response silently. `r2.close()` released the connection without surfacing the error. The test proceeded as if the mapper existed; KC never injected `org_id`.

**Fix applied:** Abandoned KC-based JWT issuance for IT tests entirely. Replaced with locally-signed RSA JWTs (`RSAKeyGenerator` + `SignedJWT` + `RSASSASigner`) and a `@TestConfiguration NimbusJwtDecoder` bean backed by the local RSA public key.

**Rule:** (a) Every KC Admin API call that creates a resource returns a JAX-RS `Response` — always assert `r.getStatus() == 201` before `r.close()`. (b) In IT tests, never rely on KC to issue JWTs — use locally-signed RSA JWTs. (c) `@TestConfiguration` JWT decoder bean must have `@Primary`. (d) Never set `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `""` to suppress auto-configuration.

---

## ERR-MES-025 — Agent applied known pattern without flagging KC25 breaking-change uncertainty
**Date:** 2026-05-21  **Category:** Agent — Confidence Calibration  **Status:** Promoted 2026-05-22

**Symptom:** Agent applied KC protocol mapper approach across 9 CI iterations without flagging that KC25's User Profile enforcement was a known niche breaking change. Fix required (locally-signed RSA JWTs) was known from iteration 1 but only reached in iteration 9.

**Root cause:** Agent treated KC25 as sufficiently known because it falls within training window. Training data depth on niche breaking changes (User Profile `unmanagedAttributePolicy`, mapper registration failure modes) was shallow. No uncertainty flag was raised.

**Fix applied:** Corrected on iteration 9 with locally-signed RSA JWT approach.

**Rule:** (a) When implementing against an external service where the feature involves a documented breaking change, STOP and explicitly flag: "This relies on [X] behavior in version [Y]. My training data on this specific change may be shallow." Do not spend CI runs discovering what a 2-minute local smoke test would reveal. (b) When using framework features with version-sensitive ordering guarantees (e.g. `@ConditionalOnMissingBean`), research the exact version's behavior before committing.

---

## ERR-MES-026 — LocalPrivilegeCache: lazy-loaded Privilege throws outside Hibernate session
**Date:** 2026-05-21  **Category:** Backend — JPA / Security  **Status:** Promoted 2026-05-22

**Symptom:** All ADMIN-token IT tests returned 5xx while VIEWER-token tests returned 403 as expected.

**Root cause:** `LocalPrivilegeCache.getPrivilegesForRole()` had no `@Transactional`. After `findActiveByRoleId()` returned, the Hibernate session was closed. The stream then called `assignment.getPrivilege().getPrivilegeKey()` on a `LAZY` relation outside the session, causing `LazyInitializationException`. VIEWER had zero assignments so the stream was empty; no lazy access, no exception.

**Fix applied:** Added `@Transactional(readOnly = true)` to `getPrivilegesForRole()` and `JOIN FETCH a.privilege` to `findActiveByRoleId()`.

**Rule:** Any method that traverses lazy-loaded JPA relations must be `@Transactional(readOnly = true)`. A method that returns an empty collection never triggers lazy loading — do not let passing no-data tests give false confidence. Use `JOIN FETCH` in the query to eliminate the N+1 and session boundary issue together.

---

## ERR-MES-027 — Hibernate 6.5 JPQL implicit join on lazy `@ManyToOne.id` returns 0 rows
**Date:** 2026-05-22  **Category:** Backend — JPA / Hibernate  **Status:** Promoted 2026-05-22

**Symptom:** After the ERR-MES-026 fix, all `@RequiresPrivilege`-gated endpoints still returned 403. `findAllActive()` returned correct data; only `findActiveByRoleId(UUID roleId)` returned empty.

**Root cause:** The JPQL `WHERE a.role.id = :roleId` navigates through a lazy `@ManyToOne role` association in Hibernate 6.5. This implicit navigation does not optimize to a direct FK column comparison and returns 0 rows even when data exists.

**Fix applied:** Replaced with `findPrivilegeKeysByRoleName` using explicit `JOIN a.role r JOIN a.privilege p WHERE r.name = :roleName` projecting String scalars directly.

**Rule:** In JPQL WHERE clauses, never navigate through a lazy `@ManyToOne` to access the target's `id` (e.g. `a.role.id = :roleId`). In Hibernate 6.x this implicit join is unreliable. Use explicit `JOIN a.role r WHERE r.id = :roleId`, or add a read-only scalar `@Column(name="role_id", insertable=false, updatable=false) UUID roleId` and filter on that.

---

## ERR-MES-028 — `@Valid` on `@RequestBody` silently no-ops without `spring-boot-starter-validation`
**Date:** 2026-05-22  **Category:** Backend — Validation  **Status:** Promoted 2026-05-22

**Symptom:** `registerPrivileges_invalidKeyFormat_returns400()` returned 204 NO_CONTENT instead of 400. The `@Pattern` constraint was never triggered; the service persisted the invalid key.

**Root cause:** `spring-boot-starter-web` includes `jakarta.validation-api` (spec) but NOT Hibernate Validator (implementation). Without `spring-boot-starter-validation`, Spring MVC silently skips `@Valid` processing on `@RequestBody`.

**Fix applied:** Added `implementation 'org.springframework.boot:spring-boot-starter-validation'` to `services/iam-service/build.gradle`.

**Rule:** `@Valid` on `@RequestBody` in a Spring Boot REST controller requires `spring-boot-starter-validation` (Hibernate Validator) explicitly declared. `spring-boot-starter-web` alone is not sufficient. Symptom: validation tests return the success code instead of 400 with no error logged.

---

## ERR-MES-029 — Explicit JPQL JOIN on lazy `@ManyToOne` also returns 0 rows in Hibernate 6.5
**Date:** 2026-05-22  **Category:** Backend — JPA / Hibernate  **Status:** Promoted 2026-05-22

**Symptom:** After the ERR-MES-027 fix (explicit `JOIN a.role r WHERE r.name = :roleName`), all `@RequiresPrivilege`-gated endpoints still returned 403.

**Root cause:** The Hibernate 6.5 behavior described in ERR-MES-027 applies to BOTH implicit navigation (`a.role.id = :roleId`) AND explicit JPQL joins (`JOIN a.role r WHERE r.name = :roleName`) on `@Audited` entities. The common factor is any parameterised WHERE filter navigating a lazy association. `findAllActive()` (no association navigation) is unaffected.

**Fix applied:** Replaced with `findAllActive()` + Java stream filter — the same pattern used in `PrivilegeService.getPrivilegeMap()` which is proven to work.

**Rule:** In Hibernate 6.5, any JPQL query on an `@Audited` entity that navigates a lazy `@ManyToOne` in WHERE — whether implicitly or via explicit JOIN — is unreliable. Use `findAllActive()` (no association navigation in WHERE) and filter in Java inside `@Transactional(readOnly = true)`.

---

## ERR-MES-030 — LocalPrivilegeCache lazy loading silently returns empty in Spring Security filter context
**Date:** 2026-05-22  **Category:** Backend — JPA / Security  **Status:** Promoted 2026-05-22

**Symptom:** After the ERR-MES-029 fix (`findAllActive()` + Java stream filter), all `@RequiresPrivilege`-gated endpoints still returned 403.

**Root cause:** `LocalPrivilegeCache.getPrivilegesForRole()` runs in the Spring Security filter chain — before `OpenEntityManagerInViewInterceptor` (OEMIV) binds an EntityManager. `@Transactional(readOnly = true)` should open a session, but lazy loading of `a.getRole().getName()` in the stream (after `findAllActive()` returns) fails silently in this context: no exception, empty set returned. `PrivilegeService.getPrivilegeMap()` works because it runs inside the web request after OEMIV has bound an EntityManager.

**Fix applied:** Replaced `RolePrivilegeRepository` + JPQL + lazy loading with `JdbcTemplate` + native SQL projecting scalar strings directly. No entity loading, no lazy associations, no Hibernate/Envers involvement.

**Rule:** Any `PrivilegeCache.getPrivilegesForRole()` called from the Spring Security filter chain must use `JdbcTemplate` with native SQL. JPQL + lazy entity traversal in security filter–invoked beans fails silently before OEMIV, even with `@Transactional`.

---

## ERR-MES-031 — Hibernate Envers calls `nextval('revinfo_seq')` without schema prefix
**Date:** 2026-05-22  **Category:** Backend — Hibernate Envers  **Status:** Promoted 2026-05-22

**Symptom:** `grantPrivilege` returned 500 with `ERROR: relation "revinfo_seq" does not exist`. The grant itself succeeded but the transaction rolled back at commit because Envers could not generate a revision ID.

**Root cause:** `application.yml` set `org.hibernate.envers.default_schema: iam` (Envers-specific) but NOT `hibernate.default_schema: iam` (Hibernate-wide). Without `hibernate.default_schema`, Hibernate generates `SELECT nextval('revinfo_seq')` with no schema prefix. The sequence was created as `iam.revinfo_seq` so PostgreSQL returns "relation does not exist".

**Fix applied:** Added `hibernate.default_schema: iam` under `spring.jpa.properties` in `application.yml`.

**Rule:** When all entities and migrations live in a non-public schema, set BOTH `spring.jpa.properties.hibernate.default_schema: <schema>` AND `spring.jpa.properties.org.hibernate.envers.default_schema: <schema>`. The Envers-specific property covers audit table names; the Hibernate property covers sequences including the Envers revision sequence.

---

## ERR-MES-032 — Keycloak 24+ unmanaged attributes DISABLED by default breaks `org_id` in tests
**Date:** 2026-05-22  **Category:** Testing — Keycloak  **Status:** Promoted 2026-05-22

**Symptom:** 4 `UserControllerIT` tests failed in CI: `listUsers` returned empty, `getUser`/`setUserRoles`/`deactivateUser` returned 404. `createUser_withAdminToken_returns201` passed.

**Root cause:** Keycloak 24+ defaults `unmanagedAttributePolicy` to `DISABLED`. Custom user attributes not declared in the User Profile schema — including `org_id` — are silently dropped on create and not returned on read, even via the Admin REST API. `searchByAttributes("org_id:...")` returns empty; `getAttributes()` returns null causing `verifyOrgOwnership` to throw 404. Tests that only checked the immediate 201 response body passed because that response doesn't require attribute access.

**Fix applied:** Added `enableUnmanagedAttributes(Keycloak)` helper to `UserControllerIT.setupKeycloak()` calling `users().userProfile().update(config)` with `UnmanagedAttributePolicy.ENABLED` after realm creation. Made `createRealm` idempotent. Created memory file `feedback-keycloak24-unmanaged-attributes.md`.

**Rule:** When creating a Keycloak realm programmatically in tests (KC 24+), always call `users().userProfile().update(config)` with `UnmanagedAttributePolicy.ENABLED` if any code sets custom user attributes via `user.setAttributes(...)`. Without this, attributes are silently dropped — user creation returns 201 but subsequent lookups by attribute or ID return 404 or empty. See memory `[[feedback-keycloak24-unmanaged-attributes]]`.

---

## ERR-MES-033 — `sonar-project.properties` lists non-existent module paths — SonarCloud exits code 3
**Date:** 2026-05-22  **Category:** CI — SonarCloud  **Status:** Promoted 2026-05-22

**Symptom:** SonarCloud analysis failed with `ERROR Invalid value of sonar.tests` and `The folder 'services/admin-service/src/test/java' does not exist`. Exit code 3.

**Root cause:** `sonar-project.properties` was written speculatively with all 18 planned services and 5 libs listed under `sonar.sources` and `sonar.tests`. Only 3 modules had been scaffolded. SonarScanner exits immediately with code 3 on the first missing path.

**Fix applied:** Removed all non-existent paths, keeping only the three existing modules. Added a Pre-PR Checklist rule to `CLAUDE.md`.

**Rule:** `sonar.sources` and `sonar.tests` must only list directories that currently exist on disk. When scaffolding a new `services/*` or `libs/*` module, add its `src/main/java` and `src/test/java` paths to both properties in the same PR. See Pre-PR Checklist in `CLAUDE.md`.

---

## ERR-MES-034 — `jacocoRootReport` fails in Gradle 9.0: missing `jacocoClasspath` and implicit dependency errors
**Date:** 2026-05-22  **Category:** Build — Gradle  **Status:** Promoted 2026-05-22

**Symptom:** CI SonarCloud step failed: (a) `Task ':jacocoRootReport' uses this output of task ':services:iam-service:jacocoTestReport' without declaring an explicit or implicit dependency` (×3 subprojects); (b) `Type 'JacocoReport' property 'jacocoClasspath' doesn't have a configured value`.

**Root cause:** Two issues: (1) `jacoco` plugin was only applied inside `subprojects {}`, not to the root project. The root-level `JacocoReport` task requires `jacocoClasspath` to be auto-configured by the `jacoco` plugin. (2) `executionData.from fileTree(rootDir)` uses `rootDir` as the input root. Gradle 9.0 detects that `jacocoTestReport` tasks write outputs under `rootDir` subdirectories and flags undeclared implicit dependencies — a build error in 9.0 (warning only in 8.x).

**Fix applied:** (1) Added `apply plugin: 'jacoco'` to the root project. (2) Added `dependsOn sub.tasks.named('jacocoTestReport')` inside the `subprojects.each { sub -> sub.plugins.withType(JavaPlugin) { ... } }` block of `jacocoRootReport`.

**Rule:** When registering an aggregate `JacocoReport` task at root project level: (a) apply `jacoco` plugin to the root project (not just in `subprojects`); (b) add `dependsOn sub.tasks.named('jacocoTestReport')` for every Java subproject alongside `dependsOn sub.tasks.named('test')`. Without (a), `jacocoClasspath` is unconfigured. Without (b), Gradle 9.0 rejects the `fileTree(rootDir)` input with an implicit dependency error.
