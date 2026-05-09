# 2026-04-24 Repeated Structure Attempts Improve Nested ICAO 4444 Parsing

## Decision

Use repeated Ollama structure attempts plus an Ollama reconciliation pass for
nested `ICAO 4444` clause families.

## Why

The transfer-family run over `4.3.2.1.1`-`4.3.2.1.3` showed that a single
structure pass can flatten nested enumerations. The repeated-attempt workflow
preserved the separate `b)` branch and nested numbered items, then improved the
final adjudication results.

## Evidence

- [icao4444-ollama-first-structure-reconciliation-pass-2026-04-24.md](docs/design/icao4444-ollama-first-structure-reconciliation-pass-2026-04-24.md)
- [transfer summary](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/summary.md)
- [transfer structure reconciliation](/tmp/icao4444-ollama-first-prototype-transfer-structure-consistency-v4/structure_reconciliation_response.json)

## Consequence

The default prototype workflow is now:

1. repeated structure attempts
2. structure reconciliation
3. repeated extraction attempts
4. extraction reconciliation
5. challenge / defense / judge

The next quality issue is authority/modality discipline in the challenger, not
raw structure preservation.
