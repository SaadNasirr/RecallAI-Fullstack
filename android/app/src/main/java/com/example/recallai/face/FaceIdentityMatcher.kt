package com.example.recallai.face

import android.util.Log
import com.example.recallai.BuildConfig
import com.example.recallai.data.FaceProfileItem

private const val TAG = "FaceMatcher"

sealed class IdentityResult {
    data object Unknown : IdentityResult()
    data class Identified(
        val name: String,
        val confidence: String,
        val cosineSimilarity: Float
    ) : IdentityResult()
    data class PossibleMatch(
        val name: String,
        val cosineSimilarity: Float
    ) : IdentityResult()
}

object FaceIdentityMatcher {

    fun identifyFace(incomingEmbedding: FloatArray, savedFaces: List<FaceProfileItem>): IdentityResult {
        if (savedFaces.isEmpty() || incomingEmbedding.isEmpty()) return IdentityResult.Unknown

        val query = FaceDescriptor.toIdentityFloatArray(incomingEmbedding.toList())
        if (query.isEmpty()) return IdentityResult.Unknown

        val ranked = savedFaces
            .mapNotNull { face ->
                val stored = FaceDescriptor.toIdentityFloatArray(face.vector)
                if (stored.isEmpty() || stored.size != query.size) return@mapNotNull null
                val cos = FaceVectorUtils.cosineSimilarity(query.toList(), stored.toList())
                if (cos.isNaN() || cos.isInfinite()) return@mapNotNull null
                Pair(face, cos)
            }
            .sortedByDescending { it.second }

        if (ranked.isEmpty()) return IdentityResult.Unknown

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "profiles=${savedFaces.size}")
            ranked.forEach { (profile, cos) ->
                Log.d(TAG, "vs [${profile.name}] cos=${"%.4f".format(cos)}")
            }
        }

        val (bestFace, bestCos) = ranked.first()

        if (ranked.size > 1) {
            val secondCos = ranked[1].second
            if (bestCos - secondCos < MatcherThresholds.COSINE_MARGIN) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "reject: margin ${"%.4f".format(bestCos - secondCos)} < ${MatcherThresholds.COSINE_MARGIN}"
                    )
                }
                return IdentityResult.Unknown
            }
        }

        return when {
            bestCos >= MatcherThresholds.COSINE_RECOGNIZED -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "MATCH → ${bestFace.name}")
                IdentityResult.Identified(
                    name = bestFace.name,
                    confidence = "Recognized",
                    cosineSimilarity = bestCos
                )
            }
            bestCos >= MatcherThresholds.COSINE_POSSIBLE_MIN -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "POSSIBLE → ${bestFace.name}")
                IdentityResult.PossibleMatch(name = bestFace.name, cosineSimilarity = bestCos)
            }
            else -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "reject: cos ${"%.4f".format(bestCos)} < ${MatcherThresholds.COSINE_POSSIBLE_MIN}")
                }
                IdentityResult.Unknown
            }
        }
    }

    fun cosineBetween(a: FloatArray, b: FloatArray): Float {
        val na = FaceDescriptor.toIdentityFloatArray(a.toList())
        val nb = FaceDescriptor.toIdentityFloatArray(b.toList())
        if (na.isEmpty() || nb.isEmpty() || na.size != nb.size) return 0f
        return FaceVectorUtils.cosineSimilarity(na.toList(), nb.toList())
    }

    private fun FloatArray.toList(): List<Float> = List(size) { this[it] }
}
