package com.autovision.clicker.vision

import com.autovision.clicker.models.DetectedShape
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * Detecção e classificação de formas geométricas a partir de contornos.
 *
 * Fluxo típico:
 * 1. [findContours] sobre uma imagem binarizada (bordas ou máscara de cor).
 * 2. Para cada contorno, [classifyShape] usa polygon approximation, número de
 *    vértices, circularidade e aspect ratio para decidir a forma.
 * 3. [huMoments] fornece uma assinatura da forma que é invariante a rotação e
 *    escala — usada como parte do featureScore na comparação híbrida.
 */
object ShapeRecognitionEngine {

    data class ContourInfo(
        val contour: MatOfPoint,
        val boundingX: Int,
        val boundingY: Int,
        val boundingWidth: Int,
        val boundingHeight: Int,
        val area: Double,
        val perimeter: Double
    )

    /**
     * Encontra os contornos externos de uma imagem já convertida para bordas/máscara
     * binária (ver [com.autovision.clicker.vision.ImageRecognitionEngine.toEdges]).
     * Contornos menores que [minArea] são descartados como ruído.
     */
    fun findContours(binaryImage: Mat, minArea: Double = 60.0): List<ContourInfo> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            binaryImage,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        hierarchy.release()

        return contours.mapNotNull { contour ->
            val area = Imgproc.contourArea(contour)
            if (area < minArea) return@mapNotNull null

            val rect = Imgproc.boundingRect(contour)
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)

            ContourInfo(
                contour = contour,
                boundingX = rect.x,
                boundingY = rect.y,
                boundingWidth = rect.width,
                boundingHeight = rect.height,
                area = area,
                perimeter = perimeter
            )
        }
    }

    /**
     * Classifica a forma de um contorno usando:
     * - polygon approximation (número de vértices aproximados)
     * - circularidade = 4π·área / perímetro² (1.0 = círculo perfeito)
     * - aspect ratio (largura/altura) para distinguir quadrado de retângulo
     */
    fun classifyShape(info: ContourInfo): DetectedShape {
        val contour2f = MatOfPoint2f(*info.contour.toArray())
        val approx = MatOfPoint2f()
        val epsilon = 0.03 * info.perimeter
        Imgproc.approxPolyDP(contour2f, approx, epsilon, true)
        val vertices = approx.toArray().size
        contour2f.release()
        approx.release()

        val circularity = if (info.perimeter > 0) {
            (4 * PI * info.area) / info.perimeter.pow(2)
        } else {
            0.0
        }

        val aspectRatio = if (info.boundingHeight > 0) {
            info.boundingWidth.toDouble() / info.boundingHeight.toDouble()
        } else {
            0.0
        }

        return when {
            circularity > 0.80 -> DetectedShape.CIRCLE
            vertices == 3 -> DetectedShape.TRIANGLE
            vertices == 4 && abs(aspectRatio - 1.0) < 0.15 -> DetectedShape.SQUARE
            vertices == 4 -> DetectedShape.RECTANGLE
            else -> DetectedShape.IRREGULAR
        }
    }

    /**
     * Hu Moments: 7 valores invariantes a translação, escala e rotação, usados
     * como "assinatura" de forma para comparar dois objetos mesmo que estejam em
     * posições/tamanhos diferentes na tela (base do findVisualPairs).
     */
    fun huMoments(contour: MatOfPoint): DoubleArray {
        val moments = Imgproc.moments(contour)
        val huMoments = org.opencv.core.Mat()
        Imgproc.HuMoments(moments, huMoments)
        val result = DoubleArray(7)
        huMoments.get(0, 0, result)
        huMoments.release()

        // Log-scale nos Hu Moments (prática padrão): eles variam em escalas muito
        // diferentes entre si, e o log comprime isso para comparação mais estável.
        return DoubleArray(7) { i ->
            val value = result[i]
            if (value == 0.0) 0.0 else -Math.signum(value) * Math.log10(abs(value))
        }
    }

    /**
     * Similaridade entre duas assinaturas de Hu Moments, de 0 a 100.
     * Distância L1 simples, normalizada empiricamente — funciona bem o suficiente
     * para diferenciar formas claramente distintas sem exigir calibração fina.
     */
    fun huMomentsSimilarity(a: DoubleArray, b: DoubleArray): Double {
        var distance = 0.0
        for (i in a.indices) {
            distance += abs(a[i] - b[i])
        }
        val normalized = (distance / a.size).coerceIn(0.0, 5.0)
        return 100.0 * (1.0 - normalized / 5.0)
    }
}
