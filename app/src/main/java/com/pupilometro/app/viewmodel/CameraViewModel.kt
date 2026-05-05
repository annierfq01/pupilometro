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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Estados posibles de la grabación */
sealed class RecordingState {
    object Idle : RecordingState()
    object WaitingFocusLock : RecordingState()   // No se puede iniciar sin bloquear foco
    object RecordingBasal : RecordingState()      // Fase 1: grabando antes del flash
    object FlashOn : RecordingState()             // Fase 2: flash encendido
    object RecordingRecovery : RecordingState()   // Fase 3: grabando redilatación
    data class Finished(val filePath: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

class CameraViewModel : ViewModel() {

    private val TAG = "CameraViewModel"

    // --- State Flows ---
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

    // --- Internals ---
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var settingsRepository: SettingsRepository? = null
    private var currentSettings = PupilometroSettings()

    /** Inicializa el repositorio y carga ajustes */
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
     * Vincula la cámara al ciclo de vida y configura Preview + VideoCapture.
     * Nota: la estabilización digital no tiene API directa en CameraX 1.3.x;
     * al usar máxima calidad y sin extensiones extra se minimiza el procesado.
     */
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

            // Máxima calidad disponible en el dispositivo
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.fromOrderedList(
                        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
                    )
                )
                .build()

            // VideoCapture usando withOutput (API estable de CameraX 1.3.x)
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                cameraControl = camera?.cameraControl
                Log.d(TAG, "Cámara vinculada correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular cámara: ${e.message}")
                _recordingState.value = RecordingState.Error("Error de cámara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Tap-to-focus: enfoca y ajusta exposición en el punto tocado.
     */
    fun tapToFocus(
        meteringPointFactory: MeteringPointFactory,
        x: Float,
        y: Float
    ) {
        val point = meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()

        cameraControl?.startFocusAndMetering(action)
        _isFocusLocked.value = false
        _statusMessage.value = "Enfocando... Presiona 'Fijar Foco' cuando esté nítido."
        Log.d(TAG, "Tap-to-focus en ($x, $y)")
    }

    /**
     * Bloquea foco y exposición en sus valores actuales.
     * CRÍTICO: evita que el flash altere el enfoque durante la prueba.
     */
    fun lockFocusAndExposure() {
        cameraControl?.cancelFocusAndMetering()
        _isFocusLocked.value = true
        _statusMessage.value = "✅ Foco y exposición bloqueados. Listo para grabar."
        Log.d(TAG, "Foco y exposición bloqueados")
    }

    /**
     * Desbloquea foco y exposición (permite volver a enfocar manualmente).
     */
    fun unlockFocus() {
        _isFocusLocked.value = false
        _statusMessage.value = "Foco desbloqueado. Toca la pantalla para re-enfocar."
    }

    /**
     * Inicia el protocolo completo de grabación:
     * 1. Fase basal → 2. Flash encendido → 3. Redilatación → 4. Finalizar
     */
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

        // Nombre de archivo con timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(System.currentTimeMillis())
        val fileName = "Pupilometro_$timestamp"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Pupilometro"
                )
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        // Iniciar grabación
        @Suppress("MissingPermission")
        activeRecording = vc.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> Log.d(TAG, "Grabación iniciada")
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.e(TAG, "Error en grabación: ${event.error}")
                            _recordingState.value =
                                RecordingState.Error("Error al guardar: código ${event.error}")
                        } else {
                            val uri = event.outputResults.outputUri
                            Log.d(TAG, "Video guardado en: $uri")
                            _recordingState.value = RecordingState.Finished(
                                "Movies/Pupilometro/$fileName.mp4"
                            )
                            _statusMessage.value = "✅ Video guardado en Movies/Pupilometro/"
                        }
                    }
                    else -> {}
                }
            }

        // Ejecutar protocolo asíncrono con tiempos de alta precisión
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()

            // --- FASE 1: BASAL ---
            _recordingState.value = RecordingState.RecordingBasal
            _statusMessage.value = "📹 Fase basal (${settings.tiempoBasal / 1000}s)..."
            _progressMs.value = 0L

            val basalEnd = startTime + settings.tiempoBasal
            while (System.currentTimeMillis() < basalEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }

            // --- FASE 2: FLASH ---
            _recordingState.value = RecordingState.FlashOn
            _statusMessage.value = "⚡ Flash encendido (${settings.duracionFlash / 1000}s)..."
            cameraControl?.enableTorch(true)

            val flashEnd = basalEnd + settings.duracionFlash
            while (System.currentTimeMillis() < flashEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }
            cameraControl?.enableTorch(false)

            // --- FASE 3: REDILATACIÓN ---
            _recordingState.value = RecordingState.RecordingRecovery
            _statusMessage.value = "👁️ Redilatación (${settings.tiempoRedilatacion / 1000}s)..."

            val recoveryEnd = flashEnd + settings.tiempoRedilatacion
            while (System.currentTimeMillis() < recoveryEnd) {
                _progressMs.value = System.currentTimeMillis() - startTime
                delay(50)
            }
            _progressMs.value = _totalDurationMs.value

            // --- FINALIZAR ---
            activeRecording?.stop()
            activeRecording = null
        }
    }

    /** Cancela la grabación en curso */
    fun cancelRecording() {
        cameraControl?.enableTorch(false)
        activeRecording?.stop()
        activeRecording = null
        _recordingState.value = RecordingState.Idle
        _progressMs.value = 0L
        _statusMessage.value = "Grabación cancelada."
    }

    /** Resetea al estado inicial */
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
