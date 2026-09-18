# Acceptance Criteria

- Rendered vehicle first-call evidence covers source unit
  `7759017903acf140` with call sign, position, destination, and ordered route.
- Rendered tow-request metadata evidence covers source unit
  `4b103081585bfb71` with addressee/receiving station, aircraft under tow,
  aircraft type, and operator.
- Vehicle/tow phraseology matching is typed and rejects mismatched or malformed
  evidence loudly in selector tests.
- Chunk 07 docs and the central blocker manifest no longer list these two
  rendered wording branches as open `PHRASE-1` residuals.
- Remaining chunk 07 policy/model blockers stay explicit and unchanged.
- Focused tests, broad relevant tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.
