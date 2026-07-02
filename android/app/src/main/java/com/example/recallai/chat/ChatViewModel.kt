package com.example.recallai.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recallai.data.AuthManager
import com.example.recallai.data.ChatRepository
import com.example.recallai.data.MemoryRepository
import com.example.recallai.data.remote.ApiClient
import com.example.recallai.data.local.ReminderEntity
import com.example.recallai.data.local.ReminderRepeatMode
import com.example.recallai.data.local.ReminderStatus
import com.example.recallai.reminders.ReminderChatFlow
import com.example.recallai.reminders.ReminderDraft
import com.example.recallai.reminders.ReminderNlp
import com.example.recallai.reminders.ReminderRepository
import com.example.recallai.reminders.ReminderScheduler
import com.example.recallai.reminders.ReminderUiFormatter
import com.example.recallai.reminders.ReminderWizardState
import com.example.recallai.reminders.ReminderWizardStep
import com.example.recallai.reminders.ScheduleParseResult
import com.example.recallai.memories.MemoryNavigation
import com.example.recallai.memories.MemoryOpenCoordinator
import com.example.recallai.memories.MemoryOpenPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private val chatMessageSeq = AtomicLong(0L)

data class ChatMessageUi(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val pendingReminder: ReminderDraft? = null,
    val reminderWizard: ReminderWizardState? = null,
    val patientNavigation: ChatPatientNavigation? = null,
    val pendingScreenOffer: ChatPatientNavigation? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val memoryRepo: MemoryRepository,
    private val reminderRepo: ReminderRepository,
    private val memoryOpenCoordinator: MemoryOpenCoordinator
) : ViewModel() {

    fun applyPendingMemoryOpen() {
        memoryOpenCoordinator.consume()?.let { openFromMemory(it) }
    }

    private fun openFromMemory(payload: MemoryOpenPayload) {
        MemoryNavigation.sessionIdFromTags(payload.tags)?.let { repo.adoptSession(it) }
        val messages = MemoryNavigation.parseChatMessages(payload.text)
        uiState = uiState.copy(
            messages = messages,
            input = "",
            isSending = false,
            error = null,
            info = "Opened: ${payload.contextLabel()}",
            pendingReminder = null,
            reminderWizard = null
        )
    }

    private fun chatMemoryTags(): List<String> {
        val base = mutableListOf("chat", "therapist")
        repo.currentSessionId()?.let { base.add(MemoryNavigation.sessionTag(it)) }
        return base
    }

    private fun newChatMessage(fromUser: Boolean, text: String) = ChatMessageUi(
        id = chatMessageSeq.incrementAndGet(),
        fromUser = fromUser,
        text = text
    )

    var uiState by mutableStateOf(ChatUiState())
        private set

    private fun humanizeChatError(e: Throwable): String = when (e) {
        is HttpException -> when (e.code()) {
            530, 522, 521, 520 ->
                "Connection dropped (Cloudflare ${e.code()}). The tunnel to your laptop closed—restart \"dev:all\", wait for the new URL to sync to the app, rebuild if needed, then try again."
            502, 503, 504 ->
                "Server temporarily unavailable (${e.code()}). Try again in a few seconds."
            401 -> "Session expired. Please log in again."
            else ->
                runCatching { e.response()?.errorBody()?.string()?.trim() }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: e.message?.takeIf { it.isNotBlank() }
                    ?: "Request failed (${e.code()})."
        }
        is IOException ->
            e.message?.takeIf { it.isNotBlank() }
                ?: "Network error. Check Wi‑Fi and that the backend is running."
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Error sending message."
    }

    fun onInputChange(newText: String) {
        uiState = uiState.copy(input = newText, error = null, info = null)
    }

    fun clearPatientNavigation() {
        uiState = uiState.copy(patientNavigation = null)
    }

    private fun isPatientRole(): Boolean =
        !AuthManager.userRole.equals("caregiver", ignoreCase = true)

    private fun patientDisplayName(): String? =
        AuthManager.userName?.trim()?.takeIf { it.isNotBlank() }

    private fun tryHandlePatientIntent(userText: String): Boolean {
        if (!isPatientRole()) return false

        uiState.pendingScreenOffer?.let { pending ->
            when (ChatPatientIntents.resolveConsent(userText)) {
                ScreenOpenConsent.Yes -> {
                    appendPatientIntentExchange(
                        userText = userText,
                        botReply = ChatPatientIntents.confirmOpenReply(pending),
                        navigateTo = pending
                    )
                    return true
                }
                ScreenOpenConsent.No -> {
                    appendPatientIntentExchange(
                        userText = userText,
                        botReply = ChatPatientIntents.declineOpenReply(pending),
                        clearPendingOffer = true
                    )
                    return true
                }
                ScreenOpenConsent.Unclear -> {
                    appendPatientIntentExchange(
                        userText = userText,
                        botReply = ChatPatientIntents.askAgainReply(pending),
                        keepPendingOffer = true
                    )
                    return true
                }
                null -> Unit
            }
        }

        return when (val result = ChatPatientIntents.handle(userText, patientDisplayName())) {
            is PatientIntentHandled.ContinueToApi -> false
            is PatientIntentHandled.Local -> {
                appendPatientIntentExchange(
                    userText = userText,
                    botReply = result.botReply,
                    navigateTo = result.navigation,
                    screenOffer = result.screenOffer,
                    keepPendingOffer = result.keepPendingOffer
                )
                true
            }
        }
    }

    private fun appendPatientIntentExchange(
        userText: String,
        botReply: String,
        navigateTo: ChatPatientNavigation? = null,
        screenOffer: ChatPatientNavigation? = null,
        keepPendingOffer: Boolean = false,
        clearPendingOffer: Boolean = false
    ) {
        val nextOffer = when {
            navigateTo != null -> null
            clearPendingOffer -> null
            screenOffer != null -> screenOffer
            keepPendingOffer -> uiState.pendingScreenOffer
            else -> null
        }
        uiState = uiState.copy(
            input = "",
            isSending = false,
            error = null,
            info = null,
            messages = uiState.messages +
                newChatMessage(fromUser = true, text = userText) +
                newChatMessage(fromUser = false, text = botReply),
            patientNavigation = navigateTo,
            pendingScreenOffer = nextOffer
        )
        viewModelScope.launch {
            val memoryText = "User: $userText\nTherapist: $botReply"
            memoryRepo.saveTextMemory(
                text = memoryText,
                title = userText.take(50).let { if (it.length < userText.length) "$it..." else it },
                type = "CHATBOT",
                tags = chatMemoryTags()
            )
        }
    }

    fun send() {
        val text = uiState.input.trim()
        if (text.isEmpty() || uiState.isSending) return

        if (uiState.reminderWizard != null) {
            handleWizardReply(text)
            return
        }

        val contextualText = withTemporalContext(text)

        val fullDraft = ReminderNlp.tryParseCompleteReminder(text)
        val intentOnly = ReminderNlp.looksLikeReminderIntent(text) && fullDraft == null

        if (intentOnly) {
            val topicGuess = ReminderNlp.extractReminderTopic(text)
            val step = if (topicGuess.isBlank()) ReminderWizardStep.TOPIC else ReminderWizardStep.SCHEDULE
            val wizard = ReminderWizardState(step = step, topic = topicGuess)
            val firstPrompt =
                if (step == ReminderWizardStep.TOPIC) {
                    ReminderChatFlow.promptForStep(ReminderWizardStep.TOPIC, "")
                } else {
                    ReminderChatFlow.promptForStep(ReminderWizardStep.SCHEDULE, topicGuess)
                }
            uiState = uiState.copy(
                messages = uiState.messages + newChatMessage(fromUser = true, text = text) +
                    newChatMessage(fromUser = false, text = firstPrompt),
                input = "",
                isSending = false,
                info = null,
                error = null,
                reminderWizard = wizard
            )
            return
        }

        if (fullDraft != null) {
            uiState = uiState.copy(pendingReminder = fullDraft, info = null, error = null)
        }

        if (tryHandlePatientIntent(text)) {
            return
        }

        uiState = uiState.copy(
            input = "",
            isSending = true,
            info = null,
            messages = uiState.messages + newChatMessage(fromUser = true, text = text)
        )

        viewModelScope.launch {
            try {
                val resp = repo.sendMessage(contextualText)
                uiState = uiState.copy(
                    isSending = false,
                    messages = uiState.messages + newChatMessage(
                        fromUser = false,
                        text = resp.message
                    )
                )

                val memoryText = "User: $text\nTherapist: ${resp.message}"
                memoryRepo.saveTextMemory(
                    text = memoryText,
                    title = text.take(50).let { if (it.length < text.length) "$it..." else it },
                    type = "CHATBOT",
                    tags = chatMemoryTags()
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isSending = false,
                    error = humanizeChatError(e)
                )
            }
        }
    }

    private fun handleWizardReply(text: String) {
        val w = uiState.reminderWizard ?: return
        val userMsg = newChatMessage(fromUser = true, text = text)
        val msgs = uiState.messages + userMsg
        when (w.step) {
            ReminderWizardStep.TOPIC -> {
                val topic = text.trim()
                val next = ReminderWizardState(
                    step = ReminderWizardStep.SCHEDULE,
                    topic = topic
                )
                val bot = ReminderChatFlow.promptForStep(ReminderWizardStep.SCHEDULE, topic)
                uiState = uiState.copy(
                    messages = msgs + newChatMessage(fromUser = false, text = bot),
                    input = "",
                    reminderWizard = next,
                    isSending = false,
                    error = null,
                    info = null
                )
            }
            ReminderWizardStep.SCHEDULE -> {
                when (val r = ReminderNlp.parseSchedulePhrase(text)) {
                    is ScheduleParseResult.Ask -> {
                        uiState = uiState.copy(
                            messages = msgs + newChatMessage(fromUser = false, text = r.question),
                            input = "",
                            reminderWizard = w.copy(pendingAsk = r.question),
                            isSending = false
                        )
                    }
                    ScheduleParseResult.Unrecognized -> {
                        uiState = uiState.copy(
                            messages = msgs + newChatMessage(
                                fromUser = false,
                                text = "I didn't catch that — could you say the date or day again? " +
                                    "For example: “every Tuesday” or “June 3rd”."
                            ),
                            input = "",
                            reminderWizard = w,
                            isSending = false
                        )
                    }
                    is ScheduleParseResult.Ok -> {
                        val ed = r.oneOffDate?.toEpochDay()
                        val nw = w.copy(
                            scheduleRaw = text,
                            repeatMode = r.repeatMode,
                            daysOfWeekMask = r.weeklyMask,
                            oneOffDateEpochDay = ed,
                            step = ReminderWizardStep.TIME,
                            pendingAsk = null
                        )
                        val bot = ReminderChatFlow.promptForStep(ReminderWizardStep.TIME, w.topic)
                        uiState = uiState.copy(
                            messages = msgs + newChatMessage(fromUser = false, text = bot),
                            input = "",
                            reminderWizard = nw,
                            isSending = false
                        )
                    }
                }
            }
            ReminderWizardStep.TIME -> {
                val lower = text.lowercase(Locale.getDefault())
                val t = ReminderNlp.parseTimeFromText(lower)
                if (t == null) {
                    uiState = uiState.copy(
                        messages = msgs + newChatMessage(
                            fromUser = false,
                            text = "I didn't catch that — could you say the time again? For example: “9pm” or “2:30 pm”."
                        ),
                        input = "",
                        reminderWizard = w,
                        isSending = false
                    )
                    return
                }
                val h24 = t.hour
                val mi = t.minute
                val scheduleNote = when (w.repeatMode) {
                    ReminderRepeatMode.NONE ->
                        w.oneOffDateEpochDay?.let { ReminderUiFormatter.formatEpochDay(it) }
                            ?: w.scheduleRaw.orEmpty()
                    ReminderRepeatMode.DAILY -> "every day"
                    ReminderRepeatMode.WEEKLY ->
                        ReminderUiFormatter.formatScheduleClause(
                            w.repeatMode,
                            w.daysOfWeekMask,
                            ""
                        ).trim()
                }
                val confirmText =
                    ReminderChatFlow.buildConfirm(w.topic, scheduleNote, t, w.repeatMode, w.daysOfWeekMask)
                val nw = w.copy(hour24 = h24, minute = mi, step = ReminderWizardStep.CONFIRM)
                uiState = uiState.copy(
                    messages = msgs + newChatMessage(fromUser = false, text = confirmText),
                    input = "",
                    reminderWizard = nw,
                    isSending = false
                )
            }
            ReminderWizardStep.CONFIRM -> {
                when {
                    ReminderChatFlow.isNegative(text) -> {
                        uiState = uiState.copy(
                            messages = msgs + newChatMessage(fromUser = false, text = "Okay — I won't save a reminder."),
                            input = "",
                            reminderWizard = null,
                            isSending = false
                        )
                    }
                    ReminderChatFlow.isAffirmative(text) -> {
                        val draft = ReminderChatFlow.wizardToDraft(w)
                        if (draft == null) {
                            uiState = uiState.copy(
                                messages = msgs + newChatMessage(
                                    fromUser = false,
                                    text = "Something went wrong building that reminder. Please try again."
                                ),
                                input = "",
                                reminderWizard = null,
                                isSending = false
                            )
                        } else {
                            val afterSave = msgs + newChatMessage(
                                fromUser = false,
                                text = "Saved your reminder."
                            )
                            viewModelScope.launch {
                                try {
                                    persistReminderFromDraft(draft, draft.warn10Min)
                                    uiState = uiState.copy(
                                        messages = afterSave,
                                        reminderWizard = null,
                                        isSending = false,
                                        info = "Reminder saved."
                                    )
                                } catch (e: Exception) {
                                    uiState = uiState.copy(
                                        messages = msgs + newChatMessage(
                                            fromUser = false,
                                            text = "Couldn't save the reminder. You can add it from the Reminders screen."
                                        ),
                                        reminderWizard = null,
                                        isSending = false,
                                        error = e.message ?: "Failed to save reminder"
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        uiState = uiState.copy(
                            messages = msgs + newChatMessage(
                                fromUser = false,
                                text = "Reply “yes” to save or “no” to cancel."
                            ),
                            input = "",
                            reminderWizard = w,
                            isSending = false
                        )
                    }
                }
            }
        }
    }

    fun clearConversationView() {
        uiState = uiState.copy(
            messages = emptyList(),
            error = null,
            info = "Cleared chat view.",
            reminderWizard = null
        )
    }

    fun saveLatestAssistantMessage() {
        val latestAssistant = uiState.messages.lastOrNull { !it.fromUser }?.text
        if (latestAssistant.isNullOrBlank()) {
            uiState = uiState.copy(info = "No assistant response to save yet.")
            return
        }
        viewModelScope.launch {
            try {
                memoryRepo.saveTextMemory(
                    text = latestAssistant,
                    title = "Therapist insight",
                    type = "CHATBOT_NOTE",
                    tags = chatMemoryTags() + "insight" + "saved"
                )
                uiState = uiState.copy(info = "Saved latest response to memories.")
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message ?: "Failed to save response")
            }
        }
    }

    fun sendVoiceMessage(audioFile: File) {
        if (uiState.isSending) return

        uiState = uiState.copy(isSending = true, error = null, info = null)

        viewModelScope.launch {
            try {
                val sttResp = withContext(Dispatchers.IO) {
                    val requestFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData(
                        name = "audio",
                        filename = audioFile.name,
                        body = requestFile
                    )
                    ApiClient.api.speechToText(body)
                }
                val text = sttResp.text.trim()
                val contextualText = withTemporalContext(text)

                if (text.length < 4) {
                    uiState = uiState.copy(
                        isSending = false,
                        error = "Could not clearly hear you. Please try again."
                    )
                    return@launch
                }

                if (tryHandlePatientIntent(text)) {
                    return@launch
                }

                uiState = uiState.copy(
                    messages = uiState.messages + newChatMessage(
                        fromUser = true,
                        text = text
                    )
                )

                val resp = repo.sendMessage(contextualText)

                uiState = uiState.copy(
                    isSending = false,
                    messages = uiState.messages + newChatMessage(
                        fromUser = false,
                        text = resp.message
                    )
                )

                val memoryText = "User (voice): $text\nTherapist: ${resp.message}"
                memoryRepo.saveTextMemory(
                    text = memoryText,
                    title = text.take(50).let { if (it.length < text.length) "$it..." else it },
                    type = "VOICE_CHATBOT",
                    tags = listOf("voice") + chatMemoryTags()
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isSending = false,
                    error = humanizeChatError(e)
                )
            } finally {
                try {
                    audioFile.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun dismissReminderDraft() {
        uiState = uiState.copy(pendingReminder = null)
    }

    private suspend fun persistReminderFromDraft(draft: ReminderDraft, warn10Min: Boolean) {
        val entity = ReminderEntity(
            title = draft.title.trim().ifBlank { "Reminder" },
            description = draft.description?.trim()?.takeIf { it.isNotBlank() },
            datetime = draft.datetime,
            status = ReminderStatus.PENDING,
            source = "chat",
            warn10Min = warn10Min,
            preset = null,
            repeatMode = draft.repeatMode,
            daysOfWeekMask = draft.daysOfWeekMask
        )
        val id = reminderRepo.add(entity)
        val saved = entity.copy(id = id)
        ReminderScheduler.schedule(reminderRepo.context(), saved)
    }

    fun confirmSaveReminder(
        title: String,
        description: String?,
        datetime: Long,
        warn10Min: Boolean
    ) {
        val draft = uiState.pendingReminder ?: return
        uiState = uiState.copy(pendingReminder = null)
        viewModelScope.launch {
            try {
                val merged = draft.copy(
                    title = title.trim().ifBlank { draft.title },
                    description = description?.trim()?.takeIf { it.isNotBlank() } ?: draft.description,
                    datetime = datetime,
                    warn10Min = warn10Min
                )
                persistReminderFromDraft(merged, warn10Min)
                uiState = uiState.copy(info = "Reminder saved.")
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message ?: "Failed to save reminder")
            }
        }
    }

    private fun withTemporalContext(userText: String): String {
        val lower = userText.lowercase(Locale.getDefault())
        val asksTime = listOf(
            "what day", "what date", "today", "time", "morning", "night", "afternoon", "evening",
            "when should i sleep", "what should i do now", "now"
        ).any { lower.contains(it) }
        if (!asksTime) return userText

        val now = Date()
        val dateFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dayPart = when (SimpleDateFormat("H", Locale.getDefault()).format(now).toInt()) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
        val contextLine = "Current local date/time context: ${dateFmt.format(now)}, ${timeFmt.format(now)} ($dayPart)."
        return "$contextLine\nUser message: $userText"
    }
}