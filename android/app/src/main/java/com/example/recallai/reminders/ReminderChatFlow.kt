package com.example.recallai.reminders

import com.example.recallai.data.local.ReminderRepeatMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

enum class ReminderWizardStep {
    TOPIC,
    SCHEDULE,
    TIME,
    CONFIRM
}

data class ReminderWizardState(
    val step: ReminderWizardStep,
    val topic: String = "",
    val scheduleRaw: String? = null,
    val repeatMode: ReminderRepeatMode = ReminderRepeatMode.NONE,
    val daysOfWeekMask: Int = 0,
    val oneOffDateEpochDay: Long? = null,
    val hour24: Int? = null,
    val minute: Int? = null,
    val pendingAsk: String? = null
)

object ReminderChatFlow {

    fun promptForStep(step: ReminderWizardStep, topic: String): String = when (step) {
        ReminderWizardStep.TOPIC ->
            "What would you like to be reminded about?"
        ReminderWizardStep.SCHEDULE ->
            "What date or day should I set this for? " +
                "You can say something like “this Friday”, “every Monday”, or “15 May”."
        ReminderWizardStep.TIME ->
            "What time should I remind you?"
        ReminderWizardStep.CONFIRM ->
            "Shall I save this reminder?"
    }

    fun buildConfirm(
        topic: String,
        scheduleNote: String,
        time: LocalTime,
        repeatMode: ReminderRepeatMode,
        mask: Int
    ): String {
        val t = ReminderUiFormatter.formatTime12(time)
        val sched = ReminderUiFormatter.formatScheduleClause(repeatMode, mask, scheduleNote)
        return "Got it — I'll remind you about ${topic.ifBlank { "that" }} $sched at $t. Shall I save this?"
    }

    fun isAffirmative(text: String): Boolean {
        val s = text.lowercase(Locale.getDefault()).trim()
        return s == "y" || s == "yes" || s == "yeah" || s == "yep" || s == "ok" || s == "okay" ||
            s == "sure" || s == "save" || s == "please" || s.startsWith("save ") || s.contains("save it")
    }

    fun isNegative(text: String): Boolean {
        val s = text.lowercase(Locale.getDefault()).trim()
        return s == "n" || s == "no" || s == "nope" || s == "cancel" || s.contains("don't") || s.contains("do not")
    }

    fun wizardToDraft(w: ReminderWizardState, nowMs: Long = System.currentTimeMillis()): ReminderDraft? {
        val hour = w.hour24 ?: return null
        val mi = w.minute ?: return null
        val zone = ZoneId.systemDefault()
        val time = LocalTime.of(hour, mi)
        val topic = w.topic.trim().ifBlank { "Reminder" }
        val whenMs =
            when (w.repeatMode) {
                ReminderRepeatMode.NONE -> {
                    val ed = w.oneOffDateEpochDay ?: return null
                    val d = LocalDate.ofEpochDay(ed)
                    LocalDateTime.of(d, time).atZone(zone).toInstant().toEpochMilli()
                }
                ReminderRepeatMode.DAILY ->
                    ReminderScheduleHelper.firstDailyTrigger(hour, mi, nowMs)
                ReminderRepeatMode.WEEKLY ->
                    ReminderScheduleHelper.firstWeeklyTrigger(hour, mi, w.daysOfWeekMask, nowMs)
                        ?: return null
            }
        return ReminderDraft(
            title = topic,
            description = null,
            datetime = whenMs,
            warn10Min = true,
            ambiguous = false,
            repeatMode = w.repeatMode,
            daysOfWeekMask = w.daysOfWeekMask
        )
    }
}
