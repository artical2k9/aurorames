# MES Agent Error Log — Live

> **Purpose:** Non-promoted errors only. When an error is promoted (CLAUDE.md rule added + archive entry + index row), move it out of this file and mark the promotion date.
>
> **Promotion gate:** An error is ready to promote when: (a) the root cause is fully understood, (b) the fix has been applied and verified, and (c) a CLAUDE.md rule or memory entry would prevent recurrence.
>
> **Format per entry:**
> ```
> ## ERR-MES-NNN — <short title>
> **Date:** YYYY-MM-DD  **Category:** <category>  **Status:** Open | Promoted YYYY-MM-DD
> **Symptom:** What the agent observed.
> **Root cause:** Why it happened.
> **Fix applied:** What was done to resolve it.
> **Rule:** The rule that prevents recurrence (copied to CLAUDE.md or memory on promotion).
> ```

<!-- Add new errors below this line. Oldest at the top, newest at the bottom. -->

## ERR-MES-019 — ESLint flat config rejects `processor: angular.processInlineTemplates`
**Date:** 2026-05-20  **Category:** Frontend — ESLint  **Status:** Promoted 2026-05-20
**Symptom:** `ng lint` failed: `Config (unnamed): Key "processor": Expected an object or a string.` when `processor: angular.processInlineTemplates` was set in `eslint.config.js`.
**Root cause:** ESLint v9 flat config requires the `processor` field to be either a registered string (`"plugin/name"`) or a plain object with `preprocess`/`postprocess` methods. `angular.processInlineTemplates` as exported by `@angular-eslint/eslint-plugin` v21 is neither — ESLint rejects it.
**Fix applied:** Removed the `processor` line entirely. All project components use `templateUrl`, so inline template extraction is not needed; external `.html` files are linted in the separate HTML config block.
**Rule:** In Angular ESLint flat config, do not set `processor: angular.processInlineTemplates` — it is rejected. The inline template processor is only needed for components with inline `template:` strings; if all components use `templateUrl`, omit the processor entirely.
