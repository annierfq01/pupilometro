package com.pupilometro.app.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pupilometro.app.data.PupilometroSettings
import com.pupilometro.app.data.SettingsRepository
import com.pupilometro.app.data.VideoQualityOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class RecordingState {
    object Idle : RecordingState()
    object WaitingFocusLock : RecordingState()
    object RecordingBasal : RecordingState()
    object FlashOn : RecordingState()
    object RecordingRecovery : RecordingState()
    data class Finished(val filePath: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

class CameraViewModel : ViewModel() {

    private val TAG = "CameraViewModel"

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _isFocusLocked = MutableStateFlow(false)
    val isFocusLocked: StateFlow<Boolean> = _isFocusLocked.asStateFlow()

    private val _statusMessage = MutableStateFlow("Listo. Toca la pantalla para enfocar.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    // Calidad activa mostrada en UI
    private val _activeQualityLabel = MutableStateFlow("—")
    val activeQualityLabel: StateFlow<String> = _activeQualityLabel.asStateFlow()

    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var settingsRepository: SettingsRepository? = null
    private var currentSettings = PupilometroSettings()

    fun init(context: Context) {
        settingsRepository = SettingsRepository(context)
        viewModelScope.launch {
            settingsRepository!!.settingsFlow.collect { settings ->
                currentSettings = settings
                _totalDurationMs.value =
                    settings.tiempoBasal + settings.duracionFlash + settings.tiempoRedilatacion
            }
        }
    }

    /**
     * Construye el QualitySelector según la preferencia del usuario.
     * AUTO = lista completa de mayor a menor, el dispositivo elige la máxima soportada.
     * Cualquier otra opción = esa calidad primero, con fallback a la siguiente inferior.
     */
    private fun buildQualitySelector(quality: VideoQualityOption): QualitySelector {
        return when (quality) {
            VideoQualityOption.AUTO -> QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            )
            VideoQualityOption.UHD -> QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            )
            VideoQualityOption.FHD -> QualitySelector.fromOrderedList(
                listOf(Quality.FHD, Quality.HD, Quality.SD)
            )
            VideoQualityOption.HD -> QualitySelector.fromOrderedList(
                listOf(Quality.HD, Quality.SD)
            )
            VideoQualityOption.SD -> QualitySelector.from(Quality.SD)
        }
    }

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val qualitySelector = buildQualitySelector(currentSettings.videoQuality)

            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            // Actualizar etiqueta de calidad activa
            _activeQualityLabel.value = currentSettings.videoQuality.label

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )
                cameraControl = camera?.cameraControl
                Log.d(TAG, "Cámara vinculada. Calidad: ${currentSettings.videoQuality.name}, API: ${Build.VERSION.SDK_INT}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular cámara: ${e.message}")
                _recordingState.value = RecordingState.Error("Error de cámara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun tapToFocus(meteringPointFactory: MeteringPointFactory, x: Float, y: Float) {
        val point = meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
        cameraControl?.startFocusAndMetering(action)
        _isFocusLocked.value = false
        _statusMessage.value = "Enfocando... Presiona 'Fijar Foco' cuando esté nítido."
    }

    fun lockFocusAndExposure() {
        cameraControl?.cancelFocusAndMetering()
        _isFocusLocked.value = true
        _statusMessage.value = "✅ Foco y exposición bloqueados. Listo para grabar."
    }

    fun unlockFocus() {
        _isFocusLocked.value = false
        _statusMessage.value = "Foco desbloqueado. Toca la pantalla para re-enfocar."
    }

    fun startProtocol(context: Context) {
        if (!_isFocusLocked.value) {
            _recordingState.value = RecordingState.WaitingFocusLock
            _statusMessage.value = "⚠️ Debes fijar el foco antes de grabar."
            return
        }
        val vc = videoCapture ?: run {
            _recordingState.value = RecordingState.Error("Cámara no disponible.")
            return
        }

        val settings = currentSettings
        _totalDurationMs.value =
            settings.tiempoBasal + settings.duracionFlash + settings.tiempoRedilatacion

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(System.currentTimeMillis())
        val fileName = "Pupilometro_$timestamp"

        @Suppress("MissingPermission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Pupilometro")
            }
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            activeRecording = vc.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    handleRecordEvent(event, "Movies/Pupilometro/$fileName.mp4")
                }
        } else {
            // Android 8 / 9
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val outputDir = File(moviesDir, "Pupilometro").also { it.mkdirs() }
            val outputFile = File(outputDir, "$fileName.mp4")
            val outputOptions = FileOutputOptions.Builder(outputFile).build()

            activeRecording = vc.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    handleRecordEvent(event, outputFile.absolutePath)
                }
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            _recordingState.value = RecordingState.RecordingBasal
            _statusMessage.value = "📹 Fase basal (${settings.tiempoBasal / 1000}s)..."
            _progressMs.value = 0L
            val basalEnd = startTime + settings.tiempoBasal
            while (System.currentTimeMillis() < basalEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }

            _recordingState.value = RecordingState.FlashOn
            _statusMessage.value = "⚡ Flash encendido (${settings.duracionFlash / 1000}s)..."
            cameraControl?.enableTorch(true)
            val flashEnd = basalEnd + settings.duracionFlash
            while (System.currentTimeMillis() < flashEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }
            cameraControl?.enableTorch(false)

            _recordingState.value = RecordingState.RecordingRecovery
            _statusMessage.value = "👁️ Redilatación (${settings.tiempoRedilatacion / 1000}s)..."
            val recoveryEnd = flashEnd + settings.tiempoRedilatacion
            while (System.currentTimeMillis() < recoveryEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }
            _progressMs.value = _totalDurationMs.value

            delay(500) // Buffer flush — crítico en Android 8
            activeRecording?.stop()
            activeRecording = null
        }
    }

    private fun handleRecordEvent(event: VideoRecordEvent, filePath: String) {
        when (event) {
            is VideoRecordEvent.Start ->
                Log.d(TAG, "Grabación iniciada")
            is VideoRecordEvent.Status ->
                Log.v(TAG, "Grabando: ${event.recordingStats.numBytesRecorded} bytes")
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    val errorMsg = when (event.error) {
                        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE ->
                            "Sin espacio de almacenamiento"
                        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED ->
                            "Límite de tamaño de archivo"
                        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                            "Sin datos válidos (¿permisos?)"
                        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ->
                            "Cámara desactivada durante grabación"
                        else -> "Error código: ${event.error}"
                    }
                    Log.e(TAG, "Error: $errorMsg")
                    _recordingState.value = RecordingState.Error(errorMsg)
                    _statusMessage.value = "❌ $errorMsg"
                } else {
                    Log.d(TAG, "Guardado en: $filePath")
                    _recordingState.value = RecordingState.Finished(filePath)
                    _statusMessage.value = "✅ Video guardado en:\n$filePath"
                }
            }
            else -> {}
        }
    }

    fun cancelRecording() {
        cameraControl?.enableTorch(false)
        activeRecording?.stop()
        activeRecording = null
        _recordingState.value = RecordingState.Idle
        _progressMs.value = 0L
        _statusMessage.value = "Grabación cancelada."
    }

    fun reset() {
        _recordingState.value = RecordingState.Idle
        _progressMs.value = 0L
        _statusMessage.value = "Listo. Toca la pantalla para enfocar."
    }

    override fun onCleared() {
        super.onCleared()
        cameraControl?.enableTorch(false)
        activeRecording?.stop()
    }
}
