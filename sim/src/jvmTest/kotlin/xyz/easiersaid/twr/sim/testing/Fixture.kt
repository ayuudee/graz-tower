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
)

/**
 * Result of loading a [Fixture]. Immutable; callers consume and discard.
 */
data class LoadedFixture(
    val world: AviationWorld,
    val worldIndex: WorldIndex,
    /** Keyed by role for clean lookup; `Map` over `List` per FP-review S4. */
    val controllers: Map<RoleName, ControllerSpec>,
    /**
     * Pass 11 (D-AUDIT.6): events the test driver should enqueue at
     * sim-start. One [SimEvent.FlightPlanFiled] per aircraft in
     * [Fixture.flightPlans]. Sorted by `AircraftId.value` ascending so
     * the seq-assignment in `step()` is deterministic across runs.
     */
    val initialEvents: List<SimEvent> = emptyList(),
)

/** Sealed error hierarchy for fixture loading. */
sealed interface LoadError {
    data class FileMissing(val path: Path) : LoadError
    data class MalformedJson(val path: Path, val reason: String) : LoadError
    data class ValidationFailed(val violations: List<FixtureViolation>) : LoadError
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
    val controllers = controllerRoles.associateWith { role ->
        ControllerSpec(
            id = ControllerId("${aerodromeId.value}_${role.name}"),
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
    val initialEvents: List<SimEvent> = flightPlans.entries
        .sortedBy { it.key.value }
        .flatMap { (aircraftId, plan) ->
            val recipients = xyz.easiersaid.twr.sim.AftnRouting
                .routeFiledPlan(plan) { aerodromeId ->
                    world.aerodromes[aerodromeId]?.roles?.keys.orEmpty()
                }
                .fold(
                    ifLeft = { failure ->
                        error(
                            "Fixture.load: routeFiledPlan failed for ${aircraftId.value} — " +
                                "$failure. The fixture's filed plan cannot be routed to any " +
                                "controller bay; check the world-candidate's published roles.",
                        )
                    },
                    ifRight = { it.toList() },
                )
            recipients.map { recipient ->
                SimEvent.FlightPlanFiled(
                    time = xyz.easiersaid.twr.protocol.SimTime.ZERO,
                    aircraft = aircraftId,
                    plan = plan,
                    recipient = recipient,
                )
            }
        }

    val loaded = LoadedFixture(world, worldIndex, controllers, initialEvents)
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
