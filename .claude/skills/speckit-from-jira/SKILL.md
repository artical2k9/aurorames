---
name: "speckit-from-jira"
description: "Import a Jira Epic into a spec-kit specification. Reads a Jira Epic by key, interprets the free-form description into the standard spec format, writes specs/[###-slug]/spec.md, and stops for human review before /speckit-plan."
argument-hint: "Jira issue key, e.g. MES-5 or just 5"
compatibility: "Requires .specify/integrations/jira.json and .specify/ project structure"
metadata:
  author: "mikemes-override"
  source: "local skill — Jira Epic → spec-kit spec import"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

The argument is the Jira issue key to import. It may be:
- Full key:  `MES-5`
- Number only: `5` → expand to `MES-5` using the project key from jira.json

If no argument is provided, ask the user to supply the Jira issue key before proceeding.

---

## Outline

### Step 1 — Load Jira configuration

Read `.specify/integrations/jira.json`.

If it does not exist, stop and tell the user:
> Jira is not configured. Run the setup steps to create `.specify/integrations/jira.json`.

Extract: `cloudId`, `projectKey`, `site`.

Normalise the input key: if the user passed only a number (e.g. `5`), prefix it
with the project key to form `MES-5`.

---

### Step 2 — Fetch the Jira Epic

Use `mcp__claude_ai_Atlassian__getJiraIssue` with:
- `cloudId` from jira.json
- `issueKey` = normalised key

Extract from the response:
- `fields.summary` → **Epic title**
- `fields.description` → **free-form content** (may be Atlassian Document Format;
  flatten all text nodes into a single readable string for interpretation)
- `fields.labels` → list of labels (used for compliance tagging)
- `fields.priority.name` → issue priority (Highest/High/Medium/Low → P1/P2/P3/P4)
- `fields.issuetype.name` → validate this is an Epic (warn if not, ask user
  whether to continue)
- `fields.components[*].name` → component/module hints (optional context)
- `fields.assignee.displayName` → author context (optional)

If the fetch fails (issue not found, permission denied), surface the error and stop.

---

### Step 3 — Determine next spec number and slug

1. List the contents of `specs/` in the repo root (it may not exist yet — that is fine).
2. Find the highest existing three-digit prefix (e.g. `003-...`). The new spec gets
   the next number (e.g. `004`).
3. If `specs/` does not exist or is empty, start at `001`.
4. Derive a kebab-case slug from the Epic title:
   - Lowercase, replace spaces and special chars with hyphens, max 40 chars.
   - Example: "Work Order Management" → `work-order-management`
5. Feature directory: `specs/[###-slug]/` (e.g. `specs/001-work-order-management/`)

---

### Step 4 — Interpret free-form Epic content into spec structure

Read the Epic title and description as free-form text written by a human. Your job
is to produce a structured spec — do not quote the raw text verbatim; interpret and
organise it.

**Mapping rules:**

| Spec section | How to derive it |
|---|---|
| Feature name | Epic title |
| Feature branch | `[###-slug]` from Step 3 |
| User Scenarios | Infer discrete user journeys from the description. Each named capability or workflow the Epic mentions becomes a User Story. Assign priorities (P1 first) based on order of mention and business criticality. |
| Acceptance Scenarios | For each User Story, write Given/When/Then scenarios that a tester could execute. Derive from the Epic text; fill gaps with sensible defaults for an MES context. |
| Functional Requirements | Extract explicit "must", "shall", "should" statements. Infer implicit requirements from domain context. Prefix FR-001, FR-002… |
| Key Entities | Identify the data objects mentioned (work order, operation, material, etc.) |
| Success Criteria | Define measurable outcomes tied to the user stories |
| Assumptions | Note anything not stated in the Epic that you are assuming |
| Compliance References | Map Epic labels + domain context to the constitution's Compliance Register. For any MES feature involving quality, materials, or traceability, populate the AS9100D row at minimum. |
| Edge Cases | Include manufacturing-domain edge cases (e.g. late material arrival, machine downtime, operator certification gap) relevant to the feature |

**Do not** leave any mandatory placeholder tokens (e.g. `[FEATURE NAME]`) unfilled
in the output spec. Every section must have real content or a justified TODO.

---

### Step 5 — Write the spec file

1. Create the directory `specs/[###-slug]/` if it does not exist.
2. Write the completed spec to `specs/[###-slug]/spec.md` using the structure
   from `.specify/templates/spec-template.md` (with Compliance References section).
3. Write a small metadata file `specs/[###-slug]/jira.json`:

```json
{
  "epicKey": "MES-N",
  "epicId": "<id from Jira response>",
  "epicSummary": "<Epic title>",
  "importedAt": "<ISO 8601 timestamp>",
  "specFile": "specs/[###-slug]/spec.md"
}
```

This file links the spec back to Jira for traceability and is used by
`/speckit-taskstoissues` to attach child issues to the correct Epic.

---

### Step 6 — Post a traceability comment on the Jira Epic

Use `mcp__claude_ai_Atlassian__addCommentToJiraIssue` to add a comment to the Epic:

```
spec-kit specification created from this Epic.

Spec file: specs/[###-slug]/spec.md
Branch:    [###-slug]
Imported:  <ISO 8601 timestamp>

Next steps (run in Claude Code):
  /speckit-plan   — generate technical implementation plan
  /speckit-tasks  — break plan into actionable tasks
  /speckit-taskstoissues — push tasks back to Jira as child issues under this Epic
```

This satisfies Constitution Principle V (Full System Auditability) — every action
against a quality record must be traceable.

---

### Step 7 — Display spec for review and stop

Print the full content of the generated `specs/[###-slug]/spec.md` to the user.

Then output exactly this review prompt:

---

**Spec generated from Jira Epic `[KEY]` → `specs/[###-slug]/spec.md`**

Please review the specification above.

- **To revise**: describe what to change and I will update the spec.
- **To approve and begin planning**: say `proceed` (or `/speckit-plan`) and I
  will start the implementation plan.

Do **not** run `/speckit-plan` until the user explicitly approves.

---

## Notes

- The spec is the authoritative document. The Jira Epic is the source of intent;
  the spec is the source of implementation truth (Constitution Principle I).
- If the Epic description is very sparse, generate a draft spec with clear TODOs
  and highlight them to the user for completion before approval.
- If the Epic has child Stories already in Jira, list them in the Assumptions
  section as context — do not auto-import them as user stories without review.
- The `jira.json` metadata file in the spec directory is also read by
  `/speckit-taskstoissues` to link created Task issues as children of the
  correct Epic — do not skip creating it.
