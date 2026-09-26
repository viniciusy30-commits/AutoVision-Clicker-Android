package com.autovision.clicker.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Reconhecimento e comparação de cores. Suporta RGB e HSV, extração de cor
 * dominante, busca por faixa de cor (com tolerância) e cálculo de similaridade
 * entre cores — usado tanto isoladamente (procurar objetos de uma cor) quanto
 * como um dos três componentes do score híbrido de comparação visual.
 */
object ColorRecognitionEngine {

    /**
     * Calcula a cor dominante de uma região da imagem, como média dos canais RGB.
     * Simples e rápido — suficiente para a maioria dos ícones/botões de UI, que em
     * geral têm uma cor predominante clara.
     */
    fun findDominantColor(mat: Mat): Scalar {
        val mean = Core.mean(mat)
        return mean
    }

    /**
     * Converte uma região BGR (padrão do OpenCV para Mats vindos de Bitmap) para HSV,
     * útil porque HSV separa matiz/saturação/brilho e tolera melhor variação de
     * iluminação do que comparar RGB puro.
     */
    fun toHsv(mat: Mat): Mat {
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)
        return hsv
    }

    /**
     * Retorna uma máscara binária (0 ou 255) marcando os pixels de [mat] cuja cor HSV
     * está dentro de [lowerHsv]..[upperHsv]. Usado para localizar objetos por faixa de cor.
     */
    fun colorRangeMask(mat: Mat, lowerHsv: Scalar, upperHsv: Scalar): Mat {
        val hsv = toHsv(mat)
        val mask = Mat()
        Core.inRange(hsv, lowerHsv, upperHsv, mask)
        hsv.release()
        return mask
    }

    /**
     * Similaridade de cor entre 0 e 100 usando distância euclidiana no espaço RGB,
     * normalizada pela distância máxima possível (~441.67, diagonal do cubo RGB).
     * [toleranceFactor] (0..1) relaxa a curva: valores maiores tornam o score menos
     * sensível a pequenas diferenças de cor/iluminação, conforme pedido no
     * planejamento ("tolerância configurável").
     */
    fun colorSimilarity(
        colorA: Scalar,
        colorB: Scalar,
        toleranceFactor: Double = 0.15
    ): Double {
        val dr = colorA.`val`[0] - colorB.`val`[0]
        val dg = colorA.`val`[1] - colorB.`val`[1]
        val db = colorA.`val`[2] - colorB.`val`[2]
        val distance = sqrt(dr * dr + dg * dg + db * db)

        val maxDistance = 441.67
        val tolerance = (toleranceFactor * maxDistance).coerceAtLeast(1.0)
        val adjustedDistance = (distance - tolerance).coerceAtLeast(0.0)
        val remainingRange = (maxDistance - tolerance).coerceAtLeast(1.0)

        val similarity = 100.0 * (1.0 - (adjustedDistance / remainingRange))
        return similarity.coerceIn(0.0, 100.0)
    }

    /**
     * Desvio-padrão dos canais de cor de uma região — útil para saber se um objeto
     * é "sólido" (uma cor só) ou tem muita variação interna (textura, gradiente, texto).
     */
    fun colorVariance(mat: Mat): Double {
        val mean = MatOfDouble()
        val stdDev = MatOfDouble()
        Core.meanStdDev(mat, mean, stdDev)
        val values = stdDev.toArray()
        mean.release()
        stdDev.release()
        return values.average()
    }
}
