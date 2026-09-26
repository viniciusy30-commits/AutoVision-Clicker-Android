package com.autovision.clicker.vision

import android.graphics.Bitmap
import com.autovision.clicker.models.ComparisonMode
import com.autovision.clicker.models.ComparisonWeights
import com.autovision.clicker.models.DetectedObject
import com.autovision.clicker.models.MatchResult
import com.autovision.clicker.models.VisionAnalysisResult
import com.autovision.clicker.models.VisualPair
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * Ponto de entrada único para a visão computacional do app. Combina
 * [ImageRecognitionEngine], [ColorRecognitionEngine] e [ShapeRecognitionEngine]
 * por trás de uma API simples, e implementa o algoritmo de matching de pares
 * descrito no planejamento (findVisualPairs).
 */
object VisionEngine {

    /** Roda a detecção de objetos numa imagem única (usado pelo Vision Lab). */
    fun analyze(bitmap: Bitmap, roi: ObjectDetector.RegionOfInterest? = null): VisionAnalysisResult {
        val startTime = System.currentTimeMillis()
        val mat = ImageRecognitionEngine.bitmapToMat(bitmap)
        val objects = ObjectDetector.detectObjects(mat, roi)
        mat.release()
        val elapsed = System.currentTimeMillis() - startTime
        return VisionAnalysisResult(objects = objects, processingTimeMillis = elapsed)
    }

    /**
     * Calcula o score de comparação entre dois objetos, de acordo com [mode]:
     * - COLOR: cor pesa mais.
     * - SHAPE: cor quase não pesa; forma e características geométricas dominam.
     * - HYBRID: usa [weights] para combinar os três componentes.
     *
     * visualScore = shapeScore * shapeWeight + featureScore * featureWeight + colorScore * colorWeight
     */
    fun compareObjects(
        topMat: Mat,
        topContour: ShapeRecognitionEngine.ContourInfo,
        topObject: DetectedObject,
        bottomMat: Mat,
        bottomContour: ShapeRecognitionEngine.ContourInfo,
        bottomObject: DetectedObject,
        mode: ComparisonMode,
        weights: ComparisonWeights = ComparisonWeights()
    ): MatchResult {
        val shapeScore = if (topObject.shape == bottomObject.shape) 100.0 else 20.0

        val huA = ShapeRecognitionEngine.huMoments(topContour.contour)
        val huB = ShapeRecognitionEngine.huMoments(bottomContour.contour)
        val featureScore = ShapeRecognitionEngine.huMomentsSimilarity(huA, huB)

        val colorA = ColorRecognitionEngine.findDominantColor(topMat)
        val colorB = ColorRecognitionEngine.findDominantColor(bottomMat)
        val colorScore = ColorRecognitionEngine.colorSimilarity(colorA, colorB)

        val overallScore = when (mode) {
            ComparisonMode.COLOR ->
                shapeScore * 0.20 + featureScore * 0.20 + colorScore * 0.60
            ComparisonMode.SHAPE ->
                shapeScore * 0.55 + featureScore * 0.40 + colorScore * 0.05
            ComparisonMode.HYBRID ->
                shapeScore * weights.shapeWeight +
                    featureScore * weights.featureWeight +
                    colorScore * weights.colorWeight
        }

        return MatchResult(
            sourceId = topObject.id,
            targetId = bottomObject.id,
            shapeScore = shapeScore,
            featureScore = featureScore,
            colorScore = colorScore,
            overallScore = overallScore.coerceIn(0.0, 100.0)
        )
    }

    /**
     * Encontra correspondências entre dois grupos de objetos (ex.: linha de cima e
     * linha de baixo de um jogo de pares) mesmo quando as posições dentro de cada
     * grupo mudam a cada captura.
     *
     * Algoritmo (conforme o planejamento):
     * 1. Caller já capturou a tela e chamou [ObjectDetector.detectObjects] para os dois grupos.
     * 2. Aqui: para cada objeto do topo, compara com todos os objetos de baixo.
     * 3. Monta a matriz de similaridade completa.
     * 4. Resolve a melhor correspondência global de forma gulosa: pega o par de
     *    maior score disponível, remove ambos os objetos da disputa, repete.
     *    (Guloso é suficiente aqui: o objetivo é achar bons pares rápido, não a
     *    solução ótima matematicamente perfeita — e a diferença raramente importa
     *    quando os objetos são visualmente distintos entre si.)
     * 5. Retorna os pares com score acima de [minScore].
     */
    fun findVisualPairs(
        topMat: Mat,
        topObjects: List<DetectedObject>,
        topContours: List<ShapeRecognitionEngine.ContourInfo>,
        bottomMat: Mat,
        bottomObjects: List<DetectedObject>,
        bottomContours: List<ShapeRecognitionEngine.ContourInfo>,
        mode: ComparisonMode = ComparisonMode.HYBRID,
        weights: ComparisonWeights = ComparisonWeights(),
        minScore: Double = 60.0
    ): List<VisualPair> {
        if (topObjects.size != topContours.size || bottomObjects.size != bottomContours.size) {
            return emptyList()
        }

        // 1. Matriz de similaridade completa (top x bottom).
        val similarityMatrix = mutableListOf<Triple<Int, Int, Double>>()
        for (i in topObjects.indices) {
            for (j in bottomObjects.indices) {
                val topRect = Rect(topObjects[i].x, topObjects[i].y, topObjects[i].width, topObjects[i].height)
                val bottomRect = Rect(
                    bottomObjects[j].x,
                    bottomObjects[j].y,
                    bottomObjects[j].width,
                    bottomObjects[j].height
                )
                val topObjMat = Mat(topMat, topRect)
                val bottomObjMat = Mat(bottomMat, bottomRect)

                val result = compareObjects(
                    topObjMat, topContours[i], topObjects[i],
                    bottomObjMat, bottomContours[j], bottomObjects[j],
                    mode, weights
                )

                topObjMat.release()
                bottomObjMat.release()

                similarityMatrix.add(Triple(i, j, result.overallScore))
            }
        }

        // 2. Melhor correspondência global, de forma gulosa (maior score primeiro).
        val sortedByScore = similarityMatrix.sortedByDescending { it.third }
        val usedTop = mutableSetOf<Int>()
        val usedBottom = mutableSetOf<Int>()
        val pairs = mutableListOf<VisualPair>()

        for ((topIndex, bottomIndex, score) in sortedByScore) {
            if (topIndex in usedTop || bottomIndex in usedBottom) continue
            if (score < minScore) continue

            usedTop.add(topIndex)
            usedBottom.add(bottomIndex)
            pairs.add(
                VisualPair(
                    topObject = topObjects[topIndex],
                    bottomObject = bottomObjects[bottomIndex],
                    matchScore = score
                )
            )
        }

        return pairs
    }
}
