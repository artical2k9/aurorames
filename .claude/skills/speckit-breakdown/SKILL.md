---
name: "speckit-breakdown"
description: "Decompose a programme-level Jira Epic into child Epics. Reads the Epic description, cross-references the MikeMES constitution's functional domain table, proposes a child Epic breakdown for review, then creates the child Epics in Jira linked to the parent via 'Relates' issue links."
argument-hint: "Programme-level Jira Epic key, e.g. MES-1 or just 1"
compatibility: "Requires .specify/integrations/jira.json and .specify/memory/constitution.md"
metadata:
  author: "mikemes-override"
  source: "local skill — programme Epic → child Epic decomposition"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

The argument is the programme-level Jira Epic key. Normalise as for
`/speckit-from-jira`: if the user passed only a number, prefix with the
project key from `jira.json` (e.g. `5` → `MES-5`).

If no argument is provided, ask the user to supply the Epic key before proceeding.

---

## Outline

### Step 1 — Load configuration

Read `.specify/integrations/jira.json` and extract:
- `cloudId`, `projectKey`, `programmeLink.linkType` (`"Relates"`)

Read `.specify/memory/constitution.md` and extract the **Functional Domain
Coverage** table — this is the primary decomposition reference.

---

### Step 2 — Fetch the programme Epic from Jira

Use `mcp__claude_ai_Atlassian__getJiraIssue` with `cloudId` and the normalised
issue key.

Extract:
- `fields.summary` → programme title
- `fields.description` → free-form intent (flatten ADF to plain text)
- `fields.labels` → any signals about scope or priority
- `fields.priority.name` → programme priority
- `fields.issuetype.name` → confirm it is an Epic; warn if not and ask user
  whether to continue

Store the parent Epic key (e.g. `MES-1`) — every child Epic created will be
linked back to this.

---

### Step 3 — Propose child Epic breakdown

Using two inputs together:

**Input A — Constitution Functional Domain Coverage table**
The 16 domains defined in the constitution (Work Orders & Scheduling,
Shop Floor Tracking, Quality & Inspection, etc.) are the canonical module
boundaries for MikeMES. Start from this list.

**Input B — Programme Epic description**
The user's free-form text may:
- Explicitly name functional areas → confirm those domains are included
- Mention specific constraints, phasing, or priorities → reflect those
- Exclude certain domains ("out of scope for now") → mark those as deferred
- Suggest a phased delivery order → reflect as priority ordering

**Decomposition rules:**

1. Each proposed child Epic covers **one functional domain** from the
   constitution table — never split or merge domains without a stated reason.
2. If the programme description explicitly includes/excludes domains, honour
   that over the constitution default list.
3. Assign a **phase priority**:
   - P1 (Foundation): IAM / Security, System Logging, Multi-Org framework —
     these must be built first as they underpin everything else.
   - P2 (Core MES): Work Orders, BOM, Shop Floor Tracking, Material Receiving.
   - P3 (Quality): Quality & Inspection, NCM, Document Management.
   - P4 (Advanced): Manufacturing Engineering, OSP, Gauge/Tool, Skills Mgmt.
   - P5 (Integration): Inbound/Outbound Integrations, Labour Tracking.
   Adjust based on anything stated in the programme Epic description.
4. Write a concise description for each child Epic (2–4 sentences) covering:
   - What it does
   - Key compliance standards that apply (from the constitution Compliance Register)
   - Its dependency on other child Epics (if any)

**Output format for review:**

Present the breakdown as a numbered table:

```
Proposed child Epics for [PARENT KEY]: [Programme title]

Phase | # | Domain                          | Priority | Depends on | Key Standards
------|---|----------------------------------|----------|------------|---------------
P1    | 1 | IAM & Security (Keycloak)        | Highest  | —          | NIST SP 800-171, CMMC
P1    | 2 | System Activity Logging          | Highest  | 1          | 21 CFR Part 11
P1    | 3 | Multi-Organisation Framework     | Highest  | 1          | ISA-95
P2    | 4 | Work Orders & Scheduling         | High     | 1,2,3      | AS9100D §8.1, ISA-95
...

Total: N child Epics across N phases.

Deferred (not in scope per Epic description):
  - [domain name] — reason

To adjust: tell me which Epics to add, remove, rename, re-prioritise, or
split differently. When ready, say "create" to create them in Jira.
```

**Do not create anything in Jira until the user explicitly approves.**

---

### Step 4 — Iterative review loop

After displaying the breakdown:

- If the user provides feedback (add/remove/rename/reprioritise), update the
  proposed table and display it again. Repeat until approved.
- If the user says `"create"` or equivalent approval, proceed to Step 5.
- If the user says `"cancel"`, stop without creating anything.

