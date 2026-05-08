package com.pupilometro.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pupilometro_settings")

/**
 * Calidades de video disponibles, ordenadas de mayor a menor.
 * AUTO = el dispositivo elige la máxima que soporta.
 */
enum class VideoQualityOption(val label: String, val description: String) {
    AUTO("Automático (máxima)", "Usa la mayor calidad que soporte el dispositivo"),
    UHD("UHD 4K", "3840 × 2160 — máximo detalle, archivos grandes"),
    FHD("FHD 1080p", "1920 × 1080 — alta calidad, recomendado"),
    HD("HD 720p", "1280 × 720 — calidad media, archivos pequeños"),
    SD("SD 480p", "720 × 480 — mínima calidad")
}

class SettingsRepository(private val context: Context) {

    companion object {
        val TIEMPO_BASAL         = longPreferencesKey("tiempo_basal")
        val DURACION_FLASH       = longPreferencesKey("duracion_flash")
        val TIEMPO_REDILATACION  = longPreferencesKey("tiempo_redilatacion")
        val VIDEO_QUALITY        = stringPreferencesKey("video_quality")

        const val DEFAULT_TIEMPO_BASAL        = 2000L
        const val DEFAULT_DURACION_FLASH      = 1000L
        const val DEFAULT_TIEMPO_REDILATACION = 5000L
        val DEFAULT_VIDEO_QUALITY             = VideoQualityOption.AUTO
    }

    val settingsFlow: Flow<PupilometroSettings> = context.settingsDataStore.data.map { prefs ->
        PupilometroSettings(
            tiempoBasal        = prefs[TIEMPO_BASAL]        ?: DEFAULT_TIEMPO_BASAL,
            duracionFlash      = prefs[DURACION_FLASH]      ?: DEFAULT_DURACION_FLASH,
            tiempoRedilatacion = prefs[TIEMPO_REDILATACION] ?: DEFAULT_TIEMPO_REDILATACION,
            videoQuality       = prefs[VIDEO_QUALITY]
                ?.let { runCatching { VideoQualityOption.valueOf(it) }.getOrNull() }
                ?: DEFAULT_VIDEO_QUALITY
        )
    }

    suspend fun saveSettings(settings: PupilometroSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[TIEMPO_BASAL]        = settings.tiempoBasal
            prefs[DURACION_FLASH]      = settings.duracionFlash
            prefs[TIEMPO_REDILATACION] = settings.tiempoRedilatacion
            prefs[VIDEO_QUALITY]       = settings.videoQuality.name
        }
    }
}

data class PupilometroSettings(
    val tiempoBasal:        Long              = SettingsRepository.DEFAULT_TIEMPO_BASAL,
    val duracionFlash:      Long              = SettingsRepository.DEFAULT_DURACION_FLASH,
    val tiempoRedilatacion: Long              = SettingsRepository.DEFAULT_TIEMPO_REDILATACION,
    val videoQuality:       VideoQualityOption = SettingsRepository.DEFAULT_VIDEO_QUALITY
)
