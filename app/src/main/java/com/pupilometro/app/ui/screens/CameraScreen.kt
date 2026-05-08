package com.pupilometro.app.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pupilometro.app.viewmodel.CameraViewModel
import com.pupilometro.app.viewmodel.RecordingState

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val recordingState by viewModel.recordingState.collectAsState()
    val isFocusLocked by viewModel.isFocusLocked.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val progressMs by viewModel.progressMs.collectAsState()
    val totalDurationMs by viewModel.totalDurationMs.collectAsState()

    val activeQualityLabel by viewModel.activeQualityLabel.collectAsState()
    val isRecording = recordingState is RecordingState.RecordingBasal ||
            recordingState is RecordingState.FlashOn ||
            recordingState is RecordingState.RecordingRecovery

    val progress by animateFloatAsState(
        targetValue = if (totalDurationMs > 0) progressMs.toFloat() / totalDurationMs.toFloat() else 0f,
        label = "progress"
    )

    val phaseColor = when (recordingState) {
        is RecordingState.FlashOn -> Color(0xFFFFD600)
        is RecordingState.RecordingBasal -> Color(0xFF4CAF50)
        is RecordingState.RecordingRecovery -> Color(0xFF2196F3)
        else -> Color.White
    }

    val phaseLabel = when (recordingState) {
        is RecordingState.RecordingBasal -> "FASE BASAL"
        is RecordingState.FlashOn -> "⚡ FLASH"
        is RecordingState.RecordingRecovery -> "REDILATACIÓN"
        is RecordingState.Finished -> "✅ COMPLETADO"
        is RecordingState.Error -> "❌ ERROR"
        else -> ""
    }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // --- PREVIEW DE CÁMARA ---
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                viewModel.bindCamera(ctx, lifecycleOwner, pv)
                pv
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { _ ->
                        if (!isRecording) { /* tap-to-focus handled via viewModel */ }
                    }
                }
        )

        // --- OVERLAY SUPERIOR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    tint = Color(0xFF4FC3F7),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pupilómetro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Indicador foco
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isFocusLocked) Color(0xFF4CAF50) else Color(0xFFFF9800))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFocusLocked) "FOCO FIJADO" else "FOCO LIBRE",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Botón Settings
                IconButton(onClick = onNavigateToSettings, enabled = !isRecording) {
                    Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = Color.White)
                }
                // Botón About
                IconButton(onClick = onNavigateToAbout, enabled = !isRecording) {
                    Icon(Icons.Default.Info, contentDescription = "Acerca de", tint = Color.White)
                }
            }
        }

        // --- BARRA DE PROGRESO ---
        AnimatedVisibility(
            visible = isRecording || recordingState is RecordingState.Finished,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(phaseLabel, color = phaseColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${(progressMs / 1000.0).let { "%.1f".format(it) }}s / ${totalDurationMs / 1000}s",
                        color = Color.White, fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = phaseColor,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        // --- MENSAJE MODAL (error, foco, completado) ---
        Box(modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)) {
            if (recordingState is RecordingState.WaitingFocusLock ||
                recordingState is RecordingState.Finished ||
                recordingState is RecordingState.Error
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (recordingState) {
                            is RecordingState.Error -> Color(0xFFB71C1C).copy(alpha = 0.9f)
                            is RecordingState.Finished -> Color(0xFF1B5E20).copy(alpha = 0.9f)
                            else -> Color(0xFF212121).copy(alpha = 0.9f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 15.sp
                    )
                }
            }
        }

        // --- CONTROLES INFERIORES ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isRecording &&
                recordingState !is RecordingState.WaitingFocusLock &&
                recordingState !is RecordingState.Finished &&
                recordingState !is RecordingState.Error
            ) {
                Text(
                    text = statusMessage,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Fijar / Desbloquear Foco
                FilledTonalButton(
                    onClick = {
                        if (isFocusLocked) viewModel.unlockFocus()
                        else viewModel.lockFocusAndExposure()
                    },
                    enabled = !isRecording,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isFocusLocked) Color(0xFF1B5E20) else Color(0xFF37474F)
                    ),
                    modifier = Modifier.width(140.dp)
                ) {
                    Icon(
                        imageVector = if (isFocusLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFocusLocked) "Foco Fijado" else "Fijar Foco",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                // Botón principal
                when {
                    isRecording -> {
                        Button(
                            onClick = { viewModel.cancelRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Cancelar", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    recordingState is RecordingState.Finished || recordingState is RecordingState.Error -> {
                        Button(
                            onClick = { viewModel.reset() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = "Nueva grabación", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.startProtocol(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFocusLocked) Color(0xFFD32F2F) else Color(0xFF616161)
                            ),
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = "Iniciar", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    isRecording -> "Toca ⏹ para cancelar"
                    recordingState is RecordingState.Finished -> "Toca 🔄 para nueva grabación"
                    isFocusLocked -> "Listo para grabar"
                    else -> "Fija el foco antes de grabar"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}
