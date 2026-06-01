package nart.simpleanki.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nart.simpleanki.core.domain.fsrs.FsrsPreset

/** User study settings, mirroring the iOS FSRS preset + daily limits. */
data class AppSettings(
    val preset: FsrsPreset = FsrsPreset.Optimal,
    val newCardsPerDay: Int = 20,
    val maxReviewsPerDay: Int = 200,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setPreset(preset: FsrsPreset)
    suspend fun setNewCardsPerDay(value: Int)
    suspend fun setMaxReviewsPerDay(value: Int)
}

private val Context.settingsDataStore by preferencesDataStore("azri_settings")

/** DataStore-backed [SettingsRepository]. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            preset = prefs[PRESET]?.let { runCatching { FsrsPreset.valueOf(it) }.getOrNull() } ?: FsrsPreset.Optimal,
            newCardsPerDay = prefs[NEW_PER_DAY] ?: 20,
            maxReviewsPerDay = prefs[MAX_REVIEWS] ?: 200,
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

    private companion object {
        val PRESET = stringPreferencesKey("fsrs_preset")
        val NEW_PER_DAY = intPreferencesKey("new_per_day")
        val MAX_REVIEWS = intPreferencesKey("max_reviews")
    }
}