---

### Step 5 — Create child Epics in Jira

Create the child Epics **in phase priority order** (P1 first) so dependencies
are already in Jira when later Epics are created.

For each child Epic, use `mcp__claude_ai_Atlassian__createJiraIssue` with:

```
cloudId:       <from jira.json>
projectKey:    "MES"
issueTypeName: "Epic"
summary:       "[MES] <Domain Name>" (prefix MES to namespace it clearly)
description:   <2-4 sentence description from Step 3> (contentFormat: "markdown")
additional_fields:
  priority:  { name: "<Highest|High|Medium|Low>" }
  labels:    ["speckit", "speckit-programme", "programme:<PARENT_KEY>"]
             e.g. ["speckit", "speckit-programme", "programme:MES-1"]
```

Record the returned issue key for each created Epic (e.g. `MES-6`, `MES-7`…).

---

### Step 6 — Link each child Epic to the programme Epic

For each created child Epic, use `mcp__claude_ai_Atlassian__createIssueLink`:

```
cloudId:      <from jira.json>
type:         "Relates"
inwardIssue:  <child Epic key>   (e.g. "MES-6")
outwardIssue: <parent Epic key>  (e.g. "MES-1")
```

This creates a "relates to" link visible in both issue detail views.

If a child Epic depends on another child Epic (from the dependency column in
Step 3), also link them with `type: "Blocks"`:

```
inwardIssue:  <dependency Epic key>   (e.g. "MES-6" — IAM)
outwardIssue: <dependent Epic key>    (e.g. "MES-9" — Work Orders)
```

This means "Work Orders is blocked by IAM" — correct Jira Blocks semantics.

---

### Step 7 — Post summary comment on the programme Epic

Use `mcp__claude_ai_Atlassian__addCommentToJiraIssue` on the parent Epic:

```markdown
## spec-kit Programme Breakdown

This Epic has been decomposed into N child Epics by the spec-kit agent.

| Key   | Domain                      | Phase | Priority |
|-------|-----------------------------|-------|----------|
| MES-6 | IAM & Security (Keycloak)   | P1    | Highest  |
| MES-7 | System Activity Logging     | P1    | Highest  |
| ...   | ...                         | ...   | ...      |

All child Epics are linked via "Relates" and labelled `programme:MES-1`.

**Next step for each child Epic (in phase order):**
Run `/speckit-from-jira <child-key>` in Claude Code to generate a
feature specification, then `/speckit-plan` → `/speckit-tasks` →
`/speckit-taskstoissues`.
```

---

### Step 8 — Write a programme index file

Create `specs/programme-[PARENT_KEY_LOWER]/index.md`
(e.g. `specs/programme-mes-1/index.md`):

```markdown
# MikeMES Programme: [Programme Epic Title]

**Jira Epic**: [PARENT_KEY] — artical.atlassian.net
**Decomposed**: <ISO 8601 date>
**Constitution version**: 1.0.0

## Child Epics (in delivery order)

| Phase | Jira Key | Domain | Status |
|-------|----------|--------|--------|
| P1    | MES-6    | IAM & Security (Keycloak) | Not started |
| P1    | MES-7    | System Activity Logging   | Not started |
| ...   | ...      | ...                       | ...         |

## Workflow for each child Epic

1. `/speckit-from-jira <key>` — import Epic, generate spec, review
2. `/speckit-plan` — generate implementation plan
3. `/speckit-tasks` — generate task list
4. `/speckit-taskstoissues` — push tasks to Jira as children of child Epic

## Deferred domains

[List any domains excluded from scope, with reason]
```

This file becomes the programme-level navigation document inside the repo.

---

### Step 9 — Output final summary

```
Programme breakdown complete.

Parent Epic:  [KEY] — [title]
Child Epics:  N created (P1: N, P2: N, P3: N, P4: N, P5: N)
Programme index: specs/programme-[key]/index.md
Jira comment: posted on [KEY]

Start with Phase 1 — run these commands in order:
  /speckit-from-jira MES-6    (IAM & Security)
  /speckit-from-jira MES-7    (System Activity Logging)
  /speckit-from-jira MES-8    (Multi-Organisation Framework)

Once each spec is reviewed and approved, run /speckit-plan to begin planning.
```

---

## Important constraints

- **Never create child Epics without explicit user approval** (Step 4 gate).
- **Never create more than one Epic per functional domain** — if the user's
  description suggests splitting a domain, ask for clarification first.
- **Always create P1 Epics first** — they are dependencies for everything else
  and need to exist in Jira before dependent Epics are linked to them.
- The programme Epic itself is **never converted to a spec** — it is a planning
  artifact only. Specs are written for child Epics via `/speckit-from-jira`.
