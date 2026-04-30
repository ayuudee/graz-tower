package xyz.easiersaid.twr.sim.testing

import arrow.core.Either
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.AuthorityGrant
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.core.world.buildWorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.sim.ControllerSpec

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
    /** Aircraft ids assigned to GROUND on init; other roles start empty. Defaults to one stub. */
    val groundResponsibilities: Set<AircraftId> = emptySet(),
)

/**
 * Result of loading a [Fixture]. Immutable; callers consume and discard.
 */
data class LoadedFixture(
    val world: AviationWorld,
    val worldIndex: WorldIndex,
    /** Keyed by role for clean lookup; `Map` over `List` per FP-review S4. */
    val controllers: Map<RoleName, ControllerSpec>,
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
    data class RoleFrequencyMismatch(
        val role: RoleName,
        val expected: Frequency,
        val got: Frequency?,
    ) : FixtureViolation
}

private val json = Json { ignoreUnknownKeys = true }

private val placeholderAuthorities = setOf(
    AuthorityGrant(
        entityType = AuthorityEntityType.RADIO_ROLE,
        operations = setOf(AuthorityOperation.CONTACT),
    ),
)

/**
 * Load the fixture's world candidate, patch role/authority/weather, and build
 * a [LoadedFixture]. Returns [Either.Left] for missing files, malformed JSON,
 * or validation failures. Total: never throws.
 */
fun Fixture.load(): Either<LoadError, LoadedFixture> {
    val file = candidatePath.toFile()
    if (!file.exists()) return Either.Left(LoadError.FileMissing(candidatePath))

    val rawWorld = try {
        val doc = json.decodeFromString<WorldCandidateDocument>(Files.readString(candidatePath))
        WorldCandidateLoader.toWorld(doc)
    } catch (e: SerializationException) {
        return Either.Left(LoadError.MalformedJson(candidatePath, e.message ?: "unknown"))
    } catch (e: IllegalArgumentException) {
        return Either.Left(LoadError.MalformedJson(candidatePath, e.message ?: "unknown"))
    }

    // Patch role/authority/frequency: the world-candidate doesn't carry roles
    // yet (Pass 10's WorldCandidateLoader rewrite will read them from the
    // manifest). Until then, every Fixture provides the role list and we
    // inject placeholder authorities + the fixture's frequency.
    val patchedRoles = controllerRoles.associateWith { role ->
        AerodromeRole(role, placeholderAuthorities, frequency)
    }
    val world = rawWorld.copy(
        aerodromes = rawWorld.aerodromes.mapValues { (id, ad) ->
            if (id == aerodromeId) ad.copy(roles = patchedRoles) else ad
        },
    )

    val worldIndex = world.buildWorldIndex()

    val controllers = controllerRoles.associateWith { role ->
        ControllerSpec(
            id = ControllerId("${aerodromeId.value}_${role.name}"),
            role = role,
            aerodromeId = aerodromeId,
            frequency = frequency,
            responsibilities = if (role == RoleName.GROUND) groundResponsibilities else emptySet(),
        )
    }

    val loaded = LoadedFixture(world, worldIndex, controllers)
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
        val got = ad.roles[role]?.frequency
        if (got != fixture.frequency) {
            add(FixtureViolation.RoleFrequencyMismatch(role, fixture.frequency, got))
        }
    }
}
