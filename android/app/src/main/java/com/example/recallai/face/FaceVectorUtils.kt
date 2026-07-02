package com.example.recallai.face

import kotlin.math.sqrt

object FaceVectorUtils {

    fun l2Normalize(v: List<Float>): List<Float> {
        if (v.isEmpty()) return v
        var s = 0f
        for (x in v) s += x * x
        val norm = sqrt(s).coerceAtLeast(1e-6f)
        return v.map { it / norm }
    }

    /** Cosine similarity for equal-length vectors (not necessarily normalized). */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom <= 1e-6f) 0f else (dot / denom).coerceIn(-1f, 1f)
    }
}
