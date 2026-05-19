---
name: "speckit-taskstoissues"
description: "Convert spec-kit tasks into Jira issues (MES project) or GitHub issues, depending on project configuration."
argument-hint: "Optional label or epic name to assign to created issues"
compatibility: "Requires spec-kit project structure with .specify/ directory"
metadata:
  author: "mikemes-override"
  source: "local override — routes to Jira when .specify/integrations/jira.json present"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Pre-Execution Checks

**Check for extension hooks (before tasks-to-issues conversion)**:
- Check if `.specify/extensions.yml` exists in the project root.
- If it exists, read it and look for entries under the `hooks.before_taskstoissues` key.
- Filter out hooks where `enabled` is explicitly `false`.
- For each enabled, condition-free, non-optional hook output:
  ```
  **Automatic Pre-Hook**: {extension}
  Executing: `/{command}`
  ```
  Then execute the hook command before continuing.
- For optional hooks output the optional hook block and skip automatic execution.
- If no hooks registered, skip silently.

## Outline

### Step 1 — Detect issue tracker

Read `.specify/integrations/jira.json`.

- If the file **exists** → use **Jira** path (Steps 2–6).
- If the file **does not exist** → fall back to GitHub Issues (Step 7).

### Step 2 — Load Jira configuration (Jira path)

Parse `.specify/integrations/jira.json` and extract:
- `site`, `cloudId`, `projectKey`, `projectName`
- `issueTypes` map
- `mapping` (speckit concept → Jira issue type)

### Step 3 — Locate tasks file

Run from repo root:

```powershell
.\.specify\scripts\powershell\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks
```

Parse the output JSON to find `FEATURE_DIR` and the path to `tasks.md`.
All paths must be absolute.

If no tasks file is found, stop and tell the user to run `/speckit-tasks` first.

### Step 4 — Parse tasks into structured items

Read the tasks file and extract every task line of the form:
```
- [ ] TXXX [flags] [StoryRef] Description
```

For each task, determine the Jira issue type using this mapping:

| Condition | Jira Type |
|---|---|
| Phase heading line (e.g. `## Phase N:`) | **Epic** |
| User Story heading (e.g. `## Phase N: User Story`) | **Story** (child of phase Epic) |
| Task with `[US\d]` label | **Task** (child of User Story) |
| Task in "Tests for User Story" block | **Task** (with label `test`) |
| Task in "Compliance Verification" phase | **Task** (with label `compliance`) |
| Task starting with `T\d+ .* defect` or defect-related | **Bug** |

Phases become Epics; User Stories become Stories under those Epics; individual
tasks become Tasks under their Story.

### Step 5 — Confirm with user before creating issues

Display a summary table:

```
Issues to create in Jira project MES (artical.atlassian.net):
  Epics:   N
  Stories: N
  Tasks:   N
  Bugs:    N
  Total:   N

Proceed? [Y to continue]
```

Wait for confirmation before creating any issues.

### Step 6 — Create Jira issues using Atlassian MCP

Use the `mcp__claude_ai_Atlassian__createJiraIssue` tool.

**Order of creation** (to support parent-child linking):
1. Create all Epics first; record returned issue keys.
2. Create all Stories, setting `customfield_10014` (Epic Link) to the parent Epic key.
3. Create all Tasks/Bugs, setting parent to the Story key where applicable.

**Issue fields for every issue**:
- `summary`: task description (trimmed, max 255 chars)
- `issuetype`: mapped type from Step 4
- `project`: `{ "key": "MES" }`
- `description` (ADF format): include the full task line, feature branch, and
  spec path for traceability
- `labels`: add `speckit` plus any labels from user input or task flags (`[P]`,
  `compliance`, `test`)
- `priority`: derive from spec priority (P1 → Highest, P2 → High, P3 → Medium,
  unset → Low)

After creating each issue, record the key (e.g. `MES-42`) and map it to the
original task ID (e.g. `T012`) for the summary report.

### Step 7 — GitHub Issues fallback (no Jira config)

If `.specify/integrations/jira.json` does not exist:

1. Get the Git remote: `git config --get remote.origin.url`
2. Only proceed if it is a GitHub URL.
3. Create GitHub issues using the GitHub MCP server for each task.

> [!CAUTION]
> NEVER create issues in repositories that do not match the remote URL.

### Step 8 — Output summary

Print a table mapping each task ID to its created Jira issue key and URL:

```
Created issues in MES:
  T001 → MES-10  Epic: Phase 1 — Setup
  T002 → MES-11  Story: Work Order Foundation
  T003 → MES-12  Task: Create WorkOrder model
  ...
```

Also suggest the next step:
```
Next: use /speckit-implement to begin executing tasks.
```

## Post-Execution Checks

**Check for extension hooks (after tasks-to-issues conversion)**:
- Check `.specify/extensions.yml` for `hooks.after_taskstoissues`.
- For optional hooks, output the optional hook block (do not auto-execute).
- For mandatory hooks (optional: false, no condition), execute them.
- Skip silently if no hooks registered.
