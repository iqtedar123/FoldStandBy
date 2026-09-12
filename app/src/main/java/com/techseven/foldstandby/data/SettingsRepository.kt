package com.techseven.foldstandby.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val nightstandEnabled: Boolean = false,
    val forceNightstand: Boolean = false,
    val nightTintEnabled: Boolean = true,
    /** ARGB color for nightstand clock and text (background stays black). */
    val accentColorArgb: Int = DEFAULT_ACCENT_COLOR
) {
    companion object {
        const val DEFAULT_ACCENT_COLOR = 0xFFE8E4DC.toInt()

        val ACCENT_SWATCHES = listOf(
            DEFAULT_ACCENT_COLOR, // warm white
            0xFFFF8A4A.toInt(), // amber
            0xFFFF6B4A.toInt(), // coral
            0xFFFFD166.toInt(), // gold
            0xFF4ADE80.toInt(), // soft green
            0xFF60A5FA.toInt(), // blue
            0xFFC084FC.toInt(), // violet
            0xFFF472B6.toInt(), // pink
            0xFF94A3B8.toInt()  // cool gray
        )
    }
}

class SettingsRepository(private val context: Context) {
    private val nightstandEnabledKey = booleanPreferencesKey("nightstand_enabled")
    private val forceNightstandKey = booleanPreferencesKey("force_nightstand")
    private val nightTintKey = booleanPreferencesKey("night_tint")
    private val accentColorKey = intPreferencesKey("accent_color")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            nightstandEnabled = prefs[nightstandEnabledKey] ?: false,
            forceNightstand = prefs[forceNightstandKey] ?: false,
            nightTintEnabled = prefs[nightTintKey] ?: true,
            accentColorArgb = prefs[accentColorKey] ?: AppSettings.DEFAULT_ACCENT_COLOR
        )
    }

    suspend fun setNightstandEnabled(enabled: Boolean) {
        context.dataStore.edit { it[nightstandEnabledKey] = enabled }
    }

    suspend fun setForceNightstand(enabled: Boolean) {
        context.dataStore.edit { it[forceNightstandKey] = enabled }
    }

    suspend fun setNightTintEnabled(enabled: Boolean) {
        context.dataStore.edit { it[nightTintKey] = enabled }
    }

    suspend fun setAccentColor(argb: Int) {
        context.dataStore.edit { it[accentColorKey] = argb }
    }
}
