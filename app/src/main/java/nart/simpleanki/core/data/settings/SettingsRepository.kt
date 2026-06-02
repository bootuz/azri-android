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

/** User study settings, mirroring the iOS FSRS preset + daily limits, plus app appearance. */
data class AppSettings(
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val newCardsPerDay: Int = 20,
    val maxReviewsPerDay: Int = 200,
    val themeMode: ThemeMode = ThemeMode.System,
    // Custom-preset parameters — only consumed when [preset] == FsrsPreset.Custom.
    val customRetention: Double = 0.90,
    val customMaxInterval: Int = 365,
    val enableFuzz: Boolean = true,
    val enableShortTerm: Boolean = true,
)

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
    suspend fun setNewCardsPerDay(value: Int)
    suspend fun setMaxReviewsPerDay(value: Int)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setCustomRetention(value: Double)
    suspend fun setCustomMaxInterval(days: Int)
    suspend fun setEnableFuzz(enabled: Boolean)
    suspend fun setEnableShortTerm(enabled: Boolean)
}

private val Context.settingsDataStore by preferencesDataStore("azri_settings")

/** DataStore-backed [SettingsRepository]. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            preset = prefs[PRESET]?.let { runCatching { FsrsPreset.valueOf(it) }.getOrNull() } ?: FsrsPreset.Optimal,
            newCardsPerDay = prefs[NEW_PER_DAY] ?: 20,
            maxReviewsPerDay = prefs[MAX_REVIEWS] ?: 200,
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            customRetention = prefs[CUSTOM_RETENTION] ?: 0.90,
            customMaxInterval = prefs[CUSTOM_MAX_INTERVAL] ?: 365,
            enableFuzz = prefs[ENABLE_FUZZ] ?: true,
            enableShortTerm = prefs[ENABLE_SHORT_TERM] ?: true,
        )
    }

    override suspend fun setPreset(preset: FsrsPreset) {
        context.settingsDataStore.edit { it[PRESET] = preset.name }
    }

    override suspend fun setNewCardsPerDay(value: Int) {
        context.settingsDataStore.edit { it[NEW_PER_DAY] = value.coerceIn(0, 9999) }
    }

    override suspend fun setMaxReviewsPerDay(value: Int) {
        context.settingsDataStore.edit { it[MAX_REVIEWS] = value.coerceIn(0, 9999) }
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

    private companion object {
        val PRESET = stringPreferencesKey("fsrs_preset")
        val NEW_PER_DAY = intPreferencesKey("new_per_day")
        val MAX_REVIEWS = intPreferencesKey("max_reviews")
        val THEME = stringPreferencesKey("theme_mode")
        val CUSTOM_RETENTION = doublePreferencesKey("custom_retention")
        val CUSTOM_MAX_INTERVAL = intPreferencesKey("custom_max_interval")
        val ENABLE_FUZZ = booleanPreferencesKey("enable_fuzz")
        val ENABLE_SHORT_TERM = booleanPreferencesKey("enable_short_term")
    }
}
