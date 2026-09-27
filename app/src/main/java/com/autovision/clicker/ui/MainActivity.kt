package com.autovision.clicker.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.autovision.clicker.R
import com.autovision.clicker.capture.ScreenCaptureManager
import com.autovision.clicker.capture.ScreenCaptureService
import com.autovision.clicker.models.CaptureInterval
import com.autovision.clicker.models.DashboardState
import com.autovision.clicker.models.ModuleStatus
import com.autovision.clicker.ui.theme.AutoVisionTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Dashboard real do AutoVision Clicker (Fase 2).
 *
 * Controla o ciclo de vida da captura de tela (MediaProjection) e mostra, em tempo
 * real, o status dos módulos, o intervalo de análise configurado e um log simples
 * de eventos. O reconhecimento visual (Vision Lab, engines) chega na Fase 3 —
 * aqui os frames capturados só são contados, ainda não analisados.
 */
class MainActivity : ComponentActivity() {

    private val state = MutableStateFlow(DashboardState())

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val requestCapturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            onCapturePermissionGranted(data, result.resultCode)
        } else {
            appendLog("Permissão de captura de tela negada")
            state.update { it.copy(captureStatus = ModuleStatus.ERROR) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureManager = ScreenCaptureManager(applicationContext, lifecycleScope)
        captureManager.onFrame = { _ ->
            state.update { it.copy(framesCaptured = it.framesCaptured + 1) }
        }
        captureManager.onCaptureStopped = {
            state.update {
                it.copy(captureStatus = ModuleStatus.DISABLED)
            }
            appendLog("Captura interrompida")
        }

        setContent {
            AutoVisionTheme {
                val dashboardState by state.asStateFlow().collectAsState()
                DashboardScreen(
                    state = dashboardState,
                    onStartCaptureClick = ::requestScreenCapture,
                    onStopCaptureClick = ::stopScreenCapture,
                    onIntervalSelected = ::onIntervalSelected,
                    onOpenVisionLabClick = ::openVisionLab,
                    onOpenAccessibilitySettingsClick = ::openAccessibilitySettings
                )
            }
        }
    }

    private fun requestScreenCapture() {
        appendLog("Solicitando permissão de captura de tela")
        requestCapturePermission.launch(captureManager.buildPermissionIntent(mediaProjectionManager))
    }

    private fun onCapturePermissionGranted(data: Intent, resultCode: Int) {
        startForegroundService(
            Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_START)
        )
        captureManager.start(
            mediaProjectionManager,
            resultCode,
            data,
            interval = state.value.captureInterval
        )
        state.update { it.copy(captureStatus = ModuleStatus.RUNNING) }
        appendLog("Captura de tela iniciada")
    }

    private fun stopScreenCapture() {
        captureManager.stop()
        startService(
            Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
        )
        state.update { it.copy(captureStatus = ModuleStatus.DISABLED) }
        appendLog("Captura de tela parada pelo usuário")
    }

    private fun openVisionLab() {
        startActivity(Intent(this, VisionLabActivity::class.java))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun onIntervalSelected(interval: CaptureInterval) {
        captureManager.updateInterval(interval)
        state.update { it.copy(captureInterval = interval) }
        appendLog("Intervalo de análise ajustado para ${interval.label}")
    }

    private fun appendLog(message: String) {
        state.update { current ->
            val updatedLogs = (current.logs + message).takeLast(50)
            current.copy(logs = updatedLogs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.stop()
    }
}

@Composable
private fun DashboardScreen(
    state: DashboardState,
    onStartCaptureClick: () -> Unit,
    onStopCaptureClick: () -> Unit,
    onIntervalSelected: (CaptureInterval) -> Unit,
    onOpenVisionLabClick: () -> Unit,
    onOpenAccessibilitySettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.dashboard_subtitle),
                style = MaterialTheme.typography.bodyMedium
            )

            StatusCard(label = "AUTOMAÇÃO", value = statusLabel(state.automationStatus))
            StatusCard(label = "CAPTURA", value = statusLabel(state.captureStatus))
            StatusCard(label = "RECONHECIMENTO", value = "OpenCV (chega na Fase 3)")

            CaptureControlCard(
                state = state,
                onStartCaptureClick = onStartCaptureClick,
                onStopCaptureClick = onStopCaptureClick,
                onIntervalSelected = onIntervalSelected
            )

            Button(onClick = onOpenVisionLabClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.button_open_vision_lab))
            }

            Button(onClick = onOpenAccessibilitySettingsClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.button_open_accessibility_settings))
            }

            LogCard(logs = state.logs)
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CaptureControlCard(
    state: DashboardState,
    onStartCaptureClick: () -> Unit,
    onStopCaptureClick: () -> Unit,
    onIntervalSelected: (CaptureInterval) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.label_frames_captured, state.framesCaptured),
                style = MaterialTheme.typography.bodyMedium
            )

            val isRunning = state.captureStatus == ModuleStatus.RUNNING
            Button(
                onClick = if (isRunning) onStopCaptureClick else onStartCaptureClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (isRunning) R.string.button_stop_capture else R.string.button_start_capture
                    )
                )
            }

            Text(
                text = stringResource(R.string.label_capture_interval),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CaptureInterval.entries.forEach { interval ->
                    val selected = interval == state.captureInterval
                    Button(onClick = { onIntervalSelected(interval) }) {
                        Text(text = if (selected) "[${interval.label}]" else interval.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "LOGS", style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                logs.asReversed().forEach { log ->
                    Text(text = log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun statusLabel(status: ModuleStatus): String = when (status) {
    ModuleStatus.DISABLED -> "Desativado"
    ModuleStatus.READY -> "Pronto"
    ModuleStatus.RUNNING -> "Executando"
    ModuleStatus.PAUSED -> "Pausado"
    ModuleStatus.ERROR -> "Erro"
}

