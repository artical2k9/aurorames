# MES Agent Error Log — Archive

> Full RCA for all promoted errors. Index is in `MES-ERR-001_Index.md`.

---

## ERR-MES-074 — IT class delegates buildToken() to sibling class; wrong RSA key signs tokens
**Date:** 2026-06-07  **Category:** Testing — Spring Security / JWT  **Status:** Promoted 2026-06-07

**Symptom:** `UserPasswordControllerIT` and `PublicAuthControllerIT` returned HTTP 401 UNAUTHORIZED on every authenticated request in CI. `resetPassword_differentOrg_returns404()` received 401 instead of 404. The `createUser()` helper in each class received a null body on its `UserResponse` exchange → NPE in 6 downstream tests. All 10 new IT tests failed in CI but passed locally when Testcontainers was skipped (Docker not accessible before api.version fix).

**Root cause:** Each IT class generates its own `static final RSAKey TEST_RSA_KEY` and registers a `@Primary JwtDecoder` bean that validates tokens using that class's public key. Both `UserPasswordControllerIT` and `PublicAuthControllerIT` delegated token generation to `UserControllerIT.buildToken(String role)` — a static method that signs tokens with `UserControllerIT.TEST_RSA_KEY`. The Spring context for each class loads its own `@Primary JwtDecoder` backed by its own `TEST_RSA_KEY`. Token signed with `UserControllerIT.TEST_RSA_KEY` ≠ `UserPasswordControllerIT.TEST_RSA_KEY` → signature verification fails → 401 for every request.

The delegation was invisible at call-site review: `UserControllerIT.buildToken("SYSTEM_ADMIN")` looks like a simple utility call. The implicit RSA key dependency only surfaces at runtime when the `JwtDecoder` rejects the token.

**Fix applied:** Added a local `static String buildToken(String role)` method to both `UserPasswordControllerIT` and `PublicAuthControllerIT`. Each method signs with the class's own `TEST_RSA_KEY`. The cross-class delegation was removed.

**Why the audit missed it:** IT tests were skipped locally (Docker not accessible before the api.version fix was applied). The audit was run as a code review against source files — there was no runtime execution to surface the 401. The key mismatch is a runtime failure, not a static analysis issue.

**Rule:** Every IT class that declares its own `static final RSAKey TEST_RSA_KEY` must define its own `static String buildToken(String role)` that signs with `this` class's key. Never delegate to another IT class's `buildToken()` static method — the calling class's `@Primary JwtDecoder` is backed by its own distinct RSA key, and a cross-class delegation silently signs tokens with the wrong key. The delegation compiles and the call-site looks correct; the failure is invisible until runtime.

---

## ERR-MES-075 — KeycloakTokenClient matched invalid_grant on HTTP status code; KC returns 400 (RFC 6749), not always 401
**Date:** 2026-06-07  **Category:** Backend — Keycloak / HTTP  **Status:** Promoted 2026-06-07

**Symptom:** After the Docker api.version fix made IT tests run locally, `PublicAuthControllerIT` AS1 (valid temp credentials + correct temp password → expected 204) failed with HTTP 400. The test setup correctly configured a user with `UPDATE_PASSWORD` required action and set a temporary password. The service returned 400 (InvalidCredentialsException) instead of 204.

**Root cause:** `KeycloakTokenClient.verifyTemporaryPasswordCredentials()` sent an ROPC token request with the user's credentials. KC returns `{"error":"invalid_grant","error_description":"Account is not fully set up"}` when a user has `UPDATE_PASSWORD` as a required action — meaning the password is correct but KC blocks token issuance until the user changes it. This is the expected signal that distinguishes "correct temporary password" from "wrong password".

The catch clause was `catch (HttpClientErrorException.Unauthorized e)` — which matches only HTTP 401. RFC 6749 §5.2 specifies HTTP 400 for `invalid_grant` errors. The Testcontainers KC version followed the RFC and returned HTTP 400. The `HttpClientErrorException.Unauthorized` catch did not fire; the exception propagated to the parent `HttpClientErrorException` catch which threw `InvalidCredentialsException` → controller returned 400.

Production KC instances on other versions may return either 400 or 401, making status-code matching fragile across KC versions.

**Fix applied:** Changed the catch clause from `HttpClientErrorException.Unauthorized` to `HttpClientErrorException` (the parent, matching all 4xx responses). The discriminator moved from HTTP status to response body: `if (body != null && body.contains("Account is not fully set up")) { return true; }`. Any other 4xx body (wrong password, unknown user, account locked) falls through to `throw new InvalidCredentialsException()`.

