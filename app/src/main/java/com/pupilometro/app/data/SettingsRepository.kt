package com.pupilometro.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensión para crear el DataStore una sola vez por contexto
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pupilometro_settings")

/**
 * Repositorio de configuración usando DataStore.
 * Guarda y recupera los tiempos del protocolo de grabación.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        val TIEMPO_BASAL = longPreferencesKey("tiempo_basal")
        val DURACION_FLASH = longPreferencesKey("duracion_flash")
        val TIEMPO_REDILATACION = longPreferencesKey("tiempo_redilatacion")

        // Valores por defecto en milisegundos
        const val DEFAULT_TIEMPO_BASAL = 2000L
        const val DEFAULT_DURACION_FLASH = 1000L
        const val DEFAULT_TIEMPO_REDILATACION = 5000L
    }

    /** Flow con los ajustes actuales */
    val settingsFlow: Flow<PupilometroSettings> = context.settingsDataStore.data.map { prefs ->
        PupilometroSettings(
            tiempoBasal = prefs[TIEMPO_BASAL] ?: DEFAULT_TIEMPO_BASAL,
            duracionFlash = prefs[DURACION_FLASH] ?: DEFAULT_DURACION_FLASH,
            tiempoRedilatacion = prefs[TIEMPO_REDILATACION] ?: DEFAULT_TIEMPO_REDILATACION
        )
    }

    /** Guarda los ajustes en DataStore */
    suspend fun saveSettings(settings: PupilometroSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[TIEMPO_BASAL] = settings.tiempoBasal
            prefs[DURACION_FLASH] = settings.duracionFlash
            prefs[TIEMPO_REDILATACION] = settings.tiempoRedilatacion
        }
    }
}

/**
 * Modelo de datos para la configuración del pupilómetro.
 */
data class PupilometroSettings(
    val tiempoBasal: Long = SettingsRepository.DEFAULT_TIEMPO_BASAL,
    val duracionFlash: Long = SettingsRepository.DEFAULT_DURACION_FLASH,
    val tiempoRedilatacion: Long = SettingsRepository.DEFAULT_TIEMPO_REDILATACION
)
