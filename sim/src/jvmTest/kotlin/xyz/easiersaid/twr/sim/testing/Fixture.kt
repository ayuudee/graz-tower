package xyz.easiersaid.twr.sim.testing

import arrow.core.Either
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.core.world.buildWorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.sim.ControllerSpec
import xyz.easiersaid.twr.sim.SimEvent

/**
 * Per-aerodrome test fixture. Bundles inputs to load a world candidate, patch
 * placeholder roles + authorities (pre-Pass-10 shim — Pass 10 will read these
 * from the manifest), and produce a fully-realised `(AviationWorld, WorldIndex)`
 * pair plus per-role [ControllerSpec]s.
 *
 * Immutable data class; sharing `val` references across tests is safe.
 *
 * Pass 10 evolution: the `weather` and `controllerRoles` fields are absorbed
 * by manifest-driven loading and removed; the [Fixture] data class itself
 * survives with shrunk body. Call surface ([load], [validate]) is unchanged.
 */
data class Fixture(
    val aerodromeId: AerodromeId,
    val candidatePath: Path,
    val standPointId: PointId,
    val frequency: Frequency,
    val weather: WeatherObservation,
    val controllerRoles: Set<RoleName>,
    /**
     * Pass 11 (D-AUDIT.6 / D-AUDIT.10): filed plans per aircraft. Each
     * entry produces N `SimEvent.FlightPlanFiled` events at sim-init via
     * [load], sorted by aircraft id for deterministic seq-assignment
     * downstream. Replaces the pre-Pass-11 `groundResponsibilities`
     * direct-injection cheat.
     *
     * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): the value type
     * is `FiledPlan` (not `FiledPlanForFixture(plan, recipient)`). The
     * recipient list is computed by
     * [xyz.easiersaid.twr.sim.AftnRouting.routeFiledPlan] from the plan
     * and the world's published roles — single-aerodrome plans get one
     * recipient (departure-side); cross-aerodrome plans fan out to two
     * (departure + destination).
     */
    val flightPlans: Map<AircraftId, FiledPlan> = emptyMap(),
    /**
     * fn-8.1 (G1 foundation): per-aircraft start points for multi-aircraft
     * fixtures.
     *
     * **Asymmetry intentional.** Single-aircraft fixtures (G0 LOWG, G2
     * LJMB) carry [standPointId] and leave [startPoints] null — the field
     * exists so multi-aircraft fixtures (G1 LOWG_TWO_AIRCRAFT) can author
     * one start point per aircraft without an Option-C unification of the
     * single-aircraft shape (deferred). Tests in multi-aircraft mode must
     * reach for [requiredStartPoints] (loud-fail on null) instead of
     * [standPointId].
     *
     * **Validation.** When non-null, [LoadedFixture.validate] enforces:
     *  - every aircraft id in [startPoints] is also in [flightPlans]
     *    ([FixtureViolation.StartPointWithoutFlightPlan])
     *  - every aircraft id in [flightPlans] has a [startPoints] entry
     *    ([FixtureViolation.FlightPlanMissingStartPoint])
     *  - no two aircraft share a start `pointId`
     *    ([FixtureViolation.DuplicateStartPoint])
     *  - every authored `pointId` exists in `worldIndex.positions`
     *    ([FixtureViolation.StartPointMissing]).
     *
     * Single-aircraft fixtures keep the existing [standPointId] check
     * via [FixtureViolation.StandPointMissing]; the new violations only
     * fire when [startPoints] is non-null.
     */
    val startPoints: Map<AircraftId, PointId>? = null,
)

/**
 * fn-8.1: non-null-asserting accessor for [Fixture.startPoints].
 *
 * Multi-aircraft tests need a non-null map. Returning the nullable field
 * directly forces every call site to either re-assert non-null or take
 * the NPE on `null.getValue(id)`. This helper pushes the failure to a
 * single, loud, source-cited error.
 *
 * Single-aircraft fixtures (G0/G2) continue to use [Fixture.standPointId]
 * and never call this helper.
 */
