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
