package com.pupilometro.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pupilometro.app.ui.screens.CameraScreen
import com.pupilometro.app.ui.screens.SettingsScreen
import com.pupilometro.app.ui.theme.PupilometroTheme
import com.pupilometro.app.viewmodel.CameraViewModel
import com.pupilometro.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PupilometroTheme {
                PupilometroApp(
                    cameraViewModel = cameraViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PupilometroApp(
    cameraViewModel: CameraViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()

    // Permisos requeridos
    val permissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    if (!permissions.allPermissionsGranted) {
        // Pantalla de solicitud de permisos
        PermissionsScreen(
            onRequestPermissions = { permissions.launchMultiplePermissionRequest() },
            shouldShowRationale = permissions.shouldShowRationale
        )
    } else {
        // Navegación principal
        NavHost(
            navController = navController,
            startDestination = "camera"
        ) {
            composable("camera") {
                CameraScreen(
                    viewModel = cameraViewModel,
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun PermissionsScreen(
    onRequestPermissions: () -> Unit,
    shouldShowRationale: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("👁️", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Pupilómetro",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (shouldShowRationale)
                    "Esta app necesita acceso a la cámara y al micrófono para grabar el protocolo de evaluación pupilar."
                else
                    "Para funcionar, Pupilómetro necesita permisos de cámara y micrófono.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    "Conceder Permisos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
