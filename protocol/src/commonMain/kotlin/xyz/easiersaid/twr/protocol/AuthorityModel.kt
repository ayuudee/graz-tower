package xyz.easiersaid.twr.protocol

enum class AuthorityEntityType {
    RUNWAY,
    TAXIWAY,
    STAND,
    APRON,
    CIRCUIT_PROCEDURE,
    HOLDING_PATTERN,
    INSTRUMENT_APPROACH,
    SID,
    STAR,
    AIRWAY,
    VFR_ROUTE,
    FIX,
    AIRSPACE_VOLUME,
    RADIO_ROLE
}

enum class AuthorityOperation {
    STARTUP,
    PUSHBACK,
    TAXI,
    CROSS,
    BACKTRACK,
    LINE_UP,
    TAKEOFF,
    LAND,
    TOUCH_AND_GO,
    CIRCUIT,
    SEQUENCE,
    HOLD,
    ROUTE_CLEARANCE,
    APPROACH_CLEARANCE,
    ALTITUDE,
    SPEED,
    SQUAWK,
    CONTACT,
    MONITOR,
    AIRSPACE_TRANSIT,
    INFORMATION
}