**Rule:** When calling the KC token endpoint to verify user credentials, never match KC error responses by HTTP status code alone. `invalid_grant` errors may arrive as HTTP 400 or 401 depending on KC version and configuration. Always catch `HttpClientErrorException` (the Spring parent class for all 4xx errors) and inspect the response body to distinguish error types. Specifically: `body.contains("Account is not fully set up")` is the signal for "correct password, UPDATE_PASSWORD pending"; any other body indicates wrong credentials.

---

## ERR-MES-059 — Angular subscribe callbacks mutating template-bound properties trigger NG0100 across entire app
**Date:** 2026-06-05  **Category:** Frontend — Angular Change Detection  **Status:** Promoted 2026-06-05

**Symptom:** `RuntimeError: NG0100: ExpressionChangedAfterItHasBeenCheckedError` thrown in dev mode on page load and on form-save error paths. Errors surfaced across 14 components in 4 separate fix passes: `bom-browser`, `item-master-list`, `eco-list`, `udf-admin`, `item-master-detail` (UDF + loadItem), `udf-fields`, `bom-list`, `eco-form` (constructor itemOptions + save error), `bom-authoring`, `bom-explosion`, `eco-detail`, `item-master-edit` (loadItem + obsoleteItem + save error), `item-master-create` (clone subscribe + save error), `add-bom-line-form` (search subscribe + save error), `item-master-form` (loadItem + save error).

**Root cause:** Angular dev mode runs change detection twice per tick: a render pass followed by a `checkNoChanges` pass. When an HTTP Observable emits inside the Angular zone, its subscriber callback runs synchronously within the same CD cycle. If the callback mutates a template-bound property (`loading`, `items[]`, `serverError`, `parentItem`, etc.) the mutation lands between the two passes. Angular's second pass detects the discrepancy and throws NG0100. The pattern is latent in every component that subscribes to HTTP without explicitly telling Angular to re-check; it surfaces non-deterministically depending on how quickly the HTTP response arrives relative to the CD cycle.

**Fix applied:** In all 14 components above: injected `ChangeDetectorRef` via `private readonly cdr = inject(ChangeDetectorRef)` and added `this.cdr.detectChanges()` as the final statement in every `next:` and `error:` callback that mutates any template-bound property.

**Rule:** Every Angular component whose `.subscribe()` callback mutates a template-bound property MUST:
1. `import { ChangeDetectorRef } from '@angular/core'`
2. `private readonly cdr = inject(ChangeDetectorRef)` in the class body
3. `this.cdr.detectChanges()` as the last line in both the `next:` and `error:` callbacks

This applies to: page-load subscribes in `ngOnInit` / `constructor` / `ngAfterViewInit`; user-action subscribes in `save()` / `delete()` / `obsolete()` / `load()`; and all shared/child components with HTTP subscriptions. When creating a new component, treat `cdr` injection + `detectChanges()` as part of the mandatory component skeleton whenever HTTP is involved. See CLAUDE.md §Angular Change Detection Rules.

---

## ERR-MES-060 — Keycloak 25 stopped auto-including `sub` in access tokens; jwt.getSubject() returns null
**Date:** 2026-06-05  **Category:** Backend — Keycloak / JWT  **Status:** Promoted 2026-06-05

**Symptom:** `POST /api/v1/ecos` returned 409: `null value in column "initiated_by" violates not-null constraint`. The same request showed `created_by = "system"` — meaning the authenticated user's identity was lost. Analysis identified five additional at-risk call sites across engineering-service, platform-service, iam-service, lib-common-audit, and work-order-service.

**Root cause:** Keycloak 25 changed default behaviour — the `sub` (subject) claim is no longer automatically included in access tokens. An explicit `oidc-usermodel-property-mapper` (property: `id`, claim name: `sub`) must be added to the client or a client scope. After an Option B realm reimport that lacked this mapper, every `jwt.getSubject()` call returned null. Code that passed null directly into `NOT NULL` columns caused 409 Conflict; code using `Optional.of(auth.getName())` without a null guard would NPE with 500 on any JPA write.

**Fix applied:**
1. Added `sub` protocol mapper to `mes-frontend` client via Keycloak admin API (live, no restart needed).
2. Persisted the mapper to `keycloak/mes-realm.json` so it survives future realm reimports.
3. Added `JwtClaimsExtractor.nullSafeSubject()` to lib-common-security — `sub → preferred_username → "unknown"` fallback chain.
4. Hardened `MesRevisionListener.resolveUserId()` in lib-common-audit with the same fallback.
5. Fixed `Optional.of(auth.getName())` NPE in iam-service and platform-service `JpaConfig`.
6. Added `subjectOf(Jwt)` null-safe helper to work-order-service `EcoController`.
7. Fix 5 deferred for engineering-service/platform-service controllers — source is on branch `111-engineering-service-scaffold`.

