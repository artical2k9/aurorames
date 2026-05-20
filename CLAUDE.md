<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan at:
specs/001-iam-multi-org-security-keycloak/plan.md
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