fun Fixture.requiredStartPoints(): Map<AircraftId, PointId> =
    startPoints
        ?: error(
            "Fixture for ${aerodromeId.value} ($candidatePath) has no startPoints — " +
                "this is a single-aircraft fixture (use standPointId). " +
                "Multi-aircraft tests need a fixture authoring per-aircraft startPoints.",
        )

/**
 * Result of loading a [Fixture]. Immutable; callers consume and discard.
 *
 * G2 Phase A (D-AUDIT.12 follow-up): [controllers] is keyed by [ControllerId]
 * (not [RoleName]) so multi-aerodrome fixtures can stage controllers that
 * share a [RoleName] across aerodromes (e.g. `LOWG_TOWER` and `LJMB_TOWER`).
 * Single-aerodrome consumers use [controllerByRole] for the same lookup
 * ergonomics as the previous shape.
 */
data class LoadedFixture(
    val world: AviationWorld,
    val worldIndex: WorldIndex,
    /**
     * Keyed by [ControllerId] (e.g. `LOWG_TOWER`, `LJMB_TOWER`). Use
     * [controllerByRole] for single-aerodrome role-keyed lookup, or
     * [controllerAt] for explicit `(aerodromeId, role)` lookup in
     * multi-aerodrome contexts.
     */
    val controllers: Map<ControllerId, ControllerSpec>,
    /**
     * Pass 11 (D-AUDIT.6): events the test driver should enqueue at
     * sim-start. One [SimEvent.FlightPlanFiled] per aircraft in
     * [Fixture.flightPlans]. Sorted by `AircraftId.value` ascending so
     * the seq-assignment in `step()` is deterministic across runs.
     */
    val initialEvents: List<SimEvent> = emptyList(),
)

/**
 * Single-aerodrome convenience: find the unique controller staffing the given
 * role. **Programming error** if more than one controller has the same role
 * (multi-aerodrome case) — the type system allows it, but the precondition
 * is "single-aerodrome `LoadedFixture`". Use [controllerAt] in multi-aerodrome
 * code paths.
 *
 * Returns [null] when no controller has the role; throws
 * [IllegalStateException] with a rich diagnostic when multiple do.
 *
 * **Future scope (deferred):** splitting [LoadedFixture] into
 * `SingleAerodromeLoadedFixture` and `MultiAerodromeLoadedFixture` variants
 * would make the precondition unrepresentable at the type level. Out of
 * scope for G2 Phase A; the function's runtime check is the floor.
 */
fun LoadedFixture.controllerByRole(role: RoleName): ControllerSpec? {
    val matches = controllers.values.filter { it.role == role }
    return when (matches.size) {
        0 -> null
        1 -> matches.single()
        else -> error(
            "controllerByRole($role) is ambiguous in a multi-aerodrome fixture: " +
                "${matches.map { it.id.value }}. Use controllerAt(aerodromeId, role) instead.",
        )
    }
}

/** Multi-aerodrome explicit lookup: returns the controller at `(aerodromeId, role)` or null. */
fun LoadedFixture.controllerAt(aerodromeId: AerodromeId, role: RoleName): ControllerSpec? =
    controllers[ControllerId("${aerodromeId.value}_${role.name}")]

/** Sealed error hierarchy for fixture loading. */
sealed interface LoadError {
    data class FileMissing(val path: Path) : LoadError
    data class MalformedJson(val path: Path, val reason: String) : LoadError
    data class ValidationFailed(val violations: List<FixtureViolation>) : LoadError

    /**
     * G2 Phase A: [xyz.easiersaid.twr.sim.AftnRouting.routeFiledPlan] failed for
     * a filed plan in the fixture. Replaces the previous `error()` throw at
     * `Fixture.load()` so the whole load pipeline returns `Either.Left`
     * uniformly. Single-aerodrome and multi-aerodrome fixtures share this leaf.
     */
    data class RoutingFailed(
        val aircraft: AircraftId,
        val failure: xyz.easiersaid.twr.sim.RoutingFailure,
    ) : LoadError

    /**
     * G2 Phase A: [xyz.easiersaid.twr.migration.world.WorldCandidateLoader.mergeAviationWorlds]
     * rejected the merge (e.g. an aerodrome lacks a hardcoded `referencePoint`
     * in `WorldCandidateLoader.kt`'s `REFERENCE_POINTS` table). Multi-aerodrome
     * fixtures only.
     */
    data class MergeFailed(val reason: String) : LoadError
}

