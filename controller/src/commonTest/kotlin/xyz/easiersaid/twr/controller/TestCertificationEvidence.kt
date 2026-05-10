package xyz.easiersaid.twr.controller

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.controller.certify.CertificationEvidence

internal fun testCertificationEvidence(): NonEmptyList<CertificationEvidence> =
    NonEmptyList(
        CertificationEvidence.RuntimeChecked(
            checkId = "test-coordination-evidence",
            summary = "Test coordination fixture evidence",
        ),
        emptyList(),
    )
