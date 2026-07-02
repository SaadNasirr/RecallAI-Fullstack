package com.example.recallai.flashcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.local.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashCard(
    val id: Long,
    val question: String,
    val answer: String,
    val hint: String,
    val category: String,
    /** Key words from the answer; used for post-flip match hint. */
    val keyTerms: List<String> = emptyList()
)

data class FlashcardUiState(
    val cards: List<FlashCard> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val userAnswer: String = "",
    val showAnswerRequired: Boolean = false,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val sessionComplete: Boolean = false,
    val isLoading: Boolean = true
) {
    val currentCard: FlashCard? get() = cards.getOrNull(currentIndex)
    val totalCards: Int get() = cards.size
    val progress: Float get() = if (totalCards == 0) 0f else (currentIndex + 1).toFloat() / totalCards
    val score: Int get() = if (totalCards == 0) 0 else (correctCount * 100 / totalCards)
    val answeredCount: Int get() = correctCount + incorrectCount

    fun answerMatchesKeyTerms(): Boolean {
        val card = currentCard ?: return false
        if (card.keyTerms.isEmpty() || userAnswer.isBlank()) return false
        val lower = userAnswer.lowercase()
        return card.keyTerms.any { term -> lower.contains(term.lowercase()) }
    }
}

@HiltViewModel
class FlashcardViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlashcardUiState())
    val uiState: StateFlow<FlashcardUiState> = _uiState.asStateFlow()

    init {
        loadCards()
    }

    fun loadCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val memories = memoryRepository.getRecentMemories(limit = 60)
            val cards = memories
                .filter { it.text.isNotBlank() && it.text.length >= 12 }
                .mapNotNull { entity -> buildCard(entity) }
                .distinctBy { it.question }
                .shuffled()
                .take(15)
            _uiState.update {
                it.copy(
                    cards = cards,
                    currentIndex = 0,
                    isFlipped = false,
                    userAnswer = "",
                    showAnswerRequired = false,
                    correctCount = 0,
                    incorrectCount = 0,
                    sessionComplete = false,
                    isLoading = false
                )
            }
        }
    }

    fun onUserAnswerChange(v: String) = _uiState.update {
        it.copy(userAnswer = v, showAnswerRequired = if (v.isNotBlank()) false else it.showAnswerRequired)
    }

    fun flipCard() {
        if (_uiState.value.userAnswer.isBlank()) {
            _uiState.update { it.copy(showAnswerRequired = true) }
            return
        }
        _uiState.update { it.copy(isFlipped = true, showAnswerRequired = false) }
    }

    fun answerCorrect() = recordAnswer(correct = true)
    fun answerIncorrect() = recordAnswer(correct = false)

    private fun recordAnswer(correct: Boolean) {
        val s = _uiState.value
        val nextIndex = s.currentIndex + 1
        val complete = nextIndex >= s.totalCards
        _uiState.update {
            it.copy(
                correctCount = it.correctCount + if (correct) 1 else 0,
                incorrectCount = it.incorrectCount + if (!correct) 1 else 0,
                currentIndex = if (complete) it.currentIndex else nextIndex,
                isFlipped = false,
                userAnswer = "",
                showAnswerRequired = false,
                sessionComplete = complete
            )
        }
    }

    fun restartSession() = loadCards()

    // ── Question builder ──────────────────────────────────────────────────

    private fun buildCard(entity: MemoryEntity): FlashCard? {
        val raw = entity.text.trim()
        val type = (entity.type ?: "TEXT").uppercase()

        return when {
            type == "PEOPLE_MEMORY" || raw.contains("Known person", ignoreCase = true) ->
                buildPeopleCard(entity, raw)

            type.startsWith("MEDICATION") || raw.contains("medication", ignoreCase = true) ->
                buildMedicationCard(entity, raw)

            type == "ROUTINE_LOG" || raw.contains("Routine task", ignoreCase = true) ->
                buildRoutineCard(entity, raw)

            type == "OBJECT_DETECTION" || raw.contains("Object detected", ignoreCase = true) ->
                buildObjectCard(entity, raw)

            type == "FACE_ANALYSIS" || type == "FACE_RECOGNITION" ||
                raw.contains("face recognized", ignoreCase = true) ||
                raw.contains("face analysis", ignoreCase = true) ->
                buildFaceCard(entity, raw)

            type == "MOOD" || raw.contains("mood", ignoreCase = true) ->
                buildMoodCard(entity, raw)

            type == "LOCATION" || type == "GEOFENCE" ->
                buildLocationCard(entity, raw)

            raw.length > 30 -> buildGenericCard(entity, raw)

            else -> null
        }
    }

    // "Known person added: Alice, Sister. Lives nearby."
    private fun buildPeopleCard(entity: MemoryEntity, raw: String): FlashCard? {
        val nameMatch = Regex("""(?:added|updated):\s*([^,\.]+)""", RegexOption.IGNORE_CASE).find(raw)
        val name = nameMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val afterName = raw.substringAfter(name).trimStart(',', ' ')
        val relation = afterName.split('.', '\n').firstOrNull()?.trim()
            ?.takeIf { it.isNotBlank() && it.length < 40 }
        val answer = relation?.ifBlank { null } ?: raw.substringAfter('.').trim().ifBlank { return null }

        return FlashCard(
            id = entity.id,
            question = "What is $name's relation to you?",
            answer = answer,
            hint = "Think about how you know $name",
            category = "People",
            keyTerms = listOfNotNull(relation?.lowercase()?.trim())
        )
    }

    // "Medication panadol marked taken at 12:05 AM."
    // "Medication scheduled: Aspirin at 8:00 AM. Take with food."
    // "Medication Aspirin skipped. Reason: Had breakfast late."
    private fun buildMedicationCard(entity: MemoryEntity, raw: String): FlashCard? {
        val takenMatch = Regex("""Medication\s+(\S+)\s+marked taken at\s+([\d:]+\s*[APap][Mm])""").find(raw)
        if (takenMatch != null) {
            val medName = takenMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            val time = takenMatch.groupValues[2].trim()
            return FlashCard(
                id = entity.id,
                question = "What time did you take $medName?",
                answer = time,
                hint = "You marked this dose as taken",
                category = "Medication",
                keyTerms = listOf(time.lowercase(), time.substringBefore(" "))
            )
        }

        val scheduledMatch = Regex("""Medication scheduled:\s*([^a]+?)\s+at\s+([\d:]+\s*[APap][Mm])""").find(raw)
        if (scheduledMatch != null) {
            val medName = scheduledMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val time = scheduledMatch.groupValues[2].trim()
            val instructions = raw.substringAfter('.').trim().takeIf { it.isNotBlank() }
            return FlashCard(
                id = entity.id,
                question = "Which medication is scheduled at $time?",
                answer = medName + (if (instructions != null) "\n$instructions" else ""),
                hint = "Check your medication schedule",
                category = "Medication",
                keyTerms = listOf(medName.lowercase())
            )
        }

        val skipMatch = Regex("""Medication\s+(\S+)\s+skipped\.\s*Reason:\s*(.+)""", RegexOption.IGNORE_CASE).find(raw)
        if (skipMatch != null) {
            val medName = skipMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            val reason = skipMatch.groupValues[2].trim()
            val keyWords = reason.lowercase().split(Regex("\\s+")).filter { it.length > 3 }
            return FlashCard(
                id = entity.id,
                question = "Why was $medName skipped?",
                answer = reason,
                hint = "A dose was skipped with a reason",
                category = "Medication",
                keyTerms = keyWords
            )
        }

        return null
    }

    // "Routine task 'Morning Walk' marked complete (streak: 3 days)."
    // "Routine task added: Drink Water (Morning at 7:00 AM)."
    private fun buildRoutineCard(entity: MemoryEntity, raw: String): FlashCard? {
        val completeMatch = Regex("""Routine task "([^"]+)" marked complete \(streak: (\d+) day""").find(raw)
        if (completeMatch != null) {
            val task = completeMatch.groupValues[1]
            val streak = completeMatch.groupValues[2]
            val suffix = if (streak == "1") "" else "s"
            return FlashCard(
                id = entity.id,
                question = "How long is your \"$task\" streak?",
                answer = "$streak day$suffix",
                hint = "Consistency builds habits",
                category = "Routine",
                keyTerms = listOf(streak, "$streak day$suffix")
            )
        }

        val addedMatch = Regex("""Routine task added:\s*([^\(]+)""").find(raw)
        if (addedMatch != null) {
            val task = addedMatch.groupValues[1].trim().trimEnd('.')
            val period = Regex("""\((\w+)""").find(raw)?.groupValues?.getOrNull(1)
            val answer = period ?: raw.substringAfter('(').substringBefore(')').trim().ifBlank { "See notes" }
            return FlashCard(
                id = entity.id,
                question = "When is the \"$task\" routine scheduled?",
                answer = answer,
                hint = "Think about your daily schedule",
                category = "Routine",
                keyTerms = listOfNotNull(period?.lowercase())
            )
        }

        return null
    }

    // "Object detected: Keys on kitchen counter"
    private fun buildObjectCard(entity: MemoryEntity, raw: String): FlashCard? {
        val match = Regex("""Object detected:\s*(.+)""", RegexOption.IGNORE_CASE).find(raw)
        val objectDesc = match?.groupValues?.getOrNull(1)?.trim() ?: return null
        val parts = objectDesc.split(Regex("\\s+on\\s+|\\s+at\\s+|\\s+near\\s+"), limit = 2)
        return if (parts.size == 2) {
            val location = parts[1].trim()
            FlashCard(
                id = entity.id,
                question = "Where are your ${parts[0].trim()}?",
                answer = location.replaceFirstChar { it.uppercase() },
                hint = "An object was captured by the camera",
                category = "Objects",
                keyTerms = location.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
            )
        } else {
            FlashCard(
                id = entity.id,
                question = "What did the camera detect?",
                answer = objectDesc.replaceFirstChar { it.uppercase() },
                hint = "Object Intelligence captured this",
                category = "Objects",
                keyTerms = objectDesc.lowercase().split(Regex("\\s+")).filter { it.length > 3 }
            )
        }
    }

    // "Face analysis: John Smith recognized. Software Engineer at ..."
    private fun buildFaceCard(entity: MemoryEntity, raw: String): FlashCard? {
        val nameMatch = Regex("""([A-Z][a-z]+ [A-Z][a-z]+)""").find(raw)
        val name = nameMatch?.groupValues?.getOrNull(1) ?: return null
        val detail = raw.substringAfter(name).trim().trimStart('.', ',', ' ')
            .takeIf { it.isNotBlank() && it.length > 5 }
        return FlashCard(
            id = entity.id,
            question = "Who is $name?",
            answer = detail ?: raw,
            hint = "This person was recognized by Face Insights",
            category = "Faces",
            keyTerms = detail?.lowercase()?.split(Regex("\\s+"))?.filter { it.length > 4 } ?: emptyList()
        )
    }

    // "Mood logged: feeling anxious today. ..."
    private fun buildMoodCard(entity: MemoryEntity, raw: String): FlashCard? {
        val mood = Regex("""(?:feeling|mood(?::\s*)?|felt)\s+(\w+)""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return FlashCard(
            id = entity.id,
            question = "What mood did you record?",
            answer = mood.replaceFirstChar { it.uppercase() },
            hint = "A mood check-in was logged",
            category = "Mood",
            keyTerms = listOf(mood.lowercase())
        )
    }

    // "Location event: arrived at Home Zone"
    private fun buildLocationCard(entity: MemoryEntity, raw: String): FlashCard? {
        val zoneMatch = Regex("""(?:entered|arrived at|left|exited)\s+(.+)""", RegexOption.IGNORE_CASE).find(raw)
        val zone = zoneMatch?.groupValues?.getOrNull(1)?.trim() ?: return null
        val action = Regex("""^(entered|arrived at|left|exited)""", RegexOption.IGNORE_CASE).find(raw)
            ?.groupValues?.getOrNull(1) ?: "visited"
        return FlashCard(
            id = entity.id,
            question = "What location event was recorded?",
            answer = "$action $zone",
            hint = "A geofence event triggered this",
            category = "Location",
            keyTerms = zone.lowercase().split(Regex("\\s+")).filter { it.length > 3 }
        )
    }

    // Generic: use the title as a cue and full text as the answer
    private fun buildGenericCard(entity: MemoryEntity, raw: String): FlashCard? {
        val title = entity.title?.trim()?.takeIf { it.isNotBlank() && it != "Memory entry" } ?: return null
        val genericTitles = setOf("text", "note", "object", "remote", "voice", "image", "chatbot")
        if (title.lowercase().split(" ").all { it in genericTitles }) return null
        val shortAnswer = raw.take(180) + if (raw.length > 180) "…" else ""
        return FlashCard(
            id = entity.id,
            question = "What happened in the memory titled \"$title\"?",
            answer = shortAnswer,
            hint = "Try to recall the specific details",
            category = "Memory"
        )
    }
}