/** Sealed sanity-check violations. */
sealed interface FixtureViolation {
    data class StandPointMissing(val pointId: PointId) : FixtureViolation
    data class AerodromeMissing(val id: AerodromeId) : FixtureViolation
    data class NoRunways(val id: AerodromeId) : FixtureViolation

    /**
     * The fixture asks for a role the world-candidate doesn't publish.
     * Pass 6 (D-AUDIT.12 closure) replaces the silent fixture patch:
     * before, missing roles got injected; after, they fail loudly.
     */
    data class RoleNotPublished(val role: RoleName, val aerodrome: AerodromeId) : FixtureViolation

    /**
     * The fixture's expected frequency for a published role disagrees with
     * what the world-candidate publishes. Pass 6 (D-AUDIT.12 closure)
     * replaces the silent fixture overwrite with this typed mismatch.
     */
    data class FrequencyMismatch(
        val aerodrome: AerodromeId,
        val role: RoleName,
        val delta: FrequencyDelta,
    ) : FixtureViolation

    /**
     * fn-8.1: a [Fixture.startPoints] entry references an [AircraftId] that
     * is not in [Fixture.flightPlans]. The aircraft has no filed plan; the
     * sim-init `FlightPlanFiled` event would never fire and the aircraft
     * would never be recognised by the controller.
     */
    data class StartPointWithoutFlightPlan(
        val aircraft: AircraftId,
        val point: PointId,
    ) : FixtureViolation

    /**
     * fn-8.1: a [Fixture.flightPlans] entry has no corresponding
     * [Fixture.startPoints] entry. Only fires when [Fixture.startPoints] is
     * non-null — single-aircraft fixtures using [Fixture.standPointId]
     * are unaffected.
     */
    data class FlightPlanMissingStartPoint(val aircraft: AircraftId) : FixtureViolation

    /**
     * fn-8.1: two aircraft authored at the same start [PointId]. The
     * `SimState.initial` smart constructor would still accept this (its
     * `AircraftPositionPointNotInIndex` check is per-aircraft, not
     * pair-wise), but kinematics layered on top would have two aircraft
     * occupying one geometry point. The fixture-load layer is the right
     * place to catch this — clearer error than a runtime symptom.
     */
    data class DuplicateStartPoint(
        val point: PointId,
        val aircraft: List<AircraftId>,
    ) : FixtureViolation

    /**
     * fn-8.1: a [Fixture.startPoints] entry's [PointId] is not in
     * `worldIndex.positions`. Mirrors [StandPointMissing] but for the
     * multi-aircraft authoring path.
     */
    data class StartPointMissing(
        val aircraft: AircraftId,
        val point: PointId,
    ) : FixtureViolation
}

/**
 * Named diff between expected and published frequencies. Pass 6 (FP review M.4):
 * future-proofs for 8.33 kHz channelisation tolerance — the consumer can read
 * [deltaKhz] without reaching into the pair.
 *
 * Pass 6 post-impl review (FP-P.2): [deltaKhz] returns [Option] rather than a
 * `Int.MAX_VALUE` sentinel. Returning a magic sentinel from a "total" function
 * is a partial function in disguise; the typed `Option` makes the parse-failure
 * branch explicit. The `Frequency` smart constructor enforces the numeric
 * invariant, so in practice this is `Some` for every well-constructed value —
 * but defensive code at the boundary deserves the explicit shape.
 */
