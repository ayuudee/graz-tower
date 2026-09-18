# ICAO 9432 implementation roadmap red team

Status: red-teamed and incorporated.

## Red-team brief

Attack `implementation_roadmap.md` as if it is about to become the next
multi-week work programme. Look for:

- places where the roadmap optimizes blocker counts instead of mission value;
- places where phraseology, policy, or model gaps might be greened with weak
  evidence;
- overlarge epics that hide multiple distinct risks;
- places where local procedure could be mistaken for universal law;
- hidden complexity that would make tests ceremonial rather than legible;
- missing review considerations required by AGENTS.md.

## Findings

### Blocking: emergency phraseology was scheduled before emergency state exists

The first `PHRASE-1A` proof set included emergency wording. Chunk 08 expected
gaps make clear that emergency phraseology cannot be honestly tested until
emergency state and message payload evidence exist.

Resolution requirement: remove emergency wording from the initial phraseology
proof set. Emergency phraseology remains blocked until emergency state/payload
exists and phraseology evidence can prove the wording.

### Blocking: `POLICY-1` was too broad

Policy buckets without a per-source contract invite local procedure to become
universal law.

Resolution requirement: require a per-source policy matrix and forbid green
movement merely because a default policy chooses one valid branch.

### Blocking: child epics could be created from an uncorrected plan

The first fn-59 acceptance made follow-on epics a deliverable but did not state
that review/red-team constraints had to be encoded in them.

Resolution requirement: child epics may only be created after material findings
are incorporated into the roadmap or carried as executable acceptance criteria.

### Blocking: blocker counts are not execution guidance because blockers overlap

Raw family counts make the work look like disjoint queues, but chunk 08 and
other chunks contain overlapping model, policy, and phraseology blockers.

Resolution requirement: use `implementation_blocker_manifest.csv` as the
overlap-preserving starting guardrail for child epics.

### Blocking: phraseology assertions were underspecified for semantic obligations

Mandatory word checks are not enough for units where wording must not imply a
different operational clearance.

Resolution requirement: use structured rendered tokens with provenance and
negative semantic classifiers for forbidden meanings. Do not green
ambiguity/ordering units with substring checks or full-string snapshots.

### Blocking: vehicle and pushback scopes hid multiple domain models

Vehicle work bundled identity, routing, runway crossing, towing metadata,
shared radio evidence, and vacated geometry.

Resolution requirement: split vehicle work into movement/permission lifecycle,
runway crossing/occupancy, and towing/vacated geometry.

### Blocking: evidence substrate could green architecture instead of source claims

The first roadmap did not identify exact source-unit proof sets for substrate
epics.

Resolution requirement: each child epic must include exact source-unit buckets:
green-targeted, expected-to-remain-blocked, and intentionally untouched.

## Resolution

Resolved in `implementation_roadmap.md` and fn-59 Flow acceptance. The revised
plan removes emergency wording from `PHRASE-1A`, adds a policy matrix gate,
uses the generated blocker manifest to preserve overlaps, requires exact child
epic movement manifests, and splits vehicle/emergency work into smaller proof
surfaces.
