package com.example.recallai.face

/**
 * Cosine similarity on L2-normalized MobileFaceNet embeddings (128–192-D).
 */
object MatcherThresholds {
    /** Bump when embedding pipeline changes — triggers one-time profile wipe. */
    const val DESCRIPTOR_SCHEMA_VERSION = 4

    /** Strong match — show name after confirmation streak. */
    const val COSINE_RECOGNIZED = 0.65f

    /** Weak match — show as "Possible: …" without full confirmation. */
    const val COSINE_POSSIBLE_MIN = 0.50f

    /** Minimum gap between best and second-best when multiple profiles exist. */
    const val COSINE_MARGIN = 0.05f

    /** Block enrolling a second profile that is too close to an existing one. */
    const val DUPLICATE_ENROLL_MIN_COSINE = 0.72f

    /** Consecutive strict matches required before showing a confirmed name. */
    const val CONFIRM_FRAMES = 4
}
