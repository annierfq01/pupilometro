package com.pupilometro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pupilometro.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    // Campos locales (en texto para el TextField)
    var basalText by remember(settings.tiempoBasal) {
        mutableStateOf(settings.tiempoBasal.toString())
    }
    var flashText by remember(settings.duracionFlash) {
        mutableStateOf(settings.duracionFlash.toString())
    }
    var redilatText by remember(settings.tiempoRedilatacion) {
        mutableStateOf(settings.tiempoRedilatacion.toString())
    }

    // Calcular duración total
    val totalSec = ((basalText.toLongOrNull() ?: 0L) +
            (flashText.toLongOrNull() ?: 0L) +
            (redilatText.toLongOrNull() ?: 0L)) / 1000.0

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    // Snackbar al guardar
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(1500)
            viewModel.clearSaveSuccess()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ajustes del Protocolo", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF161B22)
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2128)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Protocolo de grabación",
                        color = Color(0xFF4FC3F7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "La grabación se divide en 3 fases:\n" +
                        "1. 📹 Fase Basal: pupila en reposo\n" +
                        "2. ⚡ Estímulo de flash: contracción pupilar\n" +
                        "3. 👁️ Redilatación: recuperación tras el flash",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // --- Campo: Tiempo Basal ---
            SettingsField(
                label = "Tiempo Basal",
                value = basalText,
                onValueChange = {
                    basalText = it
                    it.toLongOrNull()?.let { ms -> viewModel.updateTiempoBasal(ms) }
                },
                description = "Milisegundos de grabación ANTES del flash",
                example = "Ej: 2000 = 2 segundos",
                color = Color(0xFF4CAF50)
            )

            // --- Campo: Duración Flash ---
            SettingsField(
                label = "Duración del Flash",
                value = flashText,
                onValueChange = {
                    flashText = it
                    it.toLongOrNull()?.let { ms -> viewModel.updateDuracionFlash(ms) }
                },
                description = "Milisegundos que el flash estará encendido",
                example = "Ej: 1000 = 1 segundo",
                color = Color(0xFFFFD600)
            )

            // --- Campo: Tiempo Redilatación ---
            SettingsField(
                label = "Tiempo de Redilatación",
                value = redilatText,
                onValueChange = {
                    redilatText = it
                    it.toLongOrNull()?.let { ms -> viewModel.updateTiempoRedilatacion(ms) }
                },
                description = "Milisegundos de grabación DESPUÉS del flash",
                example = "Ej: 5000 = 5 segundos",
                color = Color(0xFF2196F3)
            )

            // --- Duración total ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2128)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Duración total del video:", color = Color.White, fontSize = 14.sp)
                    Text(
                        "${"%.1f".format(totalSec)} segundos",
                        color = Color(0xFF4FC3F7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // --- Botón Guardar ---
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                if (saveSuccess) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¡Guardado! Volviendo...", color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Ajustes", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    example: String,
    color: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("ms", color = Color.White.copy(alpha = 0.5f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = color,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = color
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(description, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(example, color = color.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}
