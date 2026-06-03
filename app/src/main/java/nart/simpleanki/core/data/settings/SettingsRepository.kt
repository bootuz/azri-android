package nart.simpleanki.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.domain.fsrs.FsrsParameters
import nart.simpleanki.core.domain.fsrs.FsrsPreset
import nart.simpleanki.core.domain.fsrs.QueueSortOrder

/** App theme preference. [System] follows the device dark/light setting. */
enum class ThemeMode { System, Light, Dark }

/** User study settings: FSRS preset + daily-goal targets + app appearance. */
data class AppSettings(
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val themeMode: ThemeMode = ThemeMode.System,
    // Custom-preset parameters — only consumed when [preset] == FsrsPreset.Custom.
    val customRetention: Double = 0.90,
    val customMaxInterval: Int = 365,
    val enableFuzz: Boolean = true,
    val enableShortTerm: Boolean = true,
    // Daily goal — a SOFT target (does not cap the study queue). Mirrors iOS DailyGoalManager.
    // Off by default: users opt in (e.g. from the "Set up your daily goal" prompt).
    val dailyGoalEnabled: Boolean = false,
    val newCardsTarget: Int = 10,
    val reviewCardsTarget: Int = 20,
    // Notifications — off until the user opts in (and grants POST_NOTIFICATIONS).
    val studyReminderEnabled: Boolean = false,
    val studyReminderHour: Int = 9,
    val studyReminderMinute: Int = 0,
    val goalReminderEnabled: Boolean = false,
    val goalReminderHour: Int = 20,
    val goalReminderMinute: Int = 0,
    // Queue ordering — shared by the queue preview and the study session.
    val queueSortOrder: QueueSortOrder = QueueSortOrder.DueDate,
    val queueShuffleSeed: Long = 0L,
)

/** Total cards-per-day goal = new + review targets (derived, never stored). */
val AppSettings.dailyGoalTotal: Int get() = newCardsTarget + reviewCardsTarget

/** Per-bucket safety rail for goal targets (mirrors iOS minPerBucket/maxPerBucket). */
const val DAILY_GOAL_MIN_PER_BUCKET = 0
const val DAILY_GOAL_MAX_PER_BUCKET = 500

/** Resolves the active scheduler parameters: fixed for built-ins, stored values for Custom. */
fun AppSettings.fsrsParameters(): FsrsParameters =
    preset.fixedParameters ?: FsrsParameters(
        requestRetention = customRetention,
        maximumInterval = customMaxInterval,
        enableFuzz = enableFuzz,
        enableShortTerm = enableShortTerm,
    )

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setPreset(preset: FsrsPreset)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCustomRetention(value: Double)
    suspend fun setCustomMaxInterval(days: Int)
    suspend fun setEnableFuzz(enabled: Boolean)
    suspend fun setEnableShortTerm(enabled: Boolean)
    suspend fun setDailyGoalEnabled(enabled: Boolean)
    suspend fun setNewCardsTarget(value: Int)
    suspend fun setReviewCardsTarget(value: Int)
    suspend fun setStudyReminder(enabled: Boolean, hour: Int, minute: Int)
    suspend fun setGoalReminder(enabled: Boolean, hour: Int, minute: Int)
    suspend fun setQueueSortOrder(order: QueueSortOrder)
    suspend fun setQueueShuffleSeed(seed: Long)
}

private val Context.settingsDataStore by preferencesDataStore("azri_settings")

