# MES Agent Error Log — Index

> **Session-start instruction:** Read this file at the start of every session. It summarises all promoted lessons. Full entries are in `MES-ERR-001-ARCHIVE.md`.

| ID | Date | Category | One-line lesson |
|----|------|----------|----------------|
| ERR-MES-001 | 2026-05-20 | Shell — Bash tool | Never `cd "path" && command` in Bash tool; triggers safety gate |
| ERR-MES-002 | 2026-05-20 | Build | Always use `./gradlew`; never `gradle`; don't trust host `java -version` |
| ERR-MES-003 | 2026-05-20 | Java — generics | Cast response body to `Map<K,V>`, never `Map<?,?>`; pair with `@SuppressWarnings("unchecked")` + `requireNonNull` |
| ERR-MES-004 | 2026-05-20 | Shell — tool mix | Never mix PowerShell syntax into a Bash tool call |
| ERR-MES-005 | (seed) | Jira | Transition IDs differ by issue type; look up per-type before calling transitionJiraIssue |
| ERR-MES-006 | (seed) | Jira | Run retrospective gate before moving issue to Done |
| ERR-MES-007 | (seed) | Static analysis | Fix all violations of a pattern in one pass; audit entire codebase, not just the surfaced instance |
| ERR-MES-008 | (seed) | CI | Never sleep-poll CI; use Monitor or wait for notification |
| ERR-MES-009 | (seed) | CI | Verify all checks pass before marking a PR ready to merge |
| ERR-MES-010 | (seed) | Git | `git checkout <hash> -- file` auto-stages the file; commit immediately or unstage deliberately |
| ERR-MES-011 | (seed) | Git | Merge conflict resolution: checkout branch → merge → resolve → `git add` → `git commit` |
| ERR-MES-012 | (seed) | Git | Always clean working tree before switching branches; stash or commit first |
| ERR-MES-013 | (seed) | Edit tool | Read governance markdown before Edit; the tool fails if old_string is not present verbatim |
| ERR-MES-014 | (seed) | Governance | Governance files are single-writer across branches; never edit same file on two branches simultaneously |
| ERR-MES-015 | (seed) | Docker | Use `docker compose logs <service-name>` not container name |
| ERR-MES-016 | (seed) | Docker / Config | New env vars must land in both `.env.example` AND compose file in the same PR |
| ERR-MES-017 | (seed) | Code pattern | When a pattern-based bug surfaces, audit all instances across the codebase before closing |
| ERR-MES-018 | (seed) | Deployment | WinSCP: always use Binary mode for non-text files; strip base64 newlines before decoding |
| ERR-MES-019 | 2026-05-20 | Frontend — ESLint | Flat config rejects `processor: angular.processInlineTemplates`; omit it when all components use `templateUrl` |
| ERR-MES-024 | 2026-05-21 | Testing — Keycloak | Always assert `r.getStatus() == 201` after KC admin create calls; never use KC to issue JWTs in IT tests — use locally-signed RSA JWTs |
| ERR-MES-025 | 2026-05-21 | Agent — Confidence Calibration | Flag uncertainty before committing when relying on niche external-service breaking-change behaviour; prefer a local smoke test over a CI run as first verification |
| ERR-MES-020 | 2026-05-21 | CI — Permissions | `gradlew` committed without execute bit on Windows; run `git update-index --chmod=+x gradlew` before first push |
| ERR-MES-021 | 2026-05-21 | CI — GitHub | Branch protection API requires GitHub Pro on private repos; free plan returns 403 |
| ERR-MES-022 | 2026-05-21 | Testing — Keycloak | KC 25+ new realm needs `setDirectGrantFlow("direct grant")` + full user profile fields for ROPC to work |
| ERR-MES-023 | 2026-05-21 | Backend — Hibernate Envers | `@Audited` relation to non-audited entity requires `@Audited(targetAuditMode = NOT_AUDITED)` on the field |
| ERR-MES-024 | 2026-05-21 | Testing — Keycloak | Assert `r.getStatus() == 201` on every KC admin create call; use locally-signed RSA JWTs in IT tests, never KC ROPC |
| ERR-MES-026 | 2026-05-21 | Backend — JPA / Security | `PrivilegeCache` methods traversing lazy relations must be `@Transactional(readOnly=true)` with `JOIN FETCH` |
| ERR-MES-027 | 2026-05-22 | Backend — JPA / Hibernate | Hibernate 6.5 JPQL implicit join on lazy `@ManyToOne.id` in WHERE returns 0 rows; use explicit JOIN or scalar FK field |
| ERR-MES-028 | 2026-05-22 | Backend — Validation | `@Valid` on `@RequestBody` silently no-ops without `spring-boot-starter-validation` on classpath |
| ERR-MES-029 | 2026-05-22 | Backend — JPA / Hibernate | Hibernate 6.5 explicit JPQL JOIN on `@Audited` entity also returns 0 rows; use `findAllActive()` + Java filter |
| ERR-MES-030 | 2026-05-22 | Backend — JPA / Security | `PrivilegeCache` in Spring Security filter chain must use `JdbcTemplate`; JPQL + lazy loading fails silently before OEMIV |
| ERR-MES-031 | 2026-05-22 | Backend — Hibernate Envers | Set both `hibernate.default_schema` AND `envers.default_schema`; missing Hibernate property causes unqualified `revinfo_seq` lookup |
| ERR-MES-032 | 2026-05-22 | Testing — Keycloak | KC 24+ disables unmanaged attributes by default; call `userProfile().update(ENABLED)` after realm creation or `org_id` is silently dropped |
| ERR-MES-033 | 2026-05-22 | CI — SonarCloud | `sonar.sources`/`sonar.tests` must only list existing directories; missing path causes SonarScanner exit code 3 |
| ERR-MES-034 | 2026-05-22 | Build — Gradle | Aggregate `JacocoReport` at root needs `apply plugin: 'jacoco'` on root project + explicit `dependsOn jacocoTestReport` per subproject |
