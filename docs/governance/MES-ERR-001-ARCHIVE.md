# MES Agent Error Log — Archive

> Full RCA for all promoted errors. Index is in `MES-ERR-001_Index.md`.

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
