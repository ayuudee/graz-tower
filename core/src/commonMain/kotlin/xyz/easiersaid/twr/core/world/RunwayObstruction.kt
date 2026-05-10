package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.SimTime

/**
 * A typed declaration that a runway is unavailable for landing/take-off until
 * a specified deadline. Carried on [Runway.obstruction] (rich-domain on the
 * entity per fn-12 Decision #2) and consumed by the controller's reactive
 * obstruction-GA rule.
 *
 * The shape is deliberately opaque (no kind variant such as Vehicle, Wildlife,
 * Debris, SurfaceContamination — those are deferred to
 * `D-PASS-g3a-obstruction-kind-variants` per `feedback_no_corners.md`). v1
 * carries only [clearsAt]; the controller treats every obstruction the same
 * way (issue go-around, gate landing clearance) regardless of cause.
 *
 * **`clearsAt` immutability invariant** (fn-12 Decision #4). Once
 * `runway.obstruction = Some(RunwayObstruction(clearsAt = T))` is set, **no
 * code path mutates the inner `clearsAt`** for the lifetime of that
 * obstruction. The only allowed transitions are:
 *  - `None → Some(new)` — initial set (test fixture / world authoring path).
 *  - `Some → None` — expiry pass nulls it when `clearsAt <= now`.
 *
 * The sim's per-cycle world-diff producer relies on this invariant to remain
 * edge-only (Detected on `None → Some`, Cleared on `Some → None`). A
 * `Some(old) → Some(new)` transition is not modelled — to extend or shorten
 * an obstruction, the world must null the field first and re-set on a later
 * cycle. The diff producer's `check(...)` contract throws on invariant
 * violation rather than silently dropping the change. See
 * `D-PASS-g3a-obstruction-clearsAt-update` for the future relaxation.
 *
 * Authoring helpers (e.g. test fixtures) MUST respect the invariant.
 */
data class RunwayObstruction(val clearsAt: SimTime)
