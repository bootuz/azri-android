package nart.simpleanki.core.notifications

import nart.simpleanki.core.data.settings.AppSettings
import nart.simpleanki.core.data.settings.dailyGoalTotal

/** The two daily reminders. The string [key] is the unique WorkManager name + notification id seed. */
enum class ReminderType(val key: String) {
    Study("reminder_study"),
    Goal("reminder_goal"),
}

/** What a reminder should display when it fires. */
data class NotificationContent(val title: String, val body: String)

/**
 * Pure decision: given the current settings + today's progress, what (if anything) should the
 * [type] reminder post right now? Returns null to post nothing.
 *
 * - Study: skip when there's nothing ready to study.
 * - Goal: skip unless goal tracking is on and the goal isn't met yet.
 *
 * Kept free of Android/WorkManager so the interesting logic is unit-testable.
 */
fun reminderContent(
    type: ReminderType,
    settings: AppSettings,
    studiedToday: Int,
    readyCount: Int,
): NotificationContent? = when (type) {
    ReminderType.Study -> {
        if (readyCount <= 0) null
        else NotificationContent(
            title = "Time to study",
            body = "You have $readyCount ${cards(readyCount)} ready — a quick session keeps you sharp.",
        )
    }

    ReminderType.Goal -> {
        val remaining = settings.dailyGoalTotal - studiedToday
        if (!settings.dailyGoalEnabled || settings.dailyGoalTotal <= 0 || remaining <= 0) null
        else NotificationContent(
            title = "Daily goal",
            body = "You're $remaining ${cards(remaining)} short of today's goal. A few minutes gets you there.",
        )
    }
}

private fun cards(n: Int) = if (n == 1) "card" else "cards"
