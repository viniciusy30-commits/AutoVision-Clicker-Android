package com.autovision.clicker.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.Point
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Reconhecimento de imagens: encontra uma imagem-alvo (template) dentro de uma
 * imagem maior (a tela), sem depender de coordenadas fixas. Combina duas técnicas
 * que se complementam, conforme pedido no planejamento ("não depender de uma
 * única técnica"):
 *
 * - Template Matching: rápido, ótimo quando a escala/rotação não mudou muito.
 * - ORB (feature matching): mais robusto a pequenas mudanças de escala/ângulo,
 *   mais lento.
 *
 * Cada resultado retorna um confidence de 0 a 100.
 */
object ImageRecognitionEngine {

    data class MatchLocation(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val confidence: Double
    )

    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap.copy(Bitmap.Config.ARGB_8888, false), mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        return mat
    }

    /**
     * Normaliza uma imagem para escala de cinza, reduzindo a influência de cor —
     * usado no modo de comparação SHAPE e como base para bordas/contornos.
     */
    fun normalizeImage(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        return gray
    }

    /** Detector de bordas (Canny), base para o findContours do ShapeRecognitionEngine. */
    fun toEdges(mat: Mat, threshold1: Double = 50.0, threshold2: Double = 150.0): Mat {
        val gray = if (mat.channels() > 1) normalizeImage(mat) else mat
        val edges = Mat()
        Imgproc.Canny(gray, edges, threshold1, threshold2)
        if (gray !== mat) gray.release()
        return edges
    }

    /**
     * Procura [template] dentro de [screen] usando Template Matching normalizado
     * (TM_CCOEFF_NORMED). Retorna a melhor posição encontrada, com confidence 0-100.
     * Tolera pequenas diferenças de iluminação (por isso a normalização), mas não
     * tolera bem mudança de escala — para isso, complementar com [findByFeatures].
     */
    fun findByTemplate(screen: Mat, template: Mat): MatchLocation? {
        if (template.cols() > screen.cols() || template.rows() > screen.rows()) return null

        val screenGray = normalizeImage(screen)
        val templateGray = normalizeImage(template)

        val resultCols = screen.cols() - template.cols() + 1
        val resultRows = screen.rows() - template.rows() + 1
        if (resultCols <= 0 || resultRows <= 0) {
            screenGray.release()
            templateGray.release()
            return null
        }

        val result = Mat(resultRows, resultCols, CvType.CV_32FC1)
        Imgproc.matchTemplate(screenGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)

        val minMaxLocResult = Core.minMaxLoc(result)
        val confidence = (minMaxLocResult.maxVal.coerceIn(-1.0, 1.0)) * 100.0
        val topLeft = minMaxLocResult.maxLoc

        screenGray.release()
        templateGray.release()
        result.release()

        return MatchLocation(
            x = topLeft.x.toInt(),
            y = topLeft.y.toInt(),
            width = template.cols(),
            height = template.rows(),
            confidence = confidence.coerceIn(0.0, 100.0)
        )
    }

    /**
     * Procura [template] dentro de [screen] usando ORB (Oriented FAST and Rotated
     * BRIEF) + feature matching. Mais tolerante a mudança de escala e pequena
     * rotação do que o template matching puro. O confidence é derivado da
     * proporção de "good matches" (distância abaixo do limiar) sobre o total de
     * descritores do template.
     */
    fun findByFeatures(screen: Mat, template: Mat, maxFeatures: Int = 500): MatchLocation? {
        val orb = ORB.create(maxFeatures)

        val screenGray = normalizeImage(screen)
        val templateGray = normalizeImage(template)

        val screenKeypoints = MatOfKeyPoint()
        val templateKeypoints = MatOfKeyPoint()
        val screenDescriptors = Mat()
        val templateDescriptors = Mat()

        orb.detectAndCompute(screenGray, Mat(), screenKeypoints, screenDescriptors)
        orb.detectAndCompute(templateGray, Mat(), templateKeypoints, templateDescriptors)

        if (screenDescriptors.empty() || templateDescriptors.empty()) {
            listOf(screenGray, templateGray, screenDescriptors, templateDescriptors).forEach { it.release() }
            return null
        }

        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val matches = MatOfDMatch()
        matcher.match(templateDescriptors, screenDescriptors, matches)

        val matchList = matches.toArray().toList()
        if (matchList.isEmpty()) {
            listOf(screenGray, templateGray, screenDescriptors, templateDescriptors).forEach { it.release() }
            return null
        }

        val maxDistance = matchList.maxOf { it.distance }
        val goodThreshold = maxDistance * 0.5
        val goodMatches = matchList.filter { it.distance <= goodThreshold }

        if (goodMatches.isEmpty()) {
            listOf(screenGray, templateGray, screenDescriptors, templateDescriptors).forEach { it.release() }
            return null
        }

        val screenKeypointArray = screenKeypoints.toArray()
        val points = goodMatches.mapNotNull { match: DMatch ->
            screenKeypointArray.getOrNull(match.trainIdx)?.pt
        }

        val confidence = (goodMatches.size.toDouble() / templateDescriptors.rows().toDouble())
            .coerceIn(0.0, 1.0) * 100.0

        listOf(screenGray, templateGray, screenDescriptors, templateDescriptors).forEach { it.release() }

        if (points.isEmpty()) return null

        val boundingBox = boundingBoxOf(points)
        return MatchLocation(
            x = boundingBox.x,
            y = boundingBox.y,
            width = boundingBox.width.coerceAtLeast(template.cols()),
            height = boundingBox.height.coerceAtLeast(template.rows()),
            confidence = confidence
        )
    }

    private data class SimpleRect(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun boundingBoxOf(points: List<Point>): SimpleRect {
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        return SimpleRect(
            x = minX.toInt(),
            y = minY.toInt(),
            width = (maxX - minX).toInt(),
            height = (maxY - minY).toInt()
        )
    }

    /**
     * Combina as duas técnicas: tenta template matching primeiro (mais rápido);
     * se o confidence ficar baixo, tenta feature matching como reforço. Retorna o
     * melhor dos dois resultados. Isso é o que dá o comportamento "não depender de
     * uma única técnica" pedido no planejamento.
     */
    fun findImageOnScreen(screen: Mat, template: Mat, minConfidence: Double = 70.0): MatchLocation? {
        val templateResult = findByTemplate(screen, template)
        if (templateResult != null && templateResult.confidence >= minConfidence) {
            return templateResult
        }

        val featureResult = findByFeatures(screen, template)

        return listOfNotNull(templateResult, featureResult)
            .maxByOrNull { it.confidence }
            ?.takeIf { it.confidence >= minConfidence }
    }

    /**
     * Similaridade simples entre duas imagens de mesmo conteúdo esperado (usado no
     * Vision Lab para "comparar duas imagens" diretamente, sem procurar uma dentro
     * da outra). Redimensiona a segunda para o tamanho da primeira antes de comparar.
     */
    fun compareImages(imageA: Mat, imageB: Mat): Double {
        val resizedB = Mat()
        Imgproc.resize(imageB, resizedB, imageA.size())

        val grayA = normalizeImage(imageA)
        val grayB = normalizeImage(resizedB)

        val result = Mat()
        Imgproc.matchTemplate(grayA, grayB, result, Imgproc.TM_CCOEFF_NORMED)
        val score = Core.minMaxLoc(result).maxVal

        listOf(resizedB, grayA, grayB, result).forEach { it.release() }

        return (score.coerceIn(-1.0, 1.0) * 100.0).coerceIn(0.0, 100.0)
    }
}
