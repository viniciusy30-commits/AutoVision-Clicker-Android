package com.autovision.clicker.vision

import com.autovision.clicker.models.DetectedObject
import org.opencv.core.Mat
import org.opencv.core.Rect

/**
 * Detecta objetos automaticamente numa imagem, por contraste/bordas/contornos —
 * sem assumir posições fixas. Opcionalmente, o chamador pode restringir a busca a
 * uma Region of Interest (ROI); os objetos dentro dela continuam podendo estar em
 * qualquer posição relativa.
 */
object ObjectDetector {

    /**
     * Região de interesse opcional, em coordenadas absolutas da imagem original.
     * Serve só para limitar a área de análise (ex.: "só a metade de cima da tela"),
     * nunca para fixar a posição de um objeto específico.
     */
    data class RegionOfInterest(val x: Int, val y: Int, val width: Int, val height: Int)

    private var nextId = 0

    /**
     * Detecta todos os objetos visualmente distintos dentro de [source] (ou dentro
     * de [roi], se informado), usando bordas (Canny) + contornos.
     */
    fun detectObjects(
        source: Mat,
        roi: RegionOfInterest? = null,
        minArea: Double = 60.0
    ): List<DetectedObject> {
        nextId = 0

        val region = roi?.let {
            Rect(it.x, it.y, it.width, it.height)
        }
        val workingMat = if (region != null) Mat(source, region) else source

        val edges = ImageRecognitionEngine.toEdges(workingMat)
        val contours = ShapeRecognitionEngine.findContours(edges, minArea)
        edges.release()

        val offsetX = roi?.x ?: 0
        val offsetY = roi?.y ?: 0

        return contours.map { info ->
            val shape = ShapeRecognitionEngine.classifyShape(info)

            val objectRect = Rect(info.boundingX, info.boundingY, info.boundingWidth, info.boundingHeight)
            val objectMat = Mat(workingMat, objectRect)
            val dominantColor = ColorRecognitionEngine.findDominantColor(objectMat)
            objectMat.release()

            val absoluteX = info.boundingX + offsetX
            val absoluteY = info.boundingY + offsetY

            // Confidence heurístico: contornos maiores e mais "limpos" (perímetro
            // proporcional à área, sem ruído excessivo) recebem confidence maior.
            val expectedPerimeter = 4 * kotlin.math.sqrt(info.area)
            val perimeterRatio = if (info.perimeter > 0) {
                (expectedPerimeter / info.perimeter).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val confidence = (perimeterRatio * 100.0).coerceIn(0.0, 100.0)

            DetectedObject(
                id = nextId++,
                x = absoluteX,
                y = absoluteY,
                width = info.boundingWidth,
                height = info.boundingHeight,
                centerX = absoluteX + info.boundingWidth / 2,
                centerY = absoluteY + info.boundingHeight / 2,
                area = info.area,
                shape = shape,
                confidence = confidence,
                dominantColorRgb = dominantColor.toRgbInt()
            )
        }
    }

    private fun org.opencv.core.Scalar.toRgbInt(): Int {
        val r = this.`val`[2].toInt().coerceIn(0, 255)
        val g = this.`val`[1].toInt().coerceIn(0, 255)
        val b = this.`val`[0].toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