**Rule:** Every Keycloak client definition in `mes-realm.json` must include an explicit `sub` mapper. Never call `jwt.getSubject()` without a null fallback chain (`sub → preferred_username → "unknown"`). Use `JwtClaimsExtractor.nullSafeSubject()` or inline the same pattern. Add a pre-PR grep check for `getSubject()` in backend diffs. See CLAUDE.md §Keycloak Protocol Mapper Rules.

---

## ERR-MES-057 — Adding columns to @Audited entity without updating _aud table breaks schema-validation
**Date:** 2026-05-31  **Category:** Backend — Hibernate Envers  **Status:** Promoted 2026-05-31

**Symptom:** All 63 integration tests in PR #20 failed at context startup with `BeanCreationException → SchemaManagementException: Schema-validation: missing column [bom_type] in table [work_order.bill_of_materials_aud]`. V013 added `reason_for_revision`, `production_line`, `bom_type`, `effectivity_type`, `custom_fields` to `bill_of_materials` and the entity/DTO, but `bill_of_materials_aud` was never updated.

**Root cause:** `BillOfMaterials` is `@Audited`. At startup, Hibernate Envers validates that the `_aud` shadow table contains every column present on the entity. The V013 migration correctly updated the main table and the Java entity, but the author did not extend the same change to `bill_of_materials_aud`. The pre-PR retrospective spot-check against the Envers category (ERR-MES-023) was skipped.

**Fix applied:** V014 migration — `ALTER TABLE work_order.bill_of_materials_aud ADD COLUMN IF NOT EXISTS ...` for each of the five new fields.

**Rule:** Whenever adding columns to a `@Audited` entity, always include the corresponding `ALTER TABLE <entity>_aud ADD COLUMN ...` in the same migration or an immediately-following one. Envers schema-validation fires at application startup and checks column parity between the main and `_aud` tables. Cross-check this during the pre-PR retrospective via the Envers category in the index.

---

## ERR-MES-058 — Pre-PR retrospective spot-check skipped; known Envers pattern missed until CI
**Date:** 2026-05-31  **Category:** Agent Process  **Status:** Promoted 2026-05-31

**Symptom:** PR #20 failed CI on its first run due to the V014 miss (ERR-MES-057). The CLAUDE.md pre-PR checklist explicitly requires identifying relevant error categories from the index and spot-checking each against the code written before calling `gh pr create`. This step was not performed.

**Root cause:** The retrospective gate was treated as a governance formality to complete after raising the PR rather than as a technical blocking gate. As a result, the Envers category (ERR-MES-023/057) was never cross-referenced against the `@Audited` entity changes in this PR, and the `_aud` table gap was not caught locally.

**Fix applied:** V014 pushed; PR CI re-ran.

**Rule:** The pre-PR retrospective is a technical gate, not a post-hoc documentation step. Before every `gh pr create`: (1) enumerate the error categories touched by this PR's scope (JPA/Envers, Security, Frontend, Build, etc.), (2) read each relevant index entry, (3) confirm the written code does not repeat any listed pattern. A CI failure caused by a lesson already in the error log is a process violation — it means the gate was skipped, not that the error is new.

---

## ERR-MES-055 — tasks.md stale markers cannot distinguish "planned but skipped" from "done but unchecked"
**Date:** 2026-05-31  **Category:** Agent Process  **Status:** Promoted 2026-05-31

