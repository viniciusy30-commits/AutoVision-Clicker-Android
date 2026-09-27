package com.autovision.clicker.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.autovision.clicker.R
import com.autovision.clicker.automation.AutomationEngine
import com.autovision.clicker.capture.ScreenCaptureManager
import com.autovision.clicker.capture.ScreenCaptureService
import com.autovision.clicker.models.AutomationProfile
import com.autovision.clicker.models.CaptureInterval
import com.autovision.clicker.models.DashboardState
import com.autovision.clicker.models.ModuleStatus
import com.autovision.clicker.models.VisionAnalysisResult
import com.autovision.clicker.ui.theme.AutoVisionTheme
import com.autovision.clicker.vision.VisionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity única do AutoVision Clicker (Fase 1 — reorganização da interface).
 *
 * Hospeda a navegação por seções (Início, Automações, Reconhecimento, Vision Lab,
 * Ajustes) através de uma bottom navigation bar, mantendo toda a lógica de captura
 * de tela (MediaProjection), automação e acessibilidade que já existia — nada foi
 * removido, apenas reorganizado visualmente.
 */
class MainActivity : ComponentActivity() {

    private val state = MutableStateFlow(DashboardState())
    private val profiles = MutableStateFlow<List<AutomationProfile>>(emptyList())

    private data class LabState(
        val bitmap: Bitmap? = null,
        val result: VisionAnalysisResult? = null,
        val isProcessing: Boolean = false,
        val statusMessage: String = "Carregue uma imagem para testar o reconhecimento"
    )

    private val labState = MutableStateFlow(LabState())

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var automationEngine: AutomationEngine

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

    private val pickLabImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) loadAndAnalyzeLabImage(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureManager = ScreenCaptureManager(applicationContext, lifecycleScope)
        captureManager.onFrame = { _ ->
            state.update { it.copy(framesCaptured = it.framesCaptured + 1) }
        }
        captureManager.onCaptureStopped = {
            state.update { it.copy(captureStatus = ModuleStatus.DISABLED) }
            appendLog("Captura interrompida")
        }
        automationEngine = AutomationEngine(captureManager, lifecycleScope)

        setContent {
            AutoVisionTheme {
                var currentSection by remember { mutableStateOf(AppSection.HOME) }

                val dashboardState by state.asStateFlow().collectAsState()
                val profileList by profiles.asStateFlow().collectAsState()
                val runProgress by automationEngine.progress.collectAsState()
                val lab by labState.asStateFlow().collectAsState()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            AppSection.entries.forEach { section ->
                                NavigationBarItem(
                                    selected = currentSection == section,
                                    onClick = { currentSection = section },
                                    icon = { androidx.compose.material3.Icon(section.icon, contentDescription = section.label) },
                                    label = { Text(section.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentSection) {
                            AppSection.HOME -> HomeScreen(
                                state = dashboardState,
                                runningProfileName = profileList.firstOrNull()?.name,
                                onStartCaptureClick = ::requestScreenCapture,
                                onStopCaptureClick = ::stopScreenCapture,
                                onGoToAutomations = { currentSection = AppSection.AUTOMATIONS },
                                onGoToSettings = { currentSection = AppSection.SETTINGS },
                                onOpenAccessibilitySettingsClick = ::openAccessibilitySettings
                            )
                            AppSection.AUTOMATIONS -> AutomationsScreen(
                                profiles = profileList,
                                progress = runProgress,
                                onCreateProfile = { profile -> profiles.update { it + profile } },
                                onDeleteProfile = { profile -> profiles.update { list -> list.filterNot { it.id == profile.id } } },
                                onStart = { profile -> automationEngine.start(profile) },
                                onPause = { automationEngine.pause() },
                                onResume = { automationEngine.resume() },
                                onStop = { automationEngine.stop() }
                            )
                            AppSection.RECOGNITION -> RecognitionScreen()
                            AppSection.VISION_LAB -> VisionLabScreen(
                                bitmap = lab.bitmap,
                                result = lab.result,
                                isProcessing = lab.isProcessing,
                                statusMessage = lab.statusMessage,
                                onPickImageClick = { pickLabImage.launch("image/*") }
                            )
                            AppSection.SETTINGS -> SettingsScreen(
                                captureInterval = dashboardState.captureInterval,
                                onIntervalSelected = ::onIntervalSelected,
                                onOpenAccessibilitySettingsClick = ::openAccessibilitySettings
                            )
                        }
                    }
                }
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

    private fun loadAndAnalyzeLabImage(uri: Uri) {
        labState.value = labState.value.copy(isProcessing = true, statusMessage = "Analisando imagem...")
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
            }
            if (bitmap == null) {
                labState.value = labState.value.copy(
                    isProcessing = false,
                    statusMessage = "Não foi possível abrir essa imagem"
                )
                return@launch
            }
            val result = withContext(Dispatchers.Default) { VisionEngine.analyze(bitmap) }
            labState.value = LabState(
                bitmap = bitmap,
                result = result,
                isProcessing = false,
                statusMessage = "${result.objects.size} objeto(s) encontrado(s) em ${result.processingTimeMillis} ms"
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.stop()
    }
}

/**
 * Tela "Início" (Fase 1): enxuta, mostrando apenas o essencial — status do
 * serviço e da captura, controles de iniciar/parar, e atalhos para as outras
 * seções. Configurações e detalhes avançados ficam nas telas próprias.
 */
@Composable
private fun HomeScreen(
    state: DashboardState,
    runningProfileName: String?,
    onStartCaptureClick: () -> Unit,
    onStopCaptureClick: () -> Unit,
    onGoToAutomations: () -> Unit,
    onGoToSettings: () -> Unit,
    onOpenAccessibilitySettingsClick: () -> Unit
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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "STATUS", style = MaterialTheme.typography.labelLarge)
                StatusRow(label = "Captura de tela", value = statusLabel(state.captureStatus))
                StatusRow(
                    label = "Automação atual",
                    value = runningProfileName ?: "Nenhuma selecionada"
                )
                Text(
                    text = "Frames capturados: ${state.framesCaptured}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        val isCapturing = state.captureStatus == ModuleStatus.RUNNING
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = if (isCapturing) onStopCaptureClick else onStartCaptureClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (isCapturing) R.string.button_stop_capture else R.string.button_start_capture
                    )
                )
            }
        }

        Button(onClick = onGoToAutomations, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Criar ou iniciar automação")
        }

        Button(onClick = onOpenAccessibilitySettingsClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.button_open_accessibility_settings))
        }

        Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Ajustes do app")
        }

        if (state.logs.isNotEmpty()) {
            LogCard(logs = state.logs)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "LOGS RECENTES", style = MaterialTheme.typography.labelLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                logs.asReversed().take(10).forEach { log ->
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
