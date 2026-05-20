# Inventory accepted ICAO 9432 source units

## Description
Build the complete accepted `icao9432-extracted` inventory from the source-unit registry and previous curation outputs. The output should be a durable artifact under `research/tools/requirements-spike/quality/icao9432_programme/`, not just terminal notes.

Capture at minimum: source unit id, section/window, title/summary if available, quote/claim summary, registry status, and links back to the source JSON. Include counts by section so we can tell whether later chunk planning covers all accepted units.

## Acceptance Criteria
- [ ] The inventory contains every accepted `icao9432-extracted` source unit currently present in the registry.
- [ ] The inventory has stable source-unit ids and section/window names.
- [ ] The inventory includes enough text/summary context to classify without reopening every JSON file.
- [ ] Counts by section are reported.
- [ ] Pending/rejected ICAO 9432 units are not mixed into the accepted programme, but their counts are noted separately if easy to derive.
