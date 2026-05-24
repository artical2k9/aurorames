# Codeburn — Developer Setup

[Codeburn](https://github.com/getagentseal/codeburn) tracks AI token spend across Claude Code sessions.
It reads session files from your local machine (`~/.claude/projects/`) — no API keys required.

## One-time setup

### 1. Install globally

```bash
npm install -g codeburn
```

Requires Node.js 20+.

### 2. Set currency to GBP

```bash
codeburn currency GBP
```

### 3. Set subscription plan

```bash
codeburn plan set claude-pro
```

### 4. Verify the MikeMES project name

Codeburn derives project names from the session file path. The confirmed name for this repo is:

```
Documents/GitHub/MikeMES
```

`scripts/feature-cost.ps1` derives this automatically from the repo path — no further action needed
unless you clone to a different directory.

## Per-PR Usage Cost report

Before raising any PR, run from the repo root:

```powershell
.\scripts\feature-cost.ps1
```

Copy the markdown output and paste it into the `## Usage Cost` section of the PR description.

The script calculates the spend from the date the branch diverged from `Develop` to today.

## Manual queries

The global `codeburn` binary accepts any date range:

| Command | Description |
|---------|-------------|
| `codeburn` | All projects, last 7 days — interactive TUI |
| `codeburn --period month` | All projects, this calendar month |
| `codeburn --project "Documents/GitHub/MikeMES" --period month` | MikeMES only, this month |
| `codeburn --from 2026-01-01 --to 2026-12-31` | All projects, custom date range |
| `codeburn optimize` | Identify token waste patterns |
| `codeburn compare` | Compare model cost efficiency |
