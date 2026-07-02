package com.example.recallai.face

/**
 * Collects multiple live frames and averages them into one stable enrollment template.
 */
class EnrollmentBuffer(private val targetSamples: Int = 5) {
    private val samples = mutableListOf<FloatArray>()

    val sampleCount: Int get() = samples.size

    fun addSample(descriptor: FloatArray) {
        if (descriptor.isEmpty()) return
        if (samples.size >= targetSamples) return
        if (samples.isNotEmpty() && samples[0].size != descriptor.size) return
        samples.add(descriptor.copyOf())
    }

    fun isReady(): Boolean = samples.size >= MIN_READY

    fun buildTemplate(): FloatArray {
        require(samples.isNotEmpty()) { "No enrollment samples" }
        val size = samples[0].size
        val averaged = FloatArray(size) { i ->
            samples.sumOf { it[i].toDouble() }.toFloat() / samples.size
        }
        return FaceDescriptor.toIdentityFloatArray(averaged.toList())
    }

    fun clear() {
        samples.clear()
    }

    companion object {
        const val MIN_READY = 3
    }
}
