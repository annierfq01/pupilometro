package com.pupilometro.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pupilometro.app.data.PupilometroSettings
import com.pupilometro.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private var repository: SettingsRepository? = null

    private val _settings = MutableStateFlow(PupilometroSettings())
    val settings: StateFlow<PupilometroSettings> = _settings.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun init(context: Context) {
        repository = SettingsRepository(context)
        viewModelScope.launch {
            repository!!.settingsFlow.collect { s ->
                _settings.value = s
            }
        }
    }

    fun updateTiempoBasal(ms: Long) {
        _settings.value = _settings.value.copy(tiempoBasal = ms)
    }

    fun updateDuracionFlash(ms: Long) {
        _settings.value = _settings.value.copy(duracionFlash = ms)
    }

    fun updateTiempoRedilatacion(ms: Long) {
        _settings.value = _settings.value.copy(tiempoRedilatacion = ms)
    }

    fun saveSettings() {
        viewModelScope.launch {
            repository?.saveSettings(_settings.value)
            _saveSuccess.value = true
        }
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
