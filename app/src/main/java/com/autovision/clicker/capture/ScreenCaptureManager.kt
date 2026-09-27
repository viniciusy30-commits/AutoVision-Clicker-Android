package com.autovision.clicker.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.autovision.clicker.models.CaptureInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gerencia a captura contínua de tela via MediaProjection, sem bloquear a UI.
 *
 * Uso:
 * 1. Pedir a permissão de captura com [buildPermissionIntent] (Activity chama
 *    startActivityForResult / Activity Result API com o Intent retornado).
 * 2. Ao receber o resultCode/data do sistema, chamar [start].
 * 3. Cada frame novo chega em [onFrame], já como Bitmap, na thread de callback
 *    (chamador decide para qual dispatcher mandar o processamento pesado).
 * 4. Chamar [stop] para liberar tudo (VirtualDisplay, ImageReader, MediaProjection).
 *
 * A captura em si (leitura da tela) é orientada a eventos do ImageReader — não
 * fica rodando um loop ocupado. O [CaptureInterval] aqui controla apenas de quanto
 * em quanto tempo um frame recém-capturado é de fato processado/entregue, para não
 * sobrecarregar as próximas fases (Vision Lab, engines de reconhecimento) com mais
 * frames do que elas conseguem analisar.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    /** Chamado a cada frame aceito (respeitando o intervalo configurado). */
    var onFrame: ((Bitmap) -> Unit)? = null

    /** Chamado se a captura for interrompida de forma inesperada (ex.: permissão revogada). */
    var onCaptureStopped: (() -> Unit)? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var throttleJob: Job? = null

    @Volatile
    private var captureIntervalMillis: Long = CaptureInterval.MS_250.millis

    @Volatile
    private var latestBitmap: Bitmap? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    /** Deve ser chamado a partir da Activity: `mediaProjectionManager.createScreenCaptureIntent()`. */
    fun buildPermissionIntent(manager: MediaProjectionManager): Intent =
        manager.createScreenCaptureIntent()

    /**
     * Inicia a captura depois que o usuário aceitou o diálogo de permissão do sistema.
     * [resultCode] e [data] vêm do callback da Activity Result API.
     */
    fun start(
        projectionManager: MediaProjectionManager,
        resultCode: Int,
        data: Intent,
        interval: CaptureInterval = CaptureInterval.MS_250
    ) {
        captureIntervalMillis = interval.millis

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projection = projectionManager.getMediaProjection(resultCode, data)
            ?: return
        mediaProjection = projection

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
                onCaptureStopped?.invoke()
            }
        }, null)

        val reader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            "AutoVisionScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )

        reader.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                latestBitmap = if (rowPadding == 0) {
                    bitmap
                } else {
                    Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                }
            } finally {
                image.close()
            }
        }, null)

        startThrottleLoop()
    }

    /**
     * Entrega o frame mais recente disponível a cada [captureIntervalMillis], em vez de
     * disparar um callback por frame bruto — isso é o que dá o controle de "intervalo de
     * análise" pedido no planejamento (50/100/250/500/1000 ms).
     */
    private fun startThrottleLoop() {
        throttleJob?.cancel()
        throttleJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val bitmap = latestBitmap
                if (bitmap != null) {
                    onFrame?.invoke(bitmap)
                }
                delay(captureIntervalMillis)
            }
        }
    }

    fun updateInterval(interval: CaptureInterval) {
        captureIntervalMillis = interval.millis
    }

    fun stop() {
        throttleJob?.cancel()
        throttleJob = null
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        latestBitmap = null
    }

    fun isCapturing(): Boolean = mediaProjection != null
}
