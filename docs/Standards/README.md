# Compliance reference standards

Formal reference material for compliance-driven development of Aurora MES. These are
the external industry standards the system is built to satisfy (Constitution §IV
*Compliance by Design* and the Compliance Register), surfaced here as the authoritative
source when implementing or reviewing any compliance-bearing feature.

## ⚠ Copyright — local-only, never committed

The standards below are **commercial, copyrighted documents** published by SAE
International / the IAQG. This repository is **public**, so the PDF copies kept in this
folder are **git-ignored** (`docs/Standards/*.pdf`) and **must not** be committed or
uploaded — doing so would redistribute licensed material. Only this index is tracked.

Each developer must obtain their own licensed copies (e.g. from SAE / an IAQG member
distributor) and place them in this folder locally. Filenames the tooling/index expect:
`AS9100D.pdf`, `AS9102C.pdf`, `AS9131D.pdf`, `AS9145.pdf`.

## Standards index

| File | Standard | Scope (what it governs) | Primary use in Aurora MES |
|---|---|---|---|
| `AS9100D.pdf` | **AS9100D** — Quality Management Systems – Requirements for Aviation, Space & Defense Organizations | The overarching aerospace QMS (builds on ISO 9001): documented-information control, process control, traceability, configuration management. | Cross-cutting. Constitution §IV / Compliance Register; cited by item-master (MES-8), BOM, routing (MES-9, §8.5.1), work instructions (MES-10, §8.5), quality (MES-12). |
| `AS9102C.pdf` | **AS9102C** — Aerospace First Article Inspection (FAI) Requirement | Structure and required content of First Article Inspection Reports (FAIR) — characteristic accountability, forms 1–3. | Quality / inspection planning (MES-4 / MES-12): inspection characteristics and FAI reporting. |
| `AS9131D.pdf` | **AS9131D** — Nonconformance Data Definition & Documentation | Standardised data fields and definitions for recording nonconformances. | Nonconformance management (NCM) data model and dispositions. |
| `AS9145.pdf` | **AS9145** — Advanced Product Quality Planning (APQP) & Production Part Approval Process (PPAP) | The five APQP phases and PPAP element set for new product introduction. | Manufacturing engineering (MES-10) and NPI workflows; routing (MES-9) APQP linkage. |

> Clause references (e.g. "AS9100D §8.5.1") in specs, the constitution, and code comments
> point into these documents. Use the licensed PDFs above as the source of truth when a
> clause is cited — do **not** paste standard text into the repo, specs, code, or commit
> messages (paraphrase the requirement and cite the clause instead).

## When to consult these

- Writing or reviewing a spec's **Compliance References** section (Constitution §IV).
- Implementing a feature that touches a quality record, traceability, inspection,
  nonconformance, or configuration/document control.
- Resolving how a cited clause (e.g. "AS9100D §7.5") constrains a design decision.
