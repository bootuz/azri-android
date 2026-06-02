package nart.simpleanki.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.domain.fsrs.FsrsParameters
import nart.simpleanki.core.domain.fsrs.FsrsPreset

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
    val dailyGoalEnabled: Boolean = true,
    val newCardsTarget: Int = 10,
    val reviewCardsTarget: Int = 20,
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
            dailyGoalEnabled = prefs[DAILY_GOAL_ENABLED] ?: true,
            newCardsTarget = prefs[NEW_TARGET] ?: 10,
            reviewCardsTarget = prefs[REVIEW_TARGET] ?: 20,
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
    }
}
