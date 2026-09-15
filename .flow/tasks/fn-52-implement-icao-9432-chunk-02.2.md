# fn-52-implement-icao-9432-chunk-02.2 Model start-up approval to engine-start evidence

## Description

Model or expose evidence for ATC start-up approval followed by pilot engine
start, without inferring engine start from mission-step completion alone.

## Acceptance

- [ ] The design decides whether engine start is explicit lifecycle state or an evidence-only projection.
- [ ] Approval and start observations are orderable in sim time/evidence sequence.
- [ ] Reversal and initial-state implications of any `engineRunning` change are audited.
- [ ] Source-mapped test cites `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`.

## Done summary

## Evidence

- Commits:
- Tests:
- PRs:
