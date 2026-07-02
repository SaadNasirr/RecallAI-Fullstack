package com.example.recallai.memories

import androidx.navigation.NavController
import com.example.recallai.chat.ChatMessageUi
import com.example.recallai.data.local.MemoryEntity
import com.example.recallai.ui.screens.AuthRoute
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object MemoryNavigation {
    const val SESSION_TAG_PREFIX = "session:"

    fun routeFor(memory: MemoryEntity, isCaregiver: Boolean): String =
        routeForType(memory.type, memory.text, memory.tags, isCaregiver)

    fun routeForType(
        type: String,
        text: String = "",
        tags: String? = null,
        isCaregiver: Boolean = false
    ): String {
        if (isCareTaskMemory(text, tags)) {
            return if (isCaregiver) AuthRoute.AlertCenter.route else AuthRoute.Reminders.route
        }
        return when (type.uppercase(Locale.US)) {
            "CHATBOT", "CHATBOT_NOTE", "VOICE_CHATBOT", "VOICE" -> AuthRoute.Chat.route
            "RECALL_ASSISTANT", "RECALL_NOTE" -> AuthRoute.RecallAssistant.route
            "OBJECT_DETECTION", "OBJECT_IMPORTANT" -> AuthRoute.ObjectLocator.route
            "FACE_ANALYSIS" -> AuthRoute.FaceInsights.route
            "EMERGENCY_EVENT" -> AuthRoute.Emergency.route
            "MEDICATION", "MEDICATION_LOG", "MEDICATION_ALERT" -> AuthRoute.Medication.route
            "ROUTINE_LOG" -> AuthRoute.Routine.route
            "PEOPLE_MEMORY" -> AuthRoute.PeopleBook.route
            "GEOFENCE_ALERT", "GEOFENCE_ENTER", "GEOFENCE_EXIT" -> AuthRoute.Geofencing.route
            "DEMO" -> AuthRoute.DemoMode.route
            "MOOD_CHECKIN", "MOOD CHECKIN" -> AuthRoute.PatientHome.route
            "PATIENT_ALARM", "PATIENT ALARM" -> AuthRoute.Reminders.route
            else -> if (isCaregiver) AuthRoute.CaregiverHome.route else AuthRoute.Chat.route
        }
    }

    fun sessionIdFromTags(tags: String?): String? =
        tags?.split(",")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith(SESSION_TAG_PREFIX, ignoreCase = true) }
            ?.substring(SESSION_TAG_PREFIX.length)
            ?.takeIf { it.isNotBlank() }

    fun sessionTag(sessionId: String): String = "$SESSION_TAG_PREFIX$sessionId"

    private fun isCareTaskMemory(text: String, tags: String?): Boolean {
        if (text.contains("[CARE_TASK]", ignoreCase = true)) return true
        val t = tags?.lowercase(Locale.US).orEmpty()
        return t.contains("care_task") || t.contains("care-task")
    }

    private val parseIdSeq = AtomicLong(0L)

    fun parseChatMessages(text: String): List<ChatMessageUi> {
        val out = mutableListOf<ChatMessageUi>()
        var fromUser: Boolean? = null
        val buffer = StringBuilder()

        fun flush() {
            if (fromUser == null) return
            val body = buffer.toString().trim()
            buffer.clear()
            if (body.isNotEmpty()) {
                out.add(
                    ChatMessageUi(
                        id = parseIdSeq.decrementAndGet(),
                        fromUser = fromUser == true,
                        text = body
                    )
                )
            }
            fromUser = null
        }

        for (line in text.lines()) {
            val userPrefix = when {
                line.startsWith("User (voice):", ignoreCase = true) -> "User (voice):"
                line.startsWith("User:", ignoreCase = true) -> "User:"
                else -> null
            }
            if (userPrefix != null) {
                flush()
                fromUser = true
                buffer.append(line.removePrefix(userPrefix).trimStart())
                continue
            }
            if (line.startsWith("Therapist:", ignoreCase = true)) {
                flush()
                fromUser = false
                buffer.append(line.removePrefix("Therapist:").trimStart())
                continue
            }
            if (fromUser != null) {
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(line)
            }
        }
        flush()

        if (out.isEmpty() && text.isNotBlank()) {
            out.add(
                ChatMessageUi(
                    id = parseIdSeq.decrementAndGet(),
                    fromUser = false,
                    text = text.trim()
                )
            )
        }
        return out
    }

    fun parseRecallQuery(full: String): String? {
        val marker = "Recall Query:"
        val idx = full.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val after = full.substring(idx + marker.length).trimStart()
        val end = after.indexOf("\n\nAnswer")
        val end2 = after.indexOf("\nAnswer")
        val cut = when {
            end >= 0 -> after.substring(0, end)
            end2 >= 0 -> after.substring(0, end2)
            else -> after.substringBefore("\n\n").ifBlank { after.substringBefore("\n") }
        }
        return cut.trim().takeIf { it.isNotBlank() }
    }

    fun parseRecallAnswer(full: String): String {
        val afterAnswer = full.substringAfter("Answer:\n", "").substringAfter("Answer:", "")
        var body = if (afterAnswer.isNotBlank()) afterAnswer else full
        body = body.substringBefore("\n\nSources").substringBefore("\nSources:").trim()
        return body.ifBlank { full.trim() }
    }

    fun parseObjectQuery(full: String): String? {
        val q = full.substringAfter("Query:", "").substringBefore("\nResult:").trim()
        return q.takeIf { it.isNotBlank() }
    }

    fun parseObjectResult(full: String): String {
        val after = full.substringAfter("Result:\n", "").substringAfter("Result:", "")
        val body = if (after.isNotBlank()) after else full
        return body.lineSequence().firstOrNull()?.trim()?.take(280) ?: full.take(280)
    }
}

fun NavController.openMemoryFromTimeline(
    memory: MemoryEntity,
    coordinator: MemoryOpenCoordinator,
    isCaregiver: Boolean
) {
    coordinator.open(memory)
    navigate(MemoryNavigation.routeFor(memory, isCaregiver)) {
        launchSingleTop = true
    }
}
