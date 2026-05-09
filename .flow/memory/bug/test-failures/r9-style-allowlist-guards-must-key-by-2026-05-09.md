---
title: "R9-style allowlist guards must key by directory path, not file content"
date: "2026-05-09"
track: bug
category: test-failures
module: migration/src/jvmTest/kotlin/xyz/easiersaid/twr/migration/world/CtrApproximationRadiusLoaderTest.kt
tags: [testing, guardrail, allowlist, trust-boundary]
problem_type: test-failure
symptoms: R9 future-airport guard could be bypassed by copy-pasted JSON impersonating an allowlisted ICAO
root_cause: Allowlist keyed by document.aerodrome.icao (untrusted content) instead of rendered-directory name (authoritative contract)
resolution_type: fix
---

## Problem
A real-airport authoring guardrail (R9) keyed its allowlist by the JSON's
self-declared `aerodrome.icao` instead of the rendered-directory name.
A new directory `cad/airports/rendered/abcd/world-candidate.json` could
ship a copy-pasted JSON whose content still said `"icao": "LOWG"` —
the guardrail would treat it as LOWG, find LOWG=18 in the allowlist,
and silently pass for the untracked airport `abcd`. The "every new
rendered airport requires deliberate review + plan-review" semantics
were defeated by JSON content impersonating an allowlisted airport.

## What Didn't Work
The original R9 test scanned `cad/airports/rendered/<dir>/` for
`world-candidate.json` files but then trusted the JSON's own ICAO
field (`document.world.aerodrome.icao`) as the lookup key. This is
the same shape as trusting user-controlled input as a lookup key
when the trust boundary is the directory layout, not the JSON
payload.

## Solution
Key the allowlist by `dir.uppercase()` (the directory name is the
authoritative contract; the JSON content is the data being validated
against it). Assert `document.world.aerodrome.icao == dirIcao` BEFORE
the allowlist lookup, so a copy-pasted/stale-ICAO JSON fails fast
with a clear directive rather than silently impersonating another
airport. All error messages interpolate `$dirIcao` (directory-derived,
trusted), not `$icao` (JSON-derived, untrusted).

See `migration/src/jvmTest/kotlin/.../CtrApproximationRadiusLoaderTest.kt:138-201`
for the hardened pattern.

## Prevention
When a test scans the filesystem for an allowlist match, the
authoritative key is the filesystem path component (directory or
filename), not a field inside the file's content. The content is
the data being validated; the path is the contract. Cross-check the
content against the path BEFORE the allowlist lookup so a
mis-authored or copy-pasted file fails fast with a directive, not
silently by impersonating an allowlisted entry. Pattern applies to
any "every X under directory Y must be in allowlist Z" guard.
