package com.autovision.clicker.automation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.autovision.clicker.accessibility.AutoVisionAccessibilityService
import com.autovision.clicker.capture.ScreenCaptureManager
import com.autovision.clicker.models.ActionType
import com.autovision.clicker.models.AutomationAction
import com.autovision.clicker.models.AutomationProfile
import com.autovision.clicker.models.AutomationProgress
import com.autovision.clicker.models.AutomationRunState
import com.autovision.clicker.models.ConditionOperator
import com.autovision.clicker.models.MatchCondition
import com.autovision.clicker.models.SearchRegion
import com.autovision.clicker.vision.ColorRecognitionEngine
import com.autovision.clicker.vision.ImageRecognitionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.coroutines.resume

/**
 * Orquestra a execução de uma [AutomationProfile]: percorre as ações configuradas,
 * resolve as que dependem de visão computacional (IMAGE_MATCH, COLOR_MATCH,
 * CONDITIONAL_CLICK) contra o frame mais recente da tela, e delega os gestos
 * reais (toques, swipes) para o [AutoVisionAccessibilityService].
 *
 * O engine não sabe nada sobre UI — ele só expõe [progress] (um StateFlow) para a
 * tela observar, e [start]/[stop]/[pause]/[resume] para controlar a execução.
 */
class AutomationEngine(
    private val captureManager: ScreenCaptureManager,
    private val scope: CoroutineScope
) {

    private val _progress = MutableStateFlow(AutomationProgress())
    val progress = _progress.asStateFlow()

    private var runJob: Job? = null

    @Volatile
    private var isPaused: Boolean = false

    @Volatile
    private var stopRequested: Boolean = false

    /** Inicia a execução de [profile]. Não faz nada se já houver uma automação rodando. */
    fun start(profile: AutomationProfile) {
        if (runJob?.isActive == true) return

        if (!AutoVisionAccessibilityService.isRunning) {
            _progress.update {
                it.copy(
                    runState = AutomationRunState.WAITING_FOR_ACCESSIBILITY,
                    lastMessage = "Ative o AutoVision nas configurações de Acessibilidade para executar ações"
                )
            }
            return
        }

        stopRequested = false
        isPaused = false

        runJob = scope.launch(Dispatchers.Default) {
            runProfile(profile)
        }
    }

    /** Pausa a execução após a ação atual terminar. */
    fun pause() {
        if (runJob?.isActive == true) {
            isPaused = true
            _progress.update { it.copy(runState = AutomationRunState.PAUSED) }
        }
    }

    /** Retoma uma execução pausada. */
    fun resume() {
        if (isPaused) {
            isPaused = false
            _progress.update { it.copy(runState = AutomationRunState.RUNNING) }
        }
    }

    /** Interrompe a automação assim que possível (após a ação/gesto atual). */
    fun stop() {
        stopRequested = true
        isPaused = false
        runJob?.cancel()
        runJob = null
        _progress.update {
            it.copy(runState = AutomationRunState.IDLE, lastMessage = "Automação interrompida pelo usuário")
        }
    }

    fun isRunning(): Boolean = runJob?.isActive == true

    private suspend fun runProfile(profile: AutomationProfile) {
        val totalActions = countActions(profile.actions)
        _progress.update {
            it.copy(
                runState = AutomationRunState.RUNNING,
                currentLoop = 0,
                totalLoops = profile.loopCount,
                totalActions = totalActions,
                currentActionIndex = 0,
                lastMessage = "Iniciando \"${profile.name}\""
            )
        }

        var loop = 0
        while (!stopRequested && (profile.loopCount <= 0 || loop < profile.loopCount)) {
            loop++
            _progress.update { it.copy(currentLoop = loop) }

            var actionIndex = 0
            for (action in profile.actions) {
                if (stopRequested) break
                waitWhilePaused()
                actionIndex++
                _progress.update {
                    it.copy(
                        currentActionIndex = actionIndex,
                        currentActionLabel = action.label.ifBlank { action.type.name }
                    )
                }
                executeAction(action)
            }
        }

        if (!stopRequested) {
            _progress.update {
                it.copy(runState = AutomationRunState.FINISHED, lastMessage = "Automação concluída")
            }
        }
    }

    private fun countActions(actions: List<AutomationAction>): Int =
        actions.sumOf { 1 + countActions(it.children) }

    private suspend fun waitWhilePaused() {
        while (isPaused && !stopRequested) {
            delay(150L)
        }
    }

    private suspend fun executeAction(action: AutomationAction) {
        repeat(action.repeatCount.coerceAtLeast(1)) {
            if (stopRequested) return@repeat
            waitWhilePaused()

            when (action.type) {
                ActionType.CLICK -> dispatchClick(action.x, action.y)
                ActionType.DOUBLE_CLICK -> dispatchDoubleClick(action.x, action.y)
                ActionType.LONG_PRESS -> dispatchLongPress(action.x, action.y, action.durationMillis)
                ActionType.SWIPE -> dispatchSwipe(action.x, action.y, action.endX, action.endY, action.durationMillis)
                ActionType.WAIT -> delay(action.durationMillis)
                ActionType.IMAGE_MATCH -> executeImageMatch(action)
                ActionType.COLOR_MATCH -> executeColorMatch(action)
                ActionType.CONDITIONAL_CLICK -> executeConditionalClick(action)
                ActionType.SEQUENCE -> for (child in action.children) {
                    if (stopRequested) break
                    waitWhilePaused()
                    executeAction(child)
                }
            }

            if (action.delayAfterMillis > 0L) {
                delay(action.delayAfterMillis)
            }
        }
    }

    private suspend fun executeImageMatch(action: AutomationAction) {
        val path = action.referenceImagePath
        val screen = captureManager.getLatestFrame()
        if (path.isNullOrBlank() || screen == null) {
            log("IMAGE_MATCH sem imagem de referência ou sem frame de tela disponível")
            return
        }

        val templateBitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        if (templateBitmap == null) {
            log("Não foi possível carregar a imagem de referência: $path")
            return
        }

        val screenMat = ImageRecognitionEngine.bitmapToMat(screen)
        val templateMat = ImageRecognitionEngine.bitmapToMat(templateBitmap)

        val match = ImageRecognitionEngine.findImageOnScreen(
            screenMat,
            templateMat,
            action.minConfidence,
            action.searchRegion
        )

        screenMat.release()
        templateMat.release()

        if (match == null) {
            val regionInfo = action.searchRegion?.let { " na região configurada" } ?: ""
            log("Imagem não encontrada$regionInfo (confiança mínima ${action.minConfidence}%)")
            return
        }

        val centerX = match.x + match.width / 2
        val centerY = match.y + match.height / 2
        log("Imagem encontrada em ($centerX, $centerY) com ${match.confidence.toInt()}% de confiança")
        dispatchClick(centerX, centerY)
    }

    private suspend fun executeColorMatch(action: AutomationAction) {
        val screen = captureManager.getLatestFrame()
        if (screen == null) {
            log("COLOR_MATCH sem frame de tela disponível")
            return
        }

        val screenMat = ImageRecognitionEngine.bitmapToMat(screen)
        val point = findColorMatch(screenMat, action.targetColorRgb, action.colorTolerance, action.searchRegion)
        screenMat.release()

        if (point == null) {
            log("Nenhuma região com a cor configurada foi encontrada na tela")
            return
        }

        log("Cor encontrada em (${point.first}, ${point.second})")
        dispatchClick(point.first, point.second)
    }

    /**
     * CONDITIONAL_CLICK: avalia a árvore de [AutomationAction.condition] (E/OU/NÃO
     * combinando buscas de imagem e cor) contra o frame atual da tela. Só clica em
     * [AutomationAction.x]/[AutomationAction.y] quando a condição combinada for
     * satisfeita — do contrário, não faz nada nesta iteração.
     */
    private suspend fun executeConditionalClick(action: AutomationAction) {
        val condition = action.condition
        if (condition == null) {
            log("CONDITIONAL_CLICK sem condição configurada")
            return
        }

        val screen = captureManager.getLatestFrame()
        if (screen == null) {
            log("CONDITIONAL_CLICK sem frame de tela disponível")
            return
        }

        val screenMat = ImageRecognitionEngine.bitmapToMat(screen)
        val satisfied = withContext(Dispatchers.Default) { evaluateCondition(condition, screenMat) }
        screenMat.release()

        if (!satisfied) {
            log("Condição não satisfeita — automação não clicou")
            return
        }

        log("Condição satisfeita — clicando em (${action.x}, ${action.y})")
        dispatchClick(action.x, action.y)
    }

    /**
     * Avalia recursivamente uma [MatchCondition] contra [screenMat]. Cada folha
     * (Image/Color) roda uma busca visual isolada; [MatchCondition.Combined] e
     * [MatchCondition.Negated] combinam os resultados das subárvores.
     *
     * Nota: como cada folha faz sua própria busca na tela (a mesma screenMat,
     * já capturada uma vez), avaliar uma condição com muitas folhas tem custo
     * proporcional ao número de buscas — aceitável para o uso esperado (poucas
     * condições combinadas por ação), evitando complexidade extra.
     */
    private suspend fun evaluateCondition(condition: MatchCondition, screenMat: Mat): Boolean {
        return when (condition) {
            is MatchCondition.Image -> {
                val path = condition.referenceImagePath
                if (path.isNullOrBlank()) return false
                val templateBitmap = withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                } ?: return false
                val templateMat = ImageRecognitionEngine.bitmapToMat(templateBitmap)
                val match = ImageRecognitionEngine.findImageOnScreen(
                    screenMat,
                    templateMat,
                    condition.minConfidence,
                    condition.searchRegion
                )
                templateMat.release()
                match != null
            }
            is MatchCondition.Color -> {
                findColorMatch(
                    screenMat,
                    condition.targetColorRgb,
                    condition.colorTolerance,
                    condition.searchRegion
                ) != null
            }
            is MatchCondition.Combined -> {
                val leftResult = evaluateCondition(condition.left, screenMat)
                when (condition.operator) {
                    ConditionOperator.AND -> leftResult && evaluateCondition(condition.right, screenMat)
                    ConditionOperator.OR -> leftResult || evaluateCondition(condition.right, screenMat)
                }
            }
            is MatchCondition.Negated -> !evaluateCondition(condition.condition, screenMat)
        }
    }

    /**
     * Procura a cor [targetRgb] em [screenMat], opcionalmente restrita a [region],
     * e retorna o centro (no referencial da tela inteira) da maior região
     * encontrada, ou `null` se nada bater dentro da tolerância.
     */
    private fun findColorMatch(
        screenMat: Mat,
        targetRgb: Int,
        colorTolerance: Int,
        region: SearchRegion?
    ): Pair<Int, Int>? {
        val crop = ImageRecognitionEngine.cropToRegion(screenMat, region) ?: return null
        try {
            val targetScalar = Scalar(
                (targetRgb and 0xFF).toDouble(),
                ((targetRgb shr 8) and 0xFF).toDouble(),
                ((targetRgb shr 16) and 0xFF).toDouble()
            )

            val hsv = ColorRecognitionEngine.toHsv(crop.mat)
            val toleranceHsv = colorTolerance.toDouble()
            val targetHsvMat = Mat(1, 1, crop.mat.type(), targetScalar)
            val targetHsvConverted = Mat()
            Imgproc.cvtColor(targetHsvMat, targetHsvConverted, Imgproc.COLOR_BGR2HSV)
            val targetHsvValue = targetHsvConverted.get(0, 0)
            targetHsvMat.release()
            targetHsvConverted.release()

            val lower = Scalar(
                (targetHsvValue[0] - toleranceHsv).coerceAtLeast(0.0),
                (targetHsvValue[1] - toleranceHsv * 2).coerceAtLeast(0.0),
                (targetHsvValue[2] - toleranceHsv * 2).coerceAtLeast(0.0)
            )
            val upper = Scalar(
                (targetHsvValue[0] + toleranceHsv).coerceAtMost(179.0),
                (targetHsvValue[1] + toleranceHsv * 2).coerceAtMost(255.0),
                (targetHsvValue[2] + toleranceHsv * 2).coerceAtMost(255.0)
            )

            val mask = Mat()
            Core.inRange(hsv, lower, upper, mask)
            hsv.release()

            val point = findLargestMaskRegionCenter(mask)
            mask.release()

            return point?.let { (localX, localY) -> (localX + crop.offsetX) to (localY + crop.offsetY) }
        } finally {
            crop.release()
        }
    }

    /** Encontra o centro da maior região conectada de pixels brancos numa máscara binária. */
    private fun findLargestMaskRegionCenter(mask: Mat): Pair<Int, Int>? {
        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            mask,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
        val moments = Imgproc.moments(largest)
        contours.forEach { it.release() }

        if (moments.m00 == 0.0) return null
        val centerX = (moments.m10 / moments.m00).toInt()
        val centerY = (moments.m01 / moments.m00).toInt()
        return centerX to centerY
    }

    private suspend fun dispatchClick(x: Int, y: Int) {
        val service = AutoVisionAccessibilityService.instance ?: run {
            log("Serviço de acessibilidade não está ativo")
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            service.click(x, y) { continuation.resume(Unit) }
        }
    }

    private suspend fun dispatchDoubleClick(x: Int, y: Int) {
        val service = AutoVisionAccessibilityService.instance ?: run {
            log("Serviço de acessibilidade não está ativo")
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            service.doubleClick(x, y) { continuation.resume(Unit) }
        }
    }

    private suspend fun dispatchLongPress(x: Int, y: Int, durationMillis: Long) {
        val service = AutoVisionAccessibilityService.instance ?: run {
            log("Serviço de acessibilidade não está ativo")
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            service.longPress(x, y, durationMillis) { continuation.resume(Unit) }
        }
    }

    private suspend fun dispatchSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long) {
        val service = AutoVisionAccessibilityService.instance ?: run {
            log("Serviço de acessibilidade não está ativo")
            return
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            service.swipe(startX, startY, endX, endY, durationMillis) { continuation.resume(Unit) }
        }
    }

    private fun log(message: String) {
        _progress.update { it.copy(lastMessage = message) }
    }
}
