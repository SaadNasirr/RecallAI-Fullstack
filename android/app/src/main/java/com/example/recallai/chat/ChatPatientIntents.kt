package com.example.recallai.chat

import java.util.Locale

enum class ChatPatientNavigation {
    WhereAmI,
    Recall,
    ObjectLocator
}

enum class ScreenOpenConsent {
    Yes,
    No,
    Unclear
}

sealed class PatientIntentHandled {
    data class Local(
        val botReply: String,
        /** Navigate immediately (after user said yes). */
        val navigation: ChatPatientNavigation? = null,
        /** Waiting for yes/no before navigating. */
        val screenOffer: ChatPatientNavigation? = null,
        val keepPendingOffer: Boolean = false
    ) : PatientIntentHandled()

    data object ContinueToApi : PatientIntentHandled()
}

/**
 * On-device intent routing for patient therapist chat (location, identity, memory/recall).
 */
object ChatPatientIntents {

    private val personalItemWords = listOf(
        "key", "keys", "wallet", "glasses", "phone", "pill", "pills",
        "medicine", "medication", "remote", "pen", "watch", "bag", "purse",
        "umbrella", "charger", "ring", "card", "cards"
    )

    fun handle(userText: String, patientName: String?): PatientIntentHandled {
        val t = userText.trim().lowercase(Locale.US)
        if (t.isBlank()) return PatientIntentHandled.ContinueToApi

        if (matchesIdentity(t)) {
            return PatientIntentHandled.Local(botReply = identityReply(patientName))
        }

        if (matchesObjectLocator(t)) {
            return PatientIntentHandled.Local(
                botReply = offerOpenReply(ChatPatientNavigation.ObjectLocator),
                screenOffer = ChatPatientNavigation.ObjectLocator
            )
        }

        if (matchesRecallMemory(t)) {
            return PatientIntentHandled.Local(
                botReply = offerOpenReply(ChatPatientNavigation.Recall),
                screenOffer = ChatPatientNavigation.Recall
            )
        }

        if (matchesLocation(t)) {
            return PatientIntentHandled.Local(
                botReply = offerOpenReply(ChatPatientNavigation.WhereAmI),
                screenOffer = ChatPatientNavigation.WhereAmI
            )
        }

        return PatientIntentHandled.ContinueToApi
    }

    fun resolveConsent(userText: String): ScreenOpenConsent? {
        val t = userText.trim().lowercase(Locale.US)
        if (t.isBlank()) return null
        if (matchesNo(t)) return ScreenOpenConsent.No
        if (matchesYes(t)) return ScreenOpenConsent.Yes
        return ScreenOpenConsent.Unclear
    }

    fun offerOpenReply(screen: ChatPatientNavigation): String {
        val name = screenDisplayName(screen)
        return "I can open $name for you. Would you like me to open it now? " +
            "Just say yes or no — we can stay here in chat if you prefer."
    }

    fun confirmOpenReply(screen: ChatPatientNavigation): String {
        val name = screenDisplayName(screen)
        return "Of course — opening $name for you now."
    }

    fun declineOpenReply(screen: ChatPatientNavigation): String {
        val name = screenDisplayName(screen)
        return "No problem — we won't open $name. I'm right here in chat if you need anything else."
    }

    fun askAgainReply(screen: ChatPatientNavigation): String {
        val name = screenDisplayName(screen)
        return "I didn't catch that — would you like me to open $name? Please say yes or no."
    }

    private fun screenDisplayName(screen: ChatPatientNavigation): String = when (screen) {
        ChatPatientNavigation.WhereAmI -> "Where am I?"
        ChatPatientNavigation.Recall -> "Recall"
        ChatPatientNavigation.ObjectLocator -> "Object Intelligence"
    }

    private fun matchesYes(t: String): Boolean {
        val exact = setOf(
            "y", "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "okey",
            "please", "go ahead", "do it", "open", "open it", "show me",
            "take me there", "let's go", "lets go", "alright", "fine", "why not"
        )
        if (t in exact) return true
        val prefixes = listOf(
            "yes ", "yeah ", "sure ", "ok ", "okay ", "please ", "open "
        )
        if (prefixes.any { t.startsWith(it) }) return true
        return listOf(
            "yes please",
            "yes open",
            "open please",
            "that would help",
            "i would like that",
            "sounds good"
        ).any { t.contains(it) }
    }