**Symptom:** Backend tasks T193–T196 appeared in the PR 2 task range in `tasks.md`. PR 2 was recorded as merged (PR #11/#17) in `HANDOVER.md`. Agent treated the backend dependency as satisfied without verifying the code. At implementation time, `ModuleKey.java` was missing `BOM_LINE`/`BOM_HEADER`, V013 migration did not exist, and several `BomController`/`EcoController` endpoints were absent. All had to be added as backend prerequisites inside PR 7.

**Root cause:** `tasks.md` `[ ]`/`[X]` markers stopped being updated after PR #10. Tasks in a merged PR's declared range may still show `[ ]` (done but not checked off), and tasks that were planned for a PR but silently dropped are also `[ ]`. The file gives no structural signal to distinguish the two states. The HANDOVER.md partially compensated by flagging T193/T194 as "verify before starting", but the broader set of missing endpoints was not called out anywhere.

**Fix applied:** Pre-flight read of every controller/service the new frontend tasks called; confirmed the missing endpoints by inspection and added them to the same PR.

**Rule:** "PR N merged" does not mean every task in PR N's declared range was implemented. Before starting any PR whose frontend tasks call backend APIs, read each referenced controller/service file and confirm that the specific endpoints/methods the frontend needs are present. Do not infer completeness from `tasks.md` markers or `HANDOVER.md` merged-PR entries alone.

---

## ERR-MES-056 — speckit-clarify and speckit-analyze do not detect backend endpoint gaps
**Date:** 2026-05-31  **Category:** Agent Process  **Status:** Promoted 2026-05-31

**Symptom:** `/speckit-clarify` and `/speckit-analyze` were both run before PR 7 implementation. Neither surfaced the missing `BomController` list/delete/patch endpoints, the missing `EcoController.list` endpoint, or the missing `ModuleKey` enum values — all of which the frontend required.

**Root cause:** `speckit-clarify` generates questions about *spec ambiguities* (underspecified requirements in `spec.md`). `speckit-analyze` checks cross-artifact consistency across `spec.md`, `plan.md`, and `tasks.md`. Neither tool reads the codebase to compare what backend endpoints the frontend tasks assume against what is actually implemented. The gap is structural: both tools analyse *documents*, not *code*.

**Fix applied:** Manual pre-flight controller reads at session start identified the gaps before any frontend code was written.

**Rule:** For any PR whose tasks call backend APIs, treat speckit-clarify/analyze as insufficient for dependency verification. Add an explicit pre-flight step: for each service the frontend calls, read the controller class and verify the required HTTP methods exist. This applies even when prior PRs are marked merged in `HANDOVER.md` — speckit tooling has no visibility into what was actually committed.

---

## ERR-MES-051 — Filtered `--tests` Gradle run overwrites JaCoCo exec file
**Date:** 2026-05-30  **Category:** Testing — JaCoCo / Gradle  **Status:** Promoted 2026-05-30

**Symptom:** After running `./gradlew :services:work-order-service:test --tests "...BomServiceTest"` to verify a specific test class passed, the JaCoCo HTML report showed `EffectivityValidator: 0/23 (0%)` — even though `EffectivityValidatorTest` passes. Coverage analysis concluded EffectivityValidator was uncovered; this was incorrect.

**Root cause:** The `test` task writes to a single `build/jacoco/test.exec` file and **replaces** it on every run. A filtered `--tests` invocation runs only the matching tests and writes only their coverage. The subsequent `jacocoTestReport` HTML accurately reflects only that partial run.

**Fix applied:** Re-ran `./gradlew :services:work-order-service:check` to run the complete test suite and regenerate a full `test.exec` before drawing conclusions.

**Rule:** Never read JaCoCo reports after a `--tests`-filtered Gradle run. Always run `./gradlew :<module>:check` before any coverage analysis. The `test.exec` file is replaced, not merged, on each `test` invocation.

---

## ERR-MES-052 — SonarCloud "coverage on new code" counts PR diff lines, not whole-file JaCoCo
**Date:** 2026-05-30  **Category:** CI — SonarCloud  **Status:** Promoted 2026-05-30

**Symptom:** Local JaCoCo showed `BomController: 6/23 (26%)` — looked like a failing coverage gate. SonarCloud passed for BomController because only the 2 lines added in the PR diff (PATCH endpoint) were counted as "new code."

**Root cause:** SonarCloud's "coverage on new code" metric is based on lines that appear as `+` additions in the PR diff vs. the target branch. Pre-existing lines in modified files that were in Develop before the PR do not count. The 21 other BomController lines (createBom, getBom, etc.) were old code.

**Fix applied:** Used `git diff origin/Develop -- <file> | grep "^+"` to count actual new executable lines and recalculate expected SonarCloud coverage accurately.

**Rule:** When diagnosing SonarCloud "new code" coverage failures, run `git diff origin/Develop -- <file>` to identify which specific lines are counted as new. A whole-file JaCoCo percentage is not equivalent to SonarCloud's new-code percentage for partially-modified files.

---

## ERR-MES-049 — Agent claimed "next PR" from plan.md without verifying Develop merge state
**Date:** 2026-05-30  **Category:** Agent Process — Plan vs. Reality  **Status:** Promoted 2026-05-30

**Symptom:** After reading `specs/008-item-master-bom-management/plan.md`, agent stated "PR 2 — BOM Authoring is next." User corrected: PR 11 (BOM Authoring) had already been merged to Develop.

**Root cause:** `plan.md` PR strategy table was read and the first unscoped PR was assumed to be "next." No verification was done against actual git history or GitHub PR state. The plan describes intent and ordering, not current reality.

**Fix applied:** Ran `git log --oneline origin/Develop`; all 5 planned PRs (GitHub #10–#15) confirmed merged. Pivoted to final cleanup PR (2 trailing docs commits).

**Rule:** Before answering "what is next?" or "what remains?" on a feature branch, always run `git log --oneline origin/Develop` and `gh pr list --state merged --base Develop`. Never rely solely on plan.md — the plan describes intent, not current reality. A plan read without a git check is a guess.

---

## ERR-MES-050 — SVG logo reconstruction from raster PNG: fidelity impossible without vector source
**Date:** 2026-05-30  **Category:** Design — Asset Creation  **Status:** Promoted 2026-05-30

**Symptom:** Multiple rounds of SVG geometry corrections (A-shape, star proportions, arc positions, font) still failed to produce output the user accepted. All brand asset files were discarded.

**Root cause:** A complex raster logo cannot be accurately reconstructed in SVG without the original vector source files (AI/EPS/SVG with outlined paths). Every geometry value must be estimated from visual inspection, and small errors compound into a result that "looks nothing like the source."

**Fix applied:** User discarded all assets. The correct resolution is to obtain vector source files before attempting SVG recreation.

**Rule:** When asked to create SVG logos from a raster PNG, immediately surface the limitation: SVG recreation from raster is manual approximation that cannot achieve visual fidelity. Recommend obtaining the original vector source (AI/EPS/SVG with outlined paths) before proceeding. Multiple correction rounds cannot resolve the fundamental absence of path data.

---

## ERR-MES-053 — Angular getter returning new array/object reference triggers NG0100 in dev mode and tests
**Date:** 2026-05-31  **Category:** Frontend — Angular Change Detection  **Status:** Promoted 2026-05-31

**Symptom:** `ItemMasterEditComponent` Vitest tests all failed with `NG0100: ExpressionChangedAfterItHasBeenCheckedError`. Error pointed to a template expression whose value differed between Angular's render pass and verify pass.

**Root cause:** Angular dev mode runs every template expression TWICE per `detectChanges()` call (render pass + verify pass). A `get breadcrumbs()` getter returned a fresh array literal on every call (`return [{...}, {...}, {...}]`). Angular's `===` comparison sees a different object reference on the second evaluation even though the content is identical — NG0100 is thrown. In tests, `fixture.detectChanges()` always runs dev-mode checks, exposing the issue on every test run.

**Fix applied:** Converted `get breadcrumbs()` to a class property initialised with a default array. The property is updated only when the item loads (inside the HTTP subscribe callback). The verify pass sees the same reference on both evaluations.

**Rule:** Never use a getter that returns a newly-created array or object literal when the return value is bound to an `@Input` or template expression. Use a class property reassigned only when data actually changes. Pattern: `breadcrumbs: Crumb[] = defaultCrumbs;` (property), updated in the data-load callback — NOT `get breadcrumbs() { return [{...}]; }`.

---

## ERR-MES-054 — Wrote Jasmine-style matchers in a Vitest-based Angular project
**Date:** 2026-05-31  **Category:** Frontend — Testing / Vitest  **Status:** Promoted 2026-05-31

**Symptom:** Angular spec files failed to compile: `TS2339: Property 'toBeTrue' does not exist on type 'Assertion<boolean>'`, `TS2552: Cannot find name 'spyOn'. Did you mean 'spy'?`.

**Root cause:** The project uses the Vitest runner (`@angular/build:vitest`). Vitest does not include Jasmine's custom matchers (`toBeTrue`, `toBeFalse`) or the global `spyOn`. The agent wrote tests using Jasmine patterns without checking the project's test runner.

**Fix applied:** Replaced `toBeTrue()` → `toBe(true)`, `toBeFalse()` → `toBe(false)`, global `spyOn` → `vi.spyOn` (`import { vi } from 'vitest'`). Switched HTTP service mocks from `HttpTestingController` to `vi.fn().mockReturnValue(of(...))` to avoid async timing issues with `detectChanges()`.

**Rule:** Before writing Angular specs, check `angular.json` for `"builder": "@angular/build:vitest"` (Vitest) vs `"@angular/build:karma"` (Jasmine/Karma). In a Vitest project: `toBe(true)` not `toBeTrue()`; `toBe(false)` not `toBeFalse()`; import `vi` from `'vitest'` for `vi.spyOn()`. Prefer `vi.fn().mockReturnValue(of(...))` service mocks over `HttpTestingController` for components that make HTTP calls — synchronous `of()` observables avoid NG0100 from async state changes mid-`detectChanges()`.

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

## ERR-MES-036 — docker-java defaults to API v1.32, rejected by Docker Desktop 29.x (min 1.40)
**Date:** 2026-05-24  **Category:** Testing — Testcontainers / Docker  **Status:** Promoted 2026-05-24

**Symptom:** Testcontainers tests failed immediately with `BadRequestException (Status 400): client version 1.32 is too old. Minimum supported API version is 1.40`. Docker Desktop was running and responding.

**Root cause:** The docker-java client used by Testcontainers defaults to Docker Engine API version 1.32. Docker Desktop 4.29+ (Docker Engine 29.x) raised the minimum supported API version to 1.40, so every request from Testcontainers is rejected with HTTP 400.

**Fix applied:** Added `systemProperty 'api.version', '1.41'` to the `test {}` block in `build.gradle`. docker-java reads this JVM system property directly to override the default API version.

**Rule:** On Windows with Docker Desktop 29.x, Testcontainers tests require `systemProperty 'api.version', '1.41'` in `build.gradle` `test {}`. Without it, all requests fail with HTTP 400 (API version too old). The env var `DOCKER_API_VERSION` is not reliably forwarded to the test JVM — the `systemProperty` approach is authoritative.

---

## ERR-MES-037 — Gradle daemon does not inherit shell env vars; DOCKER_HOST silently ignored
**Date:** 2026-05-24  **Category:** Testing — Testcontainers / Gradle  **Status:** Promoted 2026-05-24

**Symptom:** Even after setting `$env:DOCKER_HOST = "npipe:////./pipe/docker_engine_linux"` in the PowerShell session, Testcontainers tests ignored it and tried a default named pipe. Manually adding `environment 'DOCKER_HOST', ...` to `build.gradle` and running `.\gradlew --stop` was required for the env var to take effect.

**Root cause:** The Gradle daemon is a long-lived JVM process started once and reused across builds. It captures its environment at daemon start time. If `DOCKER_HOST` was not set when the daemon first started, subsequent shell exports in the same session have no effect — the daemon never sees them.

**Fix applied:** (1) Added `environment 'DOCKER_HOST', dockerHost` (and `DOCKER_API_VERSION` guard) to the `test {}` block in `build.gradle` so the test worker JVM always receives the variable regardless of daemon state. (2) Required `.\gradlew --stop` once to kill the stale daemon before the change took effect.

**Rule:** Never rely on shell env vars alone to reach the Gradle test worker JVM. Forward Docker-related env vars explicitly via `environment` blocks in the `test {}` configuration. After changing `build.gradle`, run `.\gradlew --stop` to kill the cached daemon so the next build starts a fresh process with the updated env.

---

## ERR-MES-038 — Spring Security 6.5 (Boot 3.5) enforces UnreachableFilterChainException for two "any request" chains
**Date:** 2026-05-24  **Category:** Backend — Spring Security  **Status:** Promoted 2026-05-24

**Symptom:** Application startup failed with `UnreachableFilterChainException: A filter chain that matches any request ['filterChain' in SecurityConfig] ... has already been configured.`

**Root cause:** Spring Boot 3.5.0 upgraded to Spring Security 6.5, which introduced strict enforcement of filter chain ordering — it throws at startup if two `SecurityFilterChain` beans both match "any request". `MikeMESSecurityAutoConfiguration` is registered as a Spring Boot auto-configuration and creates a catch-all `SecurityFilterChain`. When `audit-service` also defines `SecurityConfig.filterChain()`, the two chains conflict.

**Fix applied:** (1) Added `spring.autoconfigure.exclude: com.mikemes.common.security.config.MikeMESSecurityAutoConfiguration` to `application.yml`. (2) Added both `OAuth2ResourceServerAutoConfiguration` AND `MikeMESSecurityAutoConfiguration` to the `DynamicPropertySource` exclude list in all IT test classes (since `DynamicPropertySource` completely replaces the `application.yml` exclude list).

**Rule:** Any service that defines its own "any request" `SecurityFilterChain` must exclude `MikeMESSecurityAutoConfiguration` in `application.yml`. In IT tests using `@DynamicPropertySource` to override `spring.autoconfigure.exclude`, the list must be complete — it replaces `application.yml` entirely. Include both `OAuth2ResourceServerAutoConfiguration` and `MikeMESSecurityAutoConfiguration`.

---

## ERR-MES-040 — PrimeNG 21 breaking API: `darkModeSelector` moved to `theme.options`; `overlaypanel` → `popover`
**Date:** 2026-05-29  **Category:** Frontend — PrimeNG  **Status:** Promoted 2026-05-29

**Symptom:** (1) TypeScript error `TS2353: 'darkModeSelector' does not exist in type 'PrimeNGConfigType'` when passing it at the top level of `providePrimeNG()`. (2) TypeScript error `TS2307: Cannot find module 'primeng/overlaypanel'` — the module path no longer exists.

**Root cause:** PrimeNG 21 restructured its configuration API. `darkModeSelector` moved from the top-level config into `theme.options.darkModeSelector`. The `OverlayPanel` component was renamed to `Popover`; its module path changed from `primeng/overlaypanel` to `primeng/popover` and its component selector from `p-overlayPanel` to `p-popover`.

**Fix applied:** Changed `providePrimeNG({ darkModeSelector: '.aurora-dark' })` to `providePrimeNG({ theme: { preset: Aura, options: { darkModeSelector: '.aurora-dark' } } })`. Changed import source to `primeng/popover`, export name to `{ PopoverModule }`, and template tag to `<p-popover>`.

**Rule:** In PrimeNG 21: (a) `darkModeSelector` goes in `theme.options.darkModeSelector`, not at the `providePrimeNG()` top level; (b) use `primeng/popover` and `PopoverModule`/`Popover` — `primeng/overlaypanel` is removed; (c) template tag is `<p-popover>` not `<p-overlayPanel>`. Before using any PrimeNG component or config option, check `node_modules/primeng/package.json` exports to confirm the path exists.

---

## ERR-MES-041 — `npm install --legacy-peer-deps` produces lockfile with mismatched versions; breaks CI `npm ci`
**Date:** 2026-05-29  **Category:** Frontend — npm  **Status:** Promoted 2026-05-29

**Symptom:** CI `npm ci` failed: `ERESOLVE could not resolve — @angular/animations@21.2.0 requires peer @angular/core@"21.2.0" but found @angular/core@21.2.13`. Local build and tests passed.

**Root cause:** `@angular/animations` was installed locally using `--legacy-peer-deps` because the patch versions didn't resolve cleanly. This pinned `21.2.0` in `package-lock.json` while all other Angular packages resolved to `21.2.13`. `npm ci` enforces strict peer dep satisfaction and rejected the mismatch.

**Fix applied:** Pinned all `@angular/*` packages in `package.json` to the exact same version (`21.2.13`). Removed `--legacy-peer-deps`. Re-ran `npm install` which regenerated a consistent `package-lock.json`.

**Rule:** Never use `npm install --legacy-peer-deps` for Angular workspace packages. Angular packages have exact cross-peer deps (`@angular/animations@X.Y.Z` requires `@angular/core@"X.Y.Z"` exactly). All `@angular/*` runtime packages must be pinned to the same exact version in `package.json`. When adding a new Angular package, verify the new package's peer dep version matches before committing the lockfile.

## ERR-MES-061 — New service scaffold omitted @Audited library entity from Envers migrations
**Date:** 2026-06-04  **Category:** Backend — Hibernate Envers  **Promoted:** 2026-06-05
**Symptom:** All integration tests in inventory-service failed at Spring context startup on CI with `SchemaManagementException: Schema-validation: missing table [udf_field_definition_aud]`. Local `./gradlew check` passed because Testcontainers is skipped on Windows without Docker socket.
**Root cause:** `UdfFieldDefinition` from `libs/mes-udf-lib` is `@Audited`. V005 created audit tables only for the three entities defined inside inventory-service. Pre-PR spot-check scanned only the service's own source tree, not library JAR dependencies.
**Fix applied:** Added V010 migration creating `inventory.udf_field_definition_aud`. Proactively included `engineering.udf_field_definition_aud` in engineering-service V004.
**Rule:** When scaffolding a new service, run `grep -rn "@Audited" libs/ --include="*.java"` to find ALL `@Audited` entities on the classpath, including those from shared libraries. Every such entity needs a `_aud` table in the service's Flyway migrations.

## ERR-MES-062 — New service scaffold missing AuditorAware bean; @CreatedBy fields fail NOT NULL
**Date:** 2026-06-05  **Category:** Backend — Spring Data / JPA Auditing  **Promoted:** 2026-06-05
**Symptom:** Every `POST /api/v1/ecos` in CI returned 409 CONFLICT instead of 201. `DataIntegrityViolationException` on `created_by` NOT NULL constraint.
**Root cause:** `@EnableJpaAuditing` declared on `EngineeringServiceApplication` but no `AuditorAware<String>` bean defined. Spring Data left `@CreatedBy`/`@LastModifiedBy` fields null, violating NOT NULL constraints.
**Fix applied:** Added `AppConfig.java` with `AuditorAware<String>` bean reading `SecurityContextHolder`; fallback to `"system"`.
**Rule:** Every service with `@EnableJpaAuditing` and `@CreatedBy`/`@LastModifiedBy` on `NOT NULL` columns MUST have an `AuditorAware<String>` bean. Add it in `AppConfig.java` in `config/`. Manifests as 409 on every create, not a startup error.

## ERR-MES-063 — Missing producer serializer config; KafkaTemplate cannot serialize Map payloads
**Date:** 2026-06-05  **Category:** Backend — Kafka  **Promoted:** 2026-06-05
**Symptom:** `BomReleasedConsumerIT` failed with `SerializationException: Can't convert value of class java.util.HashMap to class StringSerializer`. `EcoEventPublisher` uses `KafkaTemplate<String, Map<String, Object>>`.
**Root cause:** No `spring.kafka.producer.value-serializer` in `application.yml`. Spring Boot defaults to `StringSerializer` which cannot serialize `Map<String, Object>`.
**Fix applied:** Added `spring.kafka.producer.value-serializer: JsonSerializer` + consumer `spring.json.value.default.type` and `spring.json.use.type.headers: false`.
**Rule:** Any service using `KafkaTemplate` with non-String values MUST set `spring.kafka.producer.value-serializer: org.springframework.kafka.support.serializer.JsonSerializer` explicitly. The Spring Boot default is `StringSerializer`; there is no startup error, only a runtime `SerializationException`.

---

## ERR-MES-071 — Optional DTO field missing @Size constraint — validation gap vs sibling DTO
**Date:** 2026-06-07  **Category:** Backend — Validation  **Promoted:** 2026-06-07
**Symptom:** `CreateUserRequest.initialPassword` had no `@Size(min=8,max=72)` constraint. An admin could set a 1-character initial password via the create-user endpoint, bypassing the 8-character minimum that `SetPasswordRequest` enforces for admin password resets and `ChangeTemporaryPasswordRequest` enforces for self-service.
**Root cause:** When extending an existing DTO with optional fields that correspond to fields already validated in sibling DTOs (`SetPasswordRequest`, `ChangeTemporaryPasswordRequest`), the constraint was not ported. The field being nullable (`@Nullable`) gave the false impression that no further constraints were needed; Jakarta Bean Validation skips `@Size` on null values automatically, so `@Nullable @Size(min=8,max=72)` is both safe and correct.
**Fix applied:** Added `@Nullable @Size(min = 8, max = 72)` to `initialPassword` in `CreateUserRequest.java`.
**Rule:** When adding an optional field to a DTO that represents the same semantic value as a required field in a sibling DTO, port ALL validation constraints from the sibling to the optional field. Bean Validation skips `@Size`/`@Pattern`/etc. on null values, so constraints on nullable fields are safe and only fire when the field is present.

---

## ERR-MES-072 — IT test for public endpoint missing assumeTrue guard despite reaching KC
**Date:** 2026-06-07  **Category:** Testing — Testcontainers  **Promoted:** 2026-06-07
**Symptom:** `PublicAuthControllerIT.changeTemporaryPassword_unknownUser_returns400` had no `assumeTrue(KEYCLOAK.isRunning())` guard. The request body is structurally valid (all fields non-blank, newPassword ≥ 8 chars), so it passes Spring `@Valid` and reaches `UserService.changeTemporaryPassword()`, which calls `KeycloakTokenClient` — a live HTTP call to KC. Without Docker, `RestTemplate.postForEntity()` throws an unchecked network exception not caught by `HttpClientErrorException`, causing the test to fail with 500 rather than 400.
**Root cause:** The test looked like a "validation-only" scenario because its assertion was HTTP 400, but the request passes all Bean Validation constraints. The actual 400 comes from KC returning an error, not from `@Valid` rejection. Tests for public endpoints that send structurally valid payloads must be treated as KC-dependent even when the _expected_ response is an error.
**Fix applied:** Added `assumeTrue(KEYCLOAK.isRunning(), "Docker not available")` as the first statement in the test method.
**Rule:** For public endpoints where KC is involved in the error path: if a test sends a structurally valid request body (passes `@Valid`) and expects an error response, the error comes from KC — not from Spring validation. That test is KC-dependent and requires `assumeTrue(KEYCLOAK.isRunning())`.

---

## ERR-MES-073 — Controller duplicating GlobalExceptionHandler catch creates inconsistent response shapes
**Date:** 2026-06-07  **Category:** Backend — API Design  **Promoted:** 2026-06-07
**Symptom:** `PublicAuthController.changeTemporaryPassword` caught `InvalidCredentialsException` locally and returned `Map.of("message","...")` (shape: `{"message":"..."}`) while `GlobalExceptionHandler` returns `ErrorResponse("invalid_credentials", ...)` (shape: `{"code":"...","message":"..."}`). The two response shapes differ. If the local catch were ever removed, clients would silently get a different JSON structure.
**Root cause:** The controller was written to have explicit control over the error body, not realizing that `GlobalExceptionHandler` already mapped `InvalidCredentialsException` → HTTP 400 with a consistent `ErrorResponse`. The result was redundant handling with divergent shapes.
**Fix applied:** Removed the `try/catch` from `PublicAuthController`. Return type changed from `ResponseEntity<?>` to `ResponseEntity<Void>`. `GlobalExceptionHandler` handles all `InvalidCredentialsException` cases uniformly.
**Rule:** Never catch exceptions in a controller that are already mapped by `GlobalExceptionHandler`. Add exception mappings exclusively to `GlobalExceptionHandler` so all endpoints share the same response shape. If a controller needs a different response body for a specific exception, override the handler only via `@ExceptionHandler` on that specific controller class — not via inline try/catch.