/** DataStore-backed [SettingsRepository]. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            preset = prefs[PRESET]?.let { runCatching { FsrsPreset.valueOf(it) }.getOrNull() } ?: FsrsPreset.Optimal,
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            customRetention = prefs[CUSTOM_RETENTION] ?: 0.90,
            customMaxInterval = prefs[CUSTOM_MAX_INTERVAL] ?: 365,
            enableFuzz = prefs[ENABLE_FUZZ] ?: true,
            enableShortTerm = prefs[ENABLE_SHORT_TERM] ?: true,
            dailyGoalEnabled = prefs[DAILY_GOAL_ENABLED] ?: false,
            newCardsTarget = prefs[NEW_TARGET] ?: 10,
            reviewCardsTarget = prefs[REVIEW_TARGET] ?: 20,
            studyReminderEnabled = prefs[STUDY_REMINDER_ON] ?: false,
            studyReminderHour = prefs[STUDY_REMINDER_HOUR] ?: 9,
            studyReminderMinute = prefs[STUDY_REMINDER_MIN] ?: 0,
            goalReminderEnabled = prefs[GOAL_REMINDER_ON] ?: false,
            goalReminderHour = prefs[GOAL_REMINDER_HOUR] ?: 20,
            goalReminderMinute = prefs[GOAL_REMINDER_MIN] ?: 0,
            queueSortOrder = prefs[QUEUE_SORT]?.let { runCatching { QueueSortOrder.valueOf(it) }.getOrNull() }
                ?: QueueSortOrder.DueDate,
            queueShuffleSeed = prefs[QUEUE_SHUFFLE_SEED] ?: 0L,
        )
    }

    override suspend fun setPreset(preset: FsrsPreset) {
        context.settingsDataStore.edit { it[PRESET] = preset.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME] = mode.name }
    }

    override suspend fun setCustomRetention(value: Double) {
        context.settingsDataStore.edit { it[CUSTOM_RETENTION] = value.coerceIn(0.80, 0.99) }
    }

    override suspend fun setCustomMaxInterval(days: Int) {
        context.settingsDataStore.edit { it[CUSTOM_MAX_INTERVAL] = days.coerceIn(1, 36500) }
    }

    override suspend fun setEnableFuzz(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLE_FUZZ] = enabled }
    }

    override suspend fun setEnableShortTerm(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLE_SHORT_TERM] = enabled }
    }

    override suspend fun setDailyGoalEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[DAILY_GOAL_ENABLED] = enabled }
    }

    override suspend fun setNewCardsTarget(value: Int) {
        context.settingsDataStore.edit {
            it[NEW_TARGET] = value.coerceIn(DAILY_GOAL_MIN_PER_BUCKET, DAILY_GOAL_MAX_PER_BUCKET)
        }
    }

    override suspend fun setReviewCardsTarget(value: Int) {
        context.settingsDataStore.edit {
            it[REVIEW_TARGET] = value.coerceIn(DAILY_GOAL_MIN_PER_BUCKET, DAILY_GOAL_MAX_PER_BUCKET)
        }
    }

    override suspend fun setStudyReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[STUDY_REMINDER_ON] = enabled
            it[STUDY_REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[STUDY_REMINDER_MIN] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun setGoalReminder(enabled: Boolean, hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[GOAL_REMINDER_ON] = enabled
            it[GOAL_REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[GOAL_REMINDER_MIN] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun setQueueSortOrder(order: QueueSortOrder) {
        context.settingsDataStore.edit { it[QUEUE_SORT] = order.name }
    }

    override suspend fun setQueueShuffleSeed(seed: Long) {
        context.settingsDataStore.edit { it[QUEUE_SHUFFLE_SEED] = seed }
    }

    private companion object {
        val PRESET = stringPreferencesKey("fsrs_preset")
        val THEME = stringPreferencesKey("theme_mode")
        val CUSTOM_RETENTION = doublePreferencesKey("custom_retention")
        val CUSTOM_MAX_INTERVAL = intPreferencesKey("custom_max_interval")
        val ENABLE_FUZZ = booleanPreferencesKey("enable_fuzz")
        val ENABLE_SHORT_TERM = booleanPreferencesKey("enable_short_term")
        val DAILY_GOAL_ENABLED = booleanPreferencesKey("daily_goal_enabled")
        val NEW_TARGET = intPreferencesKey("daily_goal_new")
        val REVIEW_TARGET = intPreferencesKey("daily_goal_review")
        val STUDY_REMINDER_ON = booleanPreferencesKey("study_reminder_on")
        val STUDY_REMINDER_HOUR = intPreferencesKey("study_reminder_hour")
        val STUDY_REMINDER_MIN = intPreferencesKey("study_reminder_min")
        val GOAL_REMINDER_ON = booleanPreferencesKey("goal_reminder_on")
        val GOAL_REMINDER_HOUR = intPreferencesKey("goal_reminder_hour")
        val GOAL_REMINDER_MIN = intPreferencesKey("goal_reminder_min")
        val QUEUE_SORT = stringPreferencesKey("queue_sort_order")
        val QUEUE_SHUFFLE_SEED = longPreferencesKey("queue_shuffle_seed")
    }
}
