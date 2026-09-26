package com.autovision.clicker.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.autovision.clicker.models.DetectedObject
import com.autovision.clicker.models.VisionAnalysisResult
import com.autovision.clicker.ui.theme.AutoVisionTheme
import com.autovision.clicker.vision.VisionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vision Lab: tela de testes dos engines de reconhecimento visual, independente
 * da automação em si. O usuário carrega uma imagem da galeria, o app roda
 * detecção de objetos (forma + cor) sobre ela e mostra os resultados com um
 * overlay de debug visual (retângulos, número do objeto, confidence, forma).
 */
class VisionLabActivity : ComponentActivity() {

    private data class LabState(
        val bitmap: Bitmap? = null,
        val result: VisionAnalysisResult? = null,
        val isProcessing: Boolean = false,
        val statusMessage: String = "Carregue uma imagem para testar o reconhecimento"
    )

    private val state = MutableStateFlow(LabState())

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        loadAndAnalyze(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoVisionTheme {
                val labState by state.asStateFlow().collectAsState()
                VisionLabScreen(
                    bitmap = labState.bitmap,
                    result = labState.result,
                    isProcessing = labState.isProcessing,
                    statusMessage = labState.statusMessage,
                    onPickImageClick = { pickImage.launch("image/*") }
                )
            }
        }
    }

    private fun loadAndAnalyze(uri: android.net.Uri) {
        state.value = state.value.copy(isProcessing = true, statusMessage = "Analisando imagem...")
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            if (bitmap == null) {
                state.value = state.value.copy(
                    isProcessing = false,
                    statusMessage = "Não foi possível abrir essa imagem"
                )
                return@launch
            }

            val result = withContext(Dispatchers.Default) {
                VisionEngine.analyze(bitmap)
            }

            state.value = LabState(
                bitmap = bitmap,
                result = result,
                isProcessing = false,
                statusMessage = "${result.objects.size} objeto(s) encontrado(s) em ${result.processingTimeMillis} ms"
            )
        }
    }
}

@Composable
private fun VisionLabScreen(
    bitmap: Bitmap?,
    result: VisionAnalysisResult?,
    isProcessing: Boolean,
    statusMessage: String,
    onPickImageClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Vision Lab", style = MaterialTheme.typography.headlineMedium)
            Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
            if (isProcessing) {
                Text(text = "Processando...", style = MaterialTheme.typography.labelLarge)
            }

            Button(onClick = onPickImageClick, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Carregar imagem de teste")
            }

            if (bitmap != null) {
                DebugImageView(bitmap = bitmap, objects = result?.objects.orEmpty())
            }

            result?.objects?.forEach { obj ->
                ObjectResultCard(obj)
            }
        }
    }
}

@Composable
private fun DebugImageView(bitmap: Bitmap, objects: List<DetectedObject>) {
    val imageBitmap = bitmap.asImageBitmap()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .background(Color.Black)
        ) {
            val widthRatio = size.width / bitmap.width.toFloat()
            val heightRatio = size.height / bitmap.height.toFloat()

            drawImage(
                image = imageBitmap,
                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
            )

            objects.forEach { obj ->
                val left = obj.x * widthRatio
                val top = obj.y * heightRatio
                val width = obj.width * widthRatio
                val height = obj.height * heightRatio

                drawRect(
                    color = Color(0xFF00E5D0),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    style = Stroke(width = 3f)
                )

                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "#${obj.id} ${obj.confidence.toInt()}%",
                        left,
                        (top - 8f).coerceAtLeast(12f),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 28f
                            isAntiAlias = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectResultCard(obj: DetectedObject) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "OBJETO #${obj.id} — ${obj.confidence.toInt()}%",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Forma: ${obj.shape} · Centro: (${obj.centerX}, ${obj.centerY}) · Área: ${obj.area.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