    private fun matchesNo(t: String): Boolean {
        val exact = setOf(
            "n", "no", "nope", "nah", "not now", "no thanks", "no thank you",
            "cancel", "stop", "don't", "dont", "never mind", "nevermind",
            "not yet", "maybe later", "stay here", "remain here"
        )
        if (t in exact) return true
        val prefixes = listOf("no ", "don't ", "dont ", "not now", "cancel ")
        if (prefixes.any { t.startsWith(it) }) return true
        return listOf(
            "don't open",
            "dont open",
            "do not open",
            "not right now",
            "i don't want",
            "i dont want"
        ).any { t.contains(it) }
    }

    private fun matchesIdentity(t: String): Boolean {
        val patterns = listOf(
            "who am i",
            "who i am",
            "what's my name",
            "whats my name",
            "what is my name",
            "my name is what",
            "don't know who i am",
            "dont know who i am",
            "don't remember who i am",
            "dont remember who i am",
            "forgot who i am",
            "forgotten who i am",
            "do you know me",
            "do you know my name",
            "what am i called",
            "can't remember my name",
            "cant remember my name",
            "forgot my name",
            "don't remember my name",
            "dont remember my name",
            "what was my name"
        )
        return patterns.any { t.contains(it) }
    }

    private fun matchesObjectLocator(t: String): Boolean {
        val patterns = listOf(
            "object detection",
            "object intelligence",
            "identify this object",
            "identify the object",
            "what is this object",
            "what's this object",
            "whats this object",
            "what object is this",
            "recognize this object",
            "recognise this object",
            "scan this object",
            "use the camera to identify",
            "help me identify",
            "what am i looking at",
            "what am i holding"
        )
        return patterns.any { t.contains(it) }
    }

    private fun matchesRecallMemory(t: String): Boolean {
        if (matchesObjectLocator(t)) return false

        if (personalItemWords.any { word -> containsWord(t, word) }) {
            val recallPersonal = listOf(
                "where are my",
                "where is my",
                "where's my",
                "wheres my",
                "lost my",
                "can't find my",
                "cant find my",
                "cannot find my",
                "forgot my",
                "don't remember my",
                "dont remember my",
                "help me find my"
            )
            if (recallPersonal.any { t.contains(it) }) return true
        }

        val objectPatterns = listOf(
            "don't remember what",
            "dont remember what",
            "forgot what",
            "can't remember what",
            "cant remember what",
            "don't remember this",
            "dont remember this",
            "forgot this",
            "what is this thing",
            "what's this thing",
            "whats this thing",
            "what was this thing",
            "don't remember the name",
            "dont remember the name",
            "forgot the name",
            "can't remember the name",
            "cant remember the name",
            "help me remember",
            "help me recall",
            "don't remember it",
            "dont remember it",
            "forgot it",
            "what is this called",
            "what's this called"
        )
        return objectPatterns.any { t.contains(it) }
    }

    private fun matchesLocation(t: String): Boolean {
        if (matchesRecallMemory(t) || matchesObjectLocator(t)) return false

        val patterns = listOf(
            "where am i",
            "where are we",
            "where am i now",
            "where i am",
            "i'm lost",
            "im lost",
            "i am lost",
            "feel lost",
            "getting lost",
            "got lost",
            "which place",
            "what place am i",
            "what is this place",
            "where is this place",
            "current location",
            "my location",
            "show me where i am",
            "tell me where i am",
            "do you know where i am",
            "where do i live",
            "how do i get home",
            "find my way",
            "safe zone",
            "geofence"
        )
        return patterns.any { t.contains(it) }
    }

    private fun containsWord(text: String, word: String): Boolean {
        val regex = Regex("""\b${Regex.escape(word)}s?\b""")
        return regex.containsMatchIn(text)
    }

    private fun identityReply(patientName: String?): String {
        val name = patientName?.trim()?.takeIf { it.isNotBlank() }
        return if (name != null) {
            "You are $name. You're safe, and I'm here with you. " +
                "It's okay to feel confused sometimes — take a slow breath with me."
        } else {
            "I don't have your name saved in the app yet. " +
                "You can add it in your profile, and I'll always remind you who you are."
        }
    }
}
