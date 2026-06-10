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
| ERR-MES-033 | 2026-05-23 | Build | `spring-boot-starter-web` must be declared explicitly on any service with REST controllers or servlet filters; `data-jpa`/`actuator`/`validation` do not pull it in |
| ERR-MES-034 | 2026-05-24 | Testing — Spring Security | 2-arg `UsernamePasswordAuthenticationToken(name, null)` creates unauthenticated token; use 3-arg constructor with authorities for authenticated test tokens |
| ERR-MES-035 | 2026-05-24 | Testing — Spring MVC slice | `@WebMvcTest` does not load JPA repos; if a `@Configuration` bean injects a repo, add `@MockitoBean` for it in the test class |
| ERR-MES-036 | 2026-05-24 | Testing — Testcontainers | docker-java defaults to API v1.32; Docker Desktop 29.x min is 1.40 — add `systemProperty 'api.version', '1.41'` to `test {}` |
| ERR-MES-037 | 2026-05-24 | Testing — Gradle | Gradle daemon doesn't inherit shell env vars; forward `DOCKER_HOST` via `environment` in `test {}` and run `--stop` after the change |
| ERR-MES-038 | 2026-05-24 | Backend — Spring Security | Spring Security 6.5 throws `UnreachableFilterChainException` for two "any request" chains; exclude shared auto-config in `application.yml` AND in `DynamicPropertySource` (it replaces yml) |
| ERR-MES-039 | 2026-05-26 | Backend — Spring Security / Java | `Map.of()` and `Optional.of()` throw NPE when `auth.getName()` returns null (JWT without `sub` claim); always null-safe getName() before passing to these APIs |
| ERR-MES-040 | 2026-05-29 | Frontend — PrimeNG | PrimeNG 21: `darkModeSelector` in `theme.options`, not top-level; `primeng/overlaypanel` → `primeng/popover`; check package.json exports before importing |
| ERR-MES-041 | 2026-05-29 | Frontend — npm | Never `npm install --legacy-peer-deps` for Angular packages; all `@angular/*` must pin to same exact version or `npm ci` fails on CI |
| ERR-MES-049 | 2026-05-30 | Agent Process | Always run `git log --oneline origin/Develop` + `gh pr list --merged` before claiming what PR is next; plan.md describes intent, not current state |
| ERR-MES-050 | 2026-05-30 | Design — Assets | SVG recreation from raster PNG cannot achieve fidelity; request vector source (AI/EPS) before attempting; do not iterate corrections |
| ERR-MES-051 | 2026-05-30 | Testing — JaCoCo | `--tests` filtered Gradle run replaces jacoco.exec; always run `check` before reading coverage reports |
| ERR-MES-052 | 2026-05-30 | CI — SonarCloud | SonarCloud "new code" = PR diff lines only; use `git diff origin/Develop -- <file>` to identify actual new-code scope |
| ERR-MES-053 | 2026-05-31 | Frontend — Angular Change Detection | Getter returning new array/object each call triggers NG0100 in dev mode; use a class property updated on data load |
| ERR-MES-054 | 2026-05-31 | Frontend — Testing / Vitest | Jasmine matchers (`toBeTrue`, `spyOn`) don't exist in Vitest; use `toBe(true)` + `vi.spyOn` from `'vitest'` |
| ERR-MES-055 | 2026-05-31 | Agent Process | `tasks.md` stale markers can't distinguish "planned but skipped" from "done but unchecked"; read controllers to verify endpoints exist before starting a dependent PR |
| ERR-MES-056 | 2026-05-31 | Agent Process | `speckit-clarify`/`speckit-analyze` analyse documents, not code; they cannot detect missing backend endpoints; manual controller reads are required as a pre-flight for frontend PRs |
| ERR-MES-057 | 2026-05-31 | Backend — Hibernate Envers | Adding columns to `@Audited` entity requires same columns in `_aud` table in the same migration; Envers schema-validation enforces parity at startup |
| ERR-MES-058 | 2026-05-31 | Agent Process | Pre-PR retrospective is a technical gate, not a formality; skipping it let a known Envers pattern repeat; must spot-check all relevant index categories before `gh pr create` |
| ERR-MES-066 | 2026-06-07 | Infrastructure — Keycloak / Docker | KC without `KC_HOSTNAME` derives issuer from `Host` header; Spring Security 6.5 auto-validates `iss` even with `jwk-set-uri` only — localhost token vs keycloak-internal OIDC discovery = 401 on every request |
| ERR-MES-067 | 2026-06-07 | Agent Process — Verification | Post-fix verification via direct KC curl bypasses gateway; only a full-stack 200 (login → gateway → service) is a valid passing criterion for auth fixes |
| ERR-MES-068 | 2026-06-07 | Testing — Spring Security / Keycloak | IT tests that set `issuer-uri=""` mask KC_HOSTNAME misconfiguration; tests must use real Testcontainers issuer URI and a compose-level gateway smoke test is required |
| ERR-MES-069 | 2026-06-07 | Testing — Spring Boot Conditional | `@ConditionalOnMissingBean` in `@Import`-ed config evaluates before test `@TestConfiguration` beans — races and crashes context; only reliable in Spring Boot auto-configuration |
| ERR-MES-070 | 2026-06-07 | CI — SonarCloud / Coverage | Private controller methods with branches create uncovered code; always delegate to a tested shared helper or put logic in a unit-tested utility class |
| ERR-MES-059 | 2026-06-05 | Frontend — Angular Change Detection | Every `.subscribe()` callback mutating a template-bound property needs `cdr.detectChanges()` at the end of both `next:` and `error:` — applies to all lifecycle hooks and action methods |
| ERR-MES-060 | 2026-06-05 | Backend — Keycloak / JWT | Keycloak 25 no longer auto-includes `sub`; every client needs an explicit sub mapper; never call `jwt.getSubject()` without `sub → preferred_username → "unknown"` fallback |
| ERR-MES-061 | 2026-06-04 | Backend — Hibernate Envers | New service scaffold must grep `libs/` for `@Audited` entities — every one needs a `_aud` table in the service's Flyway migrations |
| ERR-MES-062 | 2026-06-05 | Backend — Spring Data / JPA Auditing | Every service with `@EnableJpaAuditing` and `NOT NULL` `@CreatedBy`/`@LastModifiedBy` fields must have an `AuditorAware<String>` bean in `AppConfig.java` |
| ERR-MES-063 | 2026-06-05 | Backend — Kafka | Any service using `KafkaTemplate` with non-String values must set `spring.kafka.producer.value-serializer: JsonSerializer` explicitly — Spring Boot default is `StringSerializer` |
| ERR-MES-071 | 2026-06-07 | Backend — Validation | Optional DTO fields that mirror required fields in sibling DTOs must carry the same `@Size`/`@Pattern` constraints — Bean Validation skips them on null safely |
| ERR-MES-072 | 2026-06-07 | Testing — Testcontainers | Public endpoint IT tests with structurally valid bodies reach KC even when expecting an error response — always add `assumeTrue(KC.isRunning())` if the error path hits KC |
| ERR-MES-073 | 2026-06-07 | Backend — API Design | Never catch exceptions inline in a controller already mapped by `GlobalExceptionHandler` — creates divergent response shapes between the inline handler and the global handler |
| ERR-MES-075 | 2026-06-08 | Backend — IAM / Privilege System | `registerManifest()` only upserts `iam.privilege`; new privileges are never granted to SYSTEM_ADMIN until `registerManifest()` explicitly auto-grants them — fixed in `PrivilegeService` |
| ERR-MES-076 | 2026-06-08 | Deployment — Docker | `docker cp` to wrong filename (artifact name, not `app.jar`) leaves old JAR running after `docker restart`; always verify container CMD/ENTRYPOINT before copying |
| ERR-MES-077 | 2026-06-09 | Backend — Flyway / Database Migration | Seed INSERT omitting NOT NULL audit columns (`created_by`, `updated_by`) causes 23502 at startup; Spring context cache then fails every IT class with "threshold exceeded" — one bad row = full suite failure |
| ERR-MES-078 | 2026-06-09 | Frontend — Angular / UDF / Column Picker | `gridPreference.load()` without UDF fetch leaves new custom fields invisible in column picker; `item[col.key]` misses `customFields[key]` — always use `getCellValue()` + dynamic UDF load pattern |
| ERR-MES-079 | 2026-06-10 | Frontend — Angular Templates | `value as Type` inside template expressions is a parse error (NG5002); add a typed helper method to the component class and call that from the template instead |