data class FrequencyDelta(val expected: Frequency, val published: Frequency) {
    val deltaKhz: arrow.core.Option<Int> get() {
        val expMhz = expected.mhz.toDoubleOrNull() ?: return arrow.core.None
        val pubMhz = published.mhz.toDoubleOrNull() ?: return arrow.core.None
        return arrow.core.Some(((expMhz - pubMhz) * 1000.0).toInt())
    }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Load the fixture's world candidate, patch role/authority/weather, and build
 * a [LoadedFixture]. Returns [Either.Left] for missing files, malformed JSON,
 * or validation failures. Total: never throws.
 */
fun Fixture.load(): Either<LoadError, LoadedFixture> {
    val file = candidatePath.toFile()
    if (!file.exists()) return Either.Left(LoadError.FileMissing(candidatePath))

    val world = try {
        val doc = json.decodeFromString<WorldCandidateDocument>(Files.readString(candidatePath))
        WorldCandidateLoader.toWorld(doc)
    } catch (e: SerializationException) {
        return Either.Left(LoadError.MalformedJson(candidatePath, e.message ?: "unknown"))
    } catch (e: IllegalArgumentException) {
        return Either.Left(LoadError.MalformedJson(candidatePath, e.message ?: "unknown"))
    }

    // Pass 6 (D-AUDIT.12 closure): roles come from the world-candidate. The
    // previous post-load `ad.copy(roles = patchedRoles)` patch is gone — if
    // the JSON doesn't publish a role the fixture asks for, validate() emits
    // RoleNotPublished. If frequencies disagree, FrequencyMismatch.

    val worldIndex = world.buildWorldIndex()

    // Pass 11 (D-AUDIT.6 / D-AUDIT.10): controllers start with empty
    // responsibilities. Aircraft enter via SimEvent.FlightPlanFiled at
    // sim-start (returned in LoadedFixture.initialEvents) — never via
    // direct fixture injection. Calls the bare ControllerSpec ctor:
    // `withOwned(ownedAircraft = emptySet())` would read as a wiring
    // leftover from the pre-Pass-11 cheat shape (Pass 11 post-impl S.1).
    // G2 Phase A: rekey by ControllerId so multi-aerodrome fixtures can stage
    // controllers that share a RoleName across aerodromes (e.g. LOWG_TOWER and
    // LJMB_TOWER). Single-aerodrome consumers use controllerByRole(role) for
    // the same lookup ergonomics.
    val controllers: Map<ControllerId, ControllerSpec> = controllerRoles.associate { role ->
        val id = ControllerId("${aerodromeId.value}_${role.name}")
        id to ControllerSpec(
            id = id,
            role = role,
            aerodromeId = aerodromeId,
            frequency = frequency,
            responsibilities = emptyMap(),
        )
    }

    // Pass 11 (D-AUDIT.6): emit FlightPlanFiled events per filed plan.
    // Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): each plan
    // fans out to N recipients via routeFiledPlan — single-aerodrome
    // → 1 recipient (departure side); cross-aerodrome → 2 (departure +
    // destination). Sort by AircraftId.value ascending, then iterate
    // recipient list in order, so seq-assignment downstream is
    // deterministic across runs.
    // G2 Phase A: routing failure now surfaces as Either.Left(RoutingFailed)
    // for uniformity with the rest of LoadError. Previously this was an
    // error() throw which broke the Either contract.
    //
    // routeFiledPlan returns NonEmptyList<AftnAddress>; iterate it directly
    // rather than .toList() to preserve the cardinality invariant — empty
    // recipients would mean the loader silently emitted zero filings.
    val initialEvents = mutableListOf<SimEvent>()
    for ((aircraftId, plan) in flightPlans.entries.sortedBy { it.key.value }) {
        val routingResult = xyz.easiersaid.twr.sim.AftnRouting
            .routeFiledPlan(plan) { aerodromeId ->
                world.aerodromes[aerodromeId]?.roles?.keys.orEmpty()
            }
        val recipients = when (routingResult) {
            is Either.Left -> return Either.Left(LoadError.RoutingFailed(aircraftId, routingResult.value))
            is Either.Right -> routingResult.value
        }
        for (recipient in recipients) {
            initialEvents.add(SimEvent.FlightPlanFiled(
                time = xyz.easiersaid.twr.protocol.SimTime.ZERO,
                aircraft = aircraftId,
                plan = plan,
                recipient = recipient,
            ))
        }
    }

    val loaded = LoadedFixture(world, worldIndex, controllers, initialEvents.toList())
    val violations = loaded.validate(this)
    if (violations.isNotEmpty()) return Either.Left(LoadError.ValidationFailed(violations))
    return Either.Right(loaded)
}

/**
 * Sanity check the loaded fixture against expected preconditions. Returns
 * the full list of violations (not the first), so failure messages can
 * report every problem at once.
 */
fun LoadedFixture.validate(fixture: Fixture): List<FixtureViolation> = buildList {
    if (fixture.standPointId !in worldIndex.positions) {
        add(FixtureViolation.StandPointMissing(fixture.standPointId))
    }
    // fn-8.1: multi-aircraft start-point validation. Only fires when the
    // fixture authored startPoints; single-aircraft fixtures (startPoints
    // == null) keep the existing StandPointMissing-only check above.
    val starts = fixture.startPoints
    if (starts != null) {
        val planIds = fixture.flightPlans.keys
        for ((acId, ptId) in starts) {
            if (acId !in planIds) {
                add(FixtureViolation.StartPointWithoutFlightPlan(acId, ptId))
            }
            if (ptId !in worldIndex.positions) {
                add(FixtureViolation.StartPointMissing(acId, ptId))
            }
        }
        for (acId in planIds) {
            if (acId !in starts) {
                add(FixtureViolation.FlightPlanMissingStartPoint(acId))
            }
        }
        // Group start-point reverse-lookup → list of aircraft sharing it.
        // A single aircraft per point is the healthy case (group size 1);
        // anything ≥ 2 is a duplicate.
        starts.entries
            .groupBy({ it.value }, { it.key })
            .filter { it.value.size > 1 }
            .forEach { (pt, acs) ->
                add(FixtureViolation.DuplicateStartPoint(point = pt, aircraft = acs.sortedBy { it.value }))
            }
    }
    val ad = world.aerodromes[fixture.aerodromeId]
    if (ad == null) {
        add(FixtureViolation.AerodromeMissing(fixture.aerodromeId))
        return@buildList
    }
    if (ad.runways.isEmpty()) add(FixtureViolation.NoRunways(fixture.aerodromeId))
    fixture.controllerRoles.forEach { role ->
        val published = ad.roles[role]
        if (published == null) {
            add(FixtureViolation.RoleNotPublished(role, fixture.aerodromeId))
            return@forEach
        }
        if (published.frequency != fixture.frequency) {
            add(FixtureViolation.FrequencyMismatch(
                aerodrome = fixture.aerodromeId,
                role = role,
                delta = FrequencyDelta(expected = fixture.frequency, published = published.frequency),
            ))
        }
    }
}

// ── Multi-aerodrome fixture (G2 Phase A) ────────────────────────────────────

/**
 * Per-aerodrome staffing for a multi-aerodrome fixture. Each entry stages one
 * aerodrome's world candidate + the roles staffed at it + per-role frequencies.
 *
 * **Why per-role frequencies (not a single field).** LOWG_GROUND/LOWG_TOWER
 * share 118.200 but LOWG_APPROACH is on 119.300; LJMB_TOWER is on 119.205.
 * Single-aerodrome [Fixture] gets away with one `frequency` field because its
 * staffed roles share a freq (see [Fixtures.LOWG] today, which staffs only
 * GROUND + TOWER). Cross-aerodrome forces per-(aerodrome, role) frequency.
 *
 * G2 Phase A introduced this for `Fixtures.LOWG_LJMB_VFR`. The Option C
 * unification (single [Fixture] carrying `NonEmptyMap<AerodromeId,
 * AerodromeStaffing>`) is filed as a follow-up cleanup pass — keeping the
 * single-aerodrome [Fixture] shape unchanged here lets G0 (`LowgGoldenTest`)
 * stay green without a wider migration.
 */
data class AerodromeStaffing(
    val aerodromeId: AerodromeId,
    val candidatePath: Path,
    /**
     * Roles to staff at this aerodrome, with per-role frequency. Validation
     * compares these against `world.aerodromes[aerodromeId].roles[role].frequency`
     * and emits [FixtureViolation.FrequencyMismatch] on disagreement.
     *
     * Non-empty by construction — an aerodrome with no staffed roles is a
     * wiring defect.
     */
    val frequencyByRole: Map<RoleName, Frequency>,
    val weather: WeatherObservation,
) {
    init {
        require(frequencyByRole.isNotEmpty()) {
            "AerodromeStaffing for ${aerodromeId.value} requires at least one staffed role"
        }
    }
}

/**
 * Multi-aerodrome test fixture for cross-aerodrome scenarios (G2 LOWG → LJMB
 * transit). Loads each aerodrome's world candidate, merges them via
 * [WorldCandidateLoader.mergeAviationWorlds], staffs the requested controllers
 * with per-(aerodrome, role) frequencies, and routes filed plans through
 * [xyz.easiersaid.twr.sim.AftnRouting.routeFiledPlan] producing one
 * [SimEvent.FlightPlanFiled] per recipient (cross-aerodrome plans fan out to
 * 2 recipients automatically per Pass 14 routing topology).
 *
 * The single-aerodrome [Fixture] remains the right shape for G0 (single ATIS,
 * single frequency, one staffed-side); this sibling is for G2-shape scenarios
 * where the test exercises a flow across two airports.
 */
data class MultiAerodromeFixture(
    /**
     * Non-empty by type — an empty staffing list would mean a fixture with
     * no aerodromes, which is meaningless. `NonEmptyList` from Arrow encodes
     * this invariant in the type rather than a runtime require.
     */
    val staffing: arrow.core.NonEmptyList<AerodromeStaffing>,
    /**
     * The "ego" aircraft's starting point — usually a stand at the departure
     * aerodrome. Phase F's outcome assertion (`positionPoint ∈ LJMB stand
     * points`) reads [destinationStandPointId] for the equality check.
     */
    val standPointId: PointId,
    /** Where Phase F's outcome assertion expects the aircraft to end up. */
    val destinationStandPointId: PointId,
    /**
     * Per-aerodrome weather observations.
     *
     * **Currently unused** — staged for Phase F's `SimState.initial` which
     * accepts `weatherByAerodrome: Map<AerodromeId, WeatherObservation>`
     * directly (see `LowgGoldenTest.kt:91` for the single-aerodrome
     * precedent). G2 Phase A populates this for forward-compat; Phase F
     * will read it.
     */
    val weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
    val flightPlans: Map<AircraftId, FiledPlan> = emptyMap(),
) {
    init {
        // Distinct aerodromes — same aerodromeId staged twice is a wiring
        // defect. (NonEmptyList already encodes non-empty.)
        val ids = staffing.map { it.aerodromeId }
        require(ids.distinct().size == ids.size) {
            "MultiAerodromeFixture has duplicate aerodromeIds: $ids"
        }
    }
}

/**
 * Load each aerodrome's world candidate, merge into one [AviationWorld],
 * staff controllers per (aerodromeId, role), validate per-aerodrome, and
 * route filed plans. Total: never throws — every failure surfaces as
 * [Either.Left] with a typed [LoadError] leaf.
 */
fun MultiAerodromeFixture.load(): Either<LoadError, LoadedFixture> {
    // Step 1: load each aerodrome's world candidate.
    val perAerodromeWorlds = mutableListOf<AviationWorld>()
    for (entry in staffing) {
        val file = entry.candidatePath.toFile()
        if (!file.exists()) return Either.Left(LoadError.FileMissing(entry.candidatePath))
        val world = try {
            val doc = json.decodeFromString<WorldCandidateDocument>(Files.readString(entry.candidatePath))
            WorldCandidateLoader.toWorld(doc)
        } catch (e: SerializationException) {
            return Either.Left(LoadError.MalformedJson(entry.candidatePath, e.message ?: "unknown"))
        } catch (e: IllegalArgumentException) {
            return Either.Left(LoadError.MalformedJson(entry.candidatePath, e.message ?: "unknown"))
        }
        perAerodromeWorlds.add(world)
    }

    // Step 2: merge. mergeAviationWorlds requires every aerodrome to have a
    // hardcoded referencePoint in WorldCandidateLoader's REFERENCE_POINTS table.
    // LOWG and LJMB both qualify as of G1-DEF-11; future aerodromes need to be
    // added there before the merge can succeed.
    //
    // Catches both IllegalArgumentException (require failures inside
    // reprojectToSharedFrame: missing referencePoint, ENU offset cap exceeded)
    // and IllegalStateException (the empty-list error() path; unreachable
    // because of init-block require, but caught for type-level totality).
    val merged = try {
        WorldCandidateLoader.mergeAviationWorlds(perAerodromeWorlds.toList())
    } catch (e: IllegalArgumentException) {
        return Either.Left(LoadError.MergeFailed(e.message ?: "unknown"))
    } catch (e: IllegalStateException) {
        return Either.Left(LoadError.MergeFailed(e.message ?: "unknown"))
    }

    val worldIndex = merged.buildWorldIndex()

    // Step 3: stage one ControllerSpec per (aerodromeId, role) entry, keyed
    // by ControllerId (e.g. `LOWG_TOWER`, `LJMB_TOWER`). Walk staffing as a
    // plain List for the inner per-role flatMap; NonEmptyList.flatMap
    // requires the inner result to also be NonEmpty, which is over-constraint
    // here.
    val controllers: Map<ControllerId, ControllerSpec> = staffing
        .toList()
        .flatMap { entry ->
            entry.frequencyByRole.map { (role, freq) ->
                val id = ControllerId("${entry.aerodromeId.value}_${role.name}")
                id to ControllerSpec(
                    id = id,
                    role = role,
                    aerodromeId = entry.aerodromeId,
                    frequency = freq,
                    responsibilities = emptyMap(),
                )
            }
        }
        .toMap()

    // Step 4: route filed plans. Same surface as Fixture.load — single plan
    // with destinationAerodrome != departure produces 2 recipients (Pass 14).
    // Iterate routeFiledPlan's NonEmptyList directly to preserve cardinality.
    val initialEvents = mutableListOf<SimEvent>()
    for ((aircraftId, plan) in flightPlans.entries.sortedBy { it.key.value }) {
        val routingResult = xyz.easiersaid.twr.sim.AftnRouting
            .routeFiledPlan(plan) { aerodromeId ->
                merged.aerodromes[aerodromeId]?.roles?.keys.orEmpty()
            }
        val recipients = when (routingResult) {
            is Either.Left -> return Either.Left(LoadError.RoutingFailed(aircraftId, routingResult.value))
            is Either.Right -> routingResult.value
        }
        for (recipient in recipients) {
            initialEvents.add(SimEvent.FlightPlanFiled(
                time = xyz.easiersaid.twr.protocol.SimTime.ZERO,
                aircraft = aircraftId,
                plan = plan,
                recipient = recipient,
            ))
        }
    }

    val loaded = LoadedFixture(merged, worldIndex, controllers, initialEvents.toList())
    val violations = loaded.validateMultiAerodrome(this)
    if (violations.isNotEmpty()) return Either.Left(LoadError.ValidationFailed(violations))
    return Either.Right(loaded)
}

/**
 * Multi-aerodrome variant of [validate]. Walks each aerodrome's staffing and
 * checks roles + frequencies against the merged world. Reports every
 * violation — not the first — so failure messages name them all at once.
 */
fun LoadedFixture.validateMultiAerodrome(fixture: MultiAerodromeFixture): List<FixtureViolation> = buildList {
    if (fixture.standPointId !in worldIndex.positions) {
        add(FixtureViolation.StandPointMissing(fixture.standPointId))
    }
    if (fixture.destinationStandPointId !in worldIndex.positions) {
        add(FixtureViolation.StandPointMissing(fixture.destinationStandPointId))
    }
    for (entry in fixture.staffing) {
        val ad = world.aerodromes[entry.aerodromeId]
        if (ad == null) {
            add(FixtureViolation.AerodromeMissing(entry.aerodromeId))
            continue
        }
        if (ad.runways.isEmpty()) add(FixtureViolation.NoRunways(entry.aerodromeId))
        for ((role, expectedFreq) in entry.frequencyByRole) {
            val published = ad.roles[role]
            if (published == null) {
                add(FixtureViolation.RoleNotPublished(role, entry.aerodromeId))
                continue
            }
            if (published.frequency != expectedFreq) {
                add(FixtureViolation.FrequencyMismatch(
                    aerodrome = entry.aerodromeId,
                    role = role,
                    delta = FrequencyDelta(expected = expectedFreq, published = published.frequency),
                ))
            }
        }
    }
}
