<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan at:
specs/007-system-activity-audit-logging/plan.md
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
[type](MES-NNN): short description

Ref: MES-NNN
```

**Correct:** `[feat](MES-5): add CaffeinePrivilegeCache with Kafka invalidation`
**Wrong:** `feat(MES-5): add CaffeinePrivilegeCache with Kafka invalidation`

Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`

The ticket key **must** appear in the headline — this is what the GitHub for Jira app scans to link commits to issues. Use `Ref: MES-NNN` in the footer (not `Closes`).

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

For **Keycloak realm changes (`keycloak/mikemes-realm.json`):**
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
