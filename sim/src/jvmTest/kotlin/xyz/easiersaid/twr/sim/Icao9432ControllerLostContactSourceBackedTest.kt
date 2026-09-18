package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.EmergencyType

private const val LOST_CONTACT_MESSAGE = "OE-ABC maintain VMC and acknowledge when able"

class Icao9432ControllerLostContactSourceBackedTest {
    @Test
    fun `controller may ask route aircraft to call or relay after failed calls on believed listening frequencies`() {
        sourceUnitSpec("icao9432-controller-route-aircraft-relay-request") {
            title("Station unable to contact aircraft may ask route aircraft to call or relay")
            sourceUnit(
                chunk08Ref("icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2"),
            )
            domain("contact-state", setOf("believed-listening-frequencies-failed", "generic-direct-failed"))
            domain("relay-actor", setOf("route-aircraft", "other-station"))
            domain("relay-action", setOf("call", "relay"))

            witness("route aircraft relay request follows failed calls on believed-listening frequencies") {
                val requested = ControllerLostContactProjection
                    .from(ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed)
                    .acceptedState()
                    .requestRelay(RelayHelper.RouteAircraft, RelayAction.CallOrRelay)
                    .acceptedState()

                check(requested.requestedRelays == setOf(RelayRequest(RelayHelper.RouteAircraft, RelayAction.CallOrRelay)))
                check(requested.policyMarker == PolicyMarker.RouteAircraftRelayRequest)
                hit("route-aircraft-call-or-relay-requested")
                requireHits("route-aircraft-call-or-relay-requested")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `controller may ask other stations to call or relay after failed calls on believed listening frequencies`() {
        sourceUnitSpec("icao9432-controller-other-station-relay-request") {
            title("Station unable to contact aircraft may ask other stations to call or relay")
            sourceUnit(
                chunk08Ref("icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e"),
            )
            domain("contact-state", setOf("believed-listening-frequencies-failed", "generic-direct-failed"))
            domain("relay-actor", setOf("route-aircraft", "other-station"))
            domain("relay-action", setOf("call", "relay"))

            witness("other-station relay request follows failed calls on believed-listening frequencies") {
                val requested = ControllerLostContactProjection
                    .from(ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed)
                    .acceptedState()
                    .requestRelay(RelayHelper.OtherStation, RelayAction.CallOrRelay)
                    .acceptedState()

                check(requested.requestedRelays == setOf(RelayRequest(RelayHelper.OtherStation, RelayAction.CallOrRelay)))
                check(requested.policyMarker == PolicyMarker.OtherStationRelayRequest)
                hit("other-station-call-or-relay-requested")
                requireHits("other-station-call-or-relay-requested")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `atc may blind transmit non clearance messages after relay attempts fail`() {
        sourceUnitSpec("icao9432-atc-non-clearance-blind-transmission") {
            title("ATC may blind-transmit non-clearance messages after failed station attempts")
            sourceUnit(
                chunk08Ref("icao9432-extracted::communications_failure_9_5_en::75055714e70d4560"),
            )
            domain("relay-attempts", setOf("route-and-station-failed", "missing"))
            domain("believed-listening", setOf("true", "false"))
            domain("message-kind", setOf("non-clearance", "clearance"))

            witness("non-clearance blind transmission requires failed relays and believed listening") {
                val blind = ControllerLostContactProjection
                    .from(ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed)
                    .acceptedState()
                    .requestRelay(RelayHelper.RouteAircraft, RelayAction.CallOrRelay)
                    .acceptedState()
                    .recordRelayResult(RelayHelper.RouteAircraft, RelayResult.Failed)
                    .acceptedState()
                    .requestRelay(RelayHelper.OtherStation, RelayAction.CallOrRelay)
                    .acceptedState()
                    .recordRelayResult(RelayHelper.OtherStation, RelayResult.Failed)
                    .acceptedState()
                    .markAircraftBelievedListening()
                    .acceptedState()
                    .blindTransmit(BlindMessageKind.NonClearance, LOST_CONTACT_MESSAGE)
                    .acceptedState()

                check(blind.blindTransmission == BlindTransmission(
                    kind = BlindMessageKind.NonClearance,
                    message = LOST_CONTACT_MESSAGE,
                ))
                check(blind.policyMarker == PolicyMarker.NonClearanceBlindTransmission)
                hit("atc-non-clearance-blind-transmission-after-failed-relays")
                requireHits("atc-non-clearance-blind-transmission-after-failed-relays")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `recovery clears all controller lost contact derived state`() {
        val active = ControllerLostContactProjection
            .from(ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed)
            .acceptedState()
            .requestRelay(RelayHelper.RouteAircraft, RelayAction.CallOrRelay)
            .acceptedState()
            .recordRelayResult(RelayHelper.RouteAircraft, RelayResult.Failed)
            .acceptedState()
            .requestRelay(RelayHelper.OtherStation, RelayAction.CallOrRelay)
            .acceptedState()
            .recordRelayResult(RelayHelper.OtherStation, RelayResult.Failed)
            .acceptedState()
            .markAircraftBelievedListening()
            .acceptedState()
            .blindTransmit(BlindMessageKind.NonClearance, LOST_CONTACT_MESSAGE)
            .acceptedState()

        val recovered = active.resolve(LostContactResolution.ContactRestored).acceptedState()

        check(recovered.state == LostContactState.Normal)
        check(!recovered.callsOnBelievedListeningFrequenciesFailed)
        check(recovered.requestedRelays.isEmpty())
        check(recovered.relayResults.isEmpty())
        check(!recovered.aircraftBelievedListening)
        check(recovered.blindTransmission == null)
        check(recovered.policyMarker == null)
    }

    @Test
    fun `negative guards prevent false controller lost contact coverage`() {
        check(ControllerLostContactProjection.from(ControllerLostContactEvent.RoutineTraffic).isRejected)
        check(ControllerLostContactProjection.from(ControllerLostContactEvent.GenericDirectStationContactFailed).isRejected)
        check(ControllerLostContactProjection.from(ControllerLostContactEvent.PilotOriginatedCommunicationsFailure).isRejected)
        check(ControllerLostContactProjection.from(ControllerLostContactEvent.PhraseologyOnlyBlindTransmission).isRejected)
        check(
            ControllerLostContactProjection
                .from(ControllerLostContactEvent.GenericEmergencyLabel(EmergencyType.MAYDAY))
                .isRejected,
        )

        val normal = ControllerLostContactProjection.normal()
        check(normal.requestRelay(RelayHelper.RouteAircraft, RelayAction.CallOrRelay).isRejected)
        check(normal.markAircraftBelievedListening().isRejected)
        check(normal.resolve(LostContactResolution.ContactRestored).isRejected)

        val failedDirect = ControllerLostContactProjection
            .from(ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed)
            .acceptedState()
        check(failedDirect.blindTransmit(BlindMessageKind.NonClearance, LOST_CONTACT_MESSAGE).isRejected)
        check(failedDirect.blindTransmit(BlindMessageKind.Clearance, LOST_CONTACT_MESSAGE).isRejected)

        val relayRequested = failedDirect
            .requestRelay(RelayHelper.RouteAircraft, RelayAction.CallOrRelay)
            .acceptedState()
        check(relayRequested.markAircraftBelievedListening().isRejected)
        check(relayRequested.recordRelayResult(RelayHelper.OtherStation, RelayResult.Failed).isRejected)

        val failedRelays = relayRequested
            .recordRelayResult(RelayHelper.RouteAircraft, RelayResult.Failed)
            .acceptedState()
            .requestRelay(RelayHelper.OtherStation, RelayAction.CallOrRelay)
            .acceptedState()
            .recordRelayResult(RelayHelper.OtherStation, RelayResult.Failed)
            .acceptedState()
        check(failedRelays.blindTransmit(BlindMessageKind.NonClearance, LOST_CONTACT_MESSAGE).isRejected)
        check(
            failedRelays
                .markAircraftBelievedListening()
                .acceptedState()
                .blindTransmit(BlindMessageKind.Clearance, LOST_CONTACT_MESSAGE)
                .isRejected,
        )
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private data class ControllerLostContactProjection(
        val state: LostContactState,
        val callsOnBelievedListeningFrequenciesFailed: Boolean,
        val requestedRelays: Set<RelayRequest>,
        val relayResults: Map<RelayHelper, RelayResult>,
        val aircraftBelievedListening: Boolean,
        val blindTransmission: BlindTransmission?,
        val policyMarker: PolicyMarker?,
    ) {
        fun requestRelay(
            helper: RelayHelper,
            action: RelayAction,
        ): StateTransition<ControllerLostContactProjection> =
            when {
                state != LostContactState.Active ->
                    StateTransition.Rejected("Relay request requires active lost-contact workflow")
                !callsOnBelievedListeningFrequenciesFailed ->
                    StateTransition.Rejected(
                        "Relay request requires failed calls on frequencies the aircraft is believed listening on",
                    )
                action == RelayAction.Absent ->
                    StateTransition.Rejected("Relay action is required")
                else ->
                    StateTransition.Accepted(
                        copy(
                            requestedRelays = requestedRelays + RelayRequest(helper, action),
                            policyMarker = when (helper) {
                                RelayHelper.RouteAircraft -> PolicyMarker.RouteAircraftRelayRequest
                                RelayHelper.OtherStation -> PolicyMarker.OtherStationRelayRequest
                            },
                        ),
                    )
            }

        fun recordRelayResult(
            helper: RelayHelper,
            result: RelayResult,
        ): StateTransition<ControllerLostContactProjection> =
            when {
                state != LostContactState.Active ->
                    StateTransition.Rejected("Relay result requires active lost-contact workflow")
                RelayRequest(helper, RelayAction.CallOrRelay) !in requestedRelays ->
                    StateTransition.Rejected("Relay result requires matching relay request")
                else ->
                    StateTransition.Accepted(copy(relayResults = relayResults + (helper to result)))
            }

        fun markAircraftBelievedListening(): StateTransition<ControllerLostContactProjection> =
            when {
                state != LostContactState.Active ->
                    StateTransition.Rejected("Believed-listening marker requires active lost-contact workflow")
                relayResults[RelayHelper.RouteAircraft] != RelayResult.Failed ->
                    StateTransition.Rejected("Believed-listening marker requires failed route-aircraft relay")
                relayResults[RelayHelper.OtherStation] != RelayResult.Failed ->
                    StateTransition.Rejected("Believed-listening marker requires failed other-station relay")
                else ->
                    StateTransition.Accepted(copy(aircraftBelievedListening = true))
            }

        fun blindTransmit(
            kind: BlindMessageKind,
            message: String,
        ): StateTransition<ControllerLostContactProjection> {
            val preconditionFailure = when {
                state != LostContactState.Active ->
                    StateTransition.Rejected("Blind transmission requires active lost-contact workflow")
                !aircraftBelievedListening ->
                    StateTransition.Rejected("Blind transmission requires aircraft believed listening")
                message.isBlank() ->
                    StateTransition.Rejected("Blind message must not be blank")
                else -> null
            }
            return preconditionFailure ?: when (kind) {
                BlindMessageKind.Clearance ->
                    StateTransition.Rejected("Blind clearances remain prohibited in lost-contact workflow")
                BlindMessageKind.NonClearance ->
                    StateTransition.Accepted(
                        copy(
                            blindTransmission = BlindTransmission(kind, message),
                            policyMarker = PolicyMarker.NonClearanceBlindTransmission,
                        ),
                    )
            }
        }

        fun resolve(event: LostContactResolution): StateTransition<ControllerLostContactProjection> =
            when (event) {
                LostContactResolution.ContactRestored ->
                    when (state) {
                        LostContactState.Active -> StateTransition.Accepted(normal())
                        LostContactState.Normal -> StateTransition.Rejected(
                            "Contact restoration requires active lost-contact workflow",
                        )
                    }

                LostContactResolution.RoutineCallAnswered -> StateTransition.Rejected(
                    "Routine call answer does not resolve controller lost-contact workflow",
                )
            }

        companion object {
            fun from(event: ControllerLostContactEvent): StateTransition<ControllerLostContactProjection> =
                when (event) {
                    ControllerLostContactEvent.CallsOnBelievedListeningFrequenciesFailed -> StateTransition.Accepted(
                        normal().copy(
                            state = LostContactState.Active,
                            callsOnBelievedListeningFrequenciesFailed = true,
                        ),
                    )

                    ControllerLostContactEvent.GenericDirectStationContactFailed,
                    ControllerLostContactEvent.RoutineTraffic,
                    ControllerLostContactEvent.PilotOriginatedCommunicationsFailure,
                    ControllerLostContactEvent.PhraseologyOnlyBlindTransmission,
                    is ControllerLostContactEvent.GenericEmergencyLabel,
                    -> StateTransition.Rejected("Not controller lost-contact workflow evidence")
                }

            fun normal(): ControllerLostContactProjection =
                ControllerLostContactProjection(
                    state = LostContactState.Normal,
                    callsOnBelievedListeningFrequenciesFailed = false,
                    requestedRelays = emptySet(),
                    relayResults = emptyMap(),
                    aircraftBelievedListening = false,
                    blindTransmission = null,
                    policyMarker = null,
                )
        }
    }

    private sealed interface ControllerLostContactEvent {
        data object CallsOnBelievedListeningFrequenciesFailed : ControllerLostContactEvent
        data object GenericDirectStationContactFailed : ControllerLostContactEvent
        data object RoutineTraffic : ControllerLostContactEvent
        data object PilotOriginatedCommunicationsFailure : ControllerLostContactEvent
        data object PhraseologyOnlyBlindTransmission : ControllerLostContactEvent
        data class GenericEmergencyLabel(val emergencyType: EmergencyType) : ControllerLostContactEvent
    }

    private enum class LostContactState {
        Normal,
        Active,
    }

    private enum class RelayHelper {
        RouteAircraft,
        OtherStation,
    }

    private enum class RelayAction {
        CallOrRelay,
        Absent,
    }

    private data class RelayRequest(
        val helper: RelayHelper,
        val action: RelayAction,
    )

    private enum class RelayResult {
        Failed,
        Succeeded,
    }

    private enum class BlindMessageKind {
        NonClearance,
        Clearance,
    }

    private data class BlindTransmission(
        val kind: BlindMessageKind,
        val message: String,
    )

    private enum class PolicyMarker {
        RouteAircraftRelayRequest,
        OtherStationRelayRequest,
        NonClearanceBlindTransmission,
    }

    private enum class LostContactResolution {
        ContactRestored,
        RoutineCallAnswered,
    }

    private sealed interface StateTransition<out T> {
        val isRejected: Boolean

        data class Accepted<T>(val state: T) : StateTransition<T> {
            override val isRejected: Boolean = false
        }

        data class Rejected(val reason: String) : StateTransition<Nothing> {
            override val isRejected: Boolean = true
        }
    }

    private fun StateTransition<ControllerLostContactProjection>.acceptedState(): ControllerLostContactProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }
}
