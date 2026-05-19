package xyz.easiersaid.twr.sim

import kotlin.random.Random
import xyz.easiersaid.twr.protocol.Heading

data class EvidenceGeneratedSampleMetadata(
    val domainName: String,
    val seed: Long,
    val count: Int,
    val sampleIndex: Int,
    val displayValue: String,
    val partition: String,
) {
    init {
        require(domainName.isNotBlank()) { "generated domain name must not be blank" }
        require(count > 0) { "generated sample count must be positive" }
        require(sampleIndex in 0 until count) { "generated sample index must be inside count" }
        require(displayValue.isNotBlank()) { "generated sample value must not be blank" }
        require(partition.isNotBlank()) { "generated partition must not be blank" }
    }
}

data class EvidenceGeneratedSample<T : Any>(
    val value: T,
    val metadata: EvidenceGeneratedSampleMetadata,
)

data class EvidenceGeneratedDomain<T : Any>(
    val name: String,
    val seed: Long,
    val count: Int,
    val samples: List<EvidenceGeneratedSample<T>>,
) {
    init {
        require(name.isNotBlank()) { "generated domain name must not be blank" }
        require(count > 0) { "generated domain count must be positive" }
        require(samples.size == count) { "generated domain sample count must match metadata count" }
    }
}

object EvidenceDomains {
    fun headings(seed: Long, count: Int): EvidenceGeneratedDomain<Heading> {
        require(count > 0) { "heading domain count must be positive" }
        val random = Random(seed)
        val samples = (0 until count).map { index ->
            val degrees = 1 + random.nextInt(360)
            val heading = Heading.unsafe(degrees)
            EvidenceGeneratedSample(
                value = heading,
                metadata = EvidenceGeneratedSampleMetadata(
                    domainName = "heading",
                    seed = seed,
                    count = count,
                    sampleIndex = index,
                    displayValue = degrees.toString(),
                    partition = headingPartition(degrees),
                ),
            )
        }
        return EvidenceGeneratedDomain(name = "heading", seed = seed, count = count, samples = samples)
    }

    private fun headingPartition(degrees: Int): String =
        when (degrees) {
            in 1..89 -> "north-east"
            in 90..179 -> "south-east"
            in 180..269 -> "south-west"
            in 270..360 -> "north-west"
            else -> "invalid-heading"
        }
}
