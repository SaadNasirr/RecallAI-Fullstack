package com.example.recallai.face

object FaceDescriptor {

    const val SCHEMA_VERSION = MatcherThresholds.DESCRIPTOR_SCHEMA_VERSION

    fun toIdentityFloatArray(vector: List<Float>): FloatArray {
        if (vector.size < FaceNetEmbedder.MIN_EMBEDDING_DIM) return FloatArray(0)
        return FaceVectorUtils.l2Normalize(vector).toFloatArray()
    }

    private fun List<Float>.toFloatArray(): FloatArray {
        val out = FloatArray(size)
        for (i in indices) out[i] = this[i]
        return out
    }
}
