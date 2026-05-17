---
satisfies: [R3]
---

## Description

Populate kotest 5.9.1 artifacts in the user's `~/.gradle/caches/modules-2` so `:sim:compileTestKotlinJvm` runs offline. fn-26.1's worker fetched kotest via `curl` into a sandbox-local Maven layout that isn't persisted to the user's real cache; offline gradle fails with `No cached version of io.kotest:kotest-framework-engine:5.9.1 available for offline mode`.

**Size:** S (single command; user-runnable in their normal terminal)
**Files:** None changed. This is a runtime cache populate, not a code edit.

## Approach

Run ONCE in a terminal with network access (NOT the agent sandbox). **Use the project wrapper `./gradlew`** for reproducibility (the wrapper pins the gradle version; the Nix-provided `gradle` may drift):

```bash
nix --extra-experimental-features 'nix-command flakes' develop --no-write-lock-file -c ./gradlew :sim:compileTestKotlinJvm
```

Gradle resolves kotest 5.9.1 + transitive deps from Maven Central and persists them under `~/.gradle/caches/modules-2/files-2.1/io.kotest/`. Thereafter the offline formula from commit `d32b8b8` works for `:sim` (note: that formula uses Nix-provided `gradle` because `./gradlew` cannot extract its distribution under the sandbox's write-restricted `~/.gradle`).

## Investigation targets

**Required**:
- `sim/build.gradle.kts` — kotest dependency declaration (verify version)
- `gradle/libs.versions.toml` (if present) — version catalog entry

## Key context

- This is the **only** blocker keeping `:sim:jvmTest` from running in the sandbox; once the cache is populated, the offline formula works.
- User-side action; the agent can't run online.
- Single command. If it fails, network / Maven Central reachable from the user's machine needs investigation.
- **Wrapper vs Nix gradle**: this fetch task uses `./gradlew` (project pin); the sandbox offline-verify formula uses Nix-provided `gradle` (sandbox can't write to `~/.gradle/wrapper/dists/`). Both resolve to gradle 8.14.4. Different invocations for different environments — both intentional.

## Acceptance

- [ ] `./gradlew :sim:compileTestKotlinJvm` succeeds online (one-time fetch)
- [ ] Verify offline works after: `gradle :sim:compileTestKotlinJvm --offline --no-daemon` (via the nix-shell formula) succeeds
- [ ] `ls ~/.gradle/caches/modules-2/files-2.1/io.kotest/` shows kotest-framework-engine + kotest-assertions-core + kotest-property + kotest-runner-junit5 subdirs

## Review considerations

- **Impact**: cache populate only — no source / test changes
- **FP / type safety**: N/A
- **Test architecture**: unblocks the existing `:sim:jvmTest` lineup; no test changes
- **Operational ATC correctness**: N/A

## Done summary
kotest 5.9.1 cache populated via user-side online `./gradlew :sim:compileTestKotlinJvm` (one-time fetch). BUILD SUCCESSFUL. Sandbox-offline gradle now resolves kotest-framework-engine + kotest-assertions-core + kotest-property + kotest-runner-junit5 via GRADLE_RO_DEP_CACHE → ~/.gradle/caches/modules-2/.
## Evidence
- Commits:
- Tests:
- PRs: