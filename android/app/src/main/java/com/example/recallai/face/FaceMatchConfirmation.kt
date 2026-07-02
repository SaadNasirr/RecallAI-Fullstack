package com.example.recallai.face

/**
 * Requires several consecutive identical strict matches before showing a confirmed name.
 */
class FaceMatchConfirmation(
    private val requiredFrames: Int = MatcherThresholds.CONFIRM_FRAMES
) {
    private var candidate: String? = null
    private var streak: Int = 0

    val confirmStreak: Int get() = streak
    val confirmRequired: Int get() = requiredFrames

    fun reset() {
        candidate = null
        streak = 0
    }

    /**
     * @return Confirmed name when streak is satisfied, else null (keep showing "Checking…").
     */
    fun observe(result: IdentityResult): String? {
        when (result) {
            IdentityResult.Unknown,
            is IdentityResult.PossibleMatch -> {
                reset()
                return null
            }
            is IdentityResult.Identified -> {
                if (candidate == result.name) {
                    streak++
                } else {
                    candidate = result.name
                    streak = 1
                }
                return if (streak >= requiredFrames) result.name else null
            }
        }
    }
}
