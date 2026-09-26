package com.autovision.clicker.models

/**
 * Forma geométrica detectada pelo ShapeRecognitionEngine.
 */
enum class DetectedShape {
    CIRCLE,
    SQUARE,
    RECTANGLE,
    TRIANGLE,
    IRREGULAR
}

/**
 * Modo de comparação visual usado pelo VisionEngine.
 *
 * COLOR: a cor influencia bastante a comparação.
 * SHAPE: reduz a influência da cor (grayscale, bordas, contornos, geometria).
 * HYBRID: combina forma + características + cor com pesos configuráveis.
 */
enum class ComparisonMode {
    COLOR,
    SHAPE,
    HYBRID
}

/**
 * Pesos usados no modo HYBRID para calcular o score visual final:
 * visualScore = shapeScore * shapeWeight + featureScore * featureWeight + colorScore * colorWeight
 * A soma dos três pesos deve ser 1.0 para o score final ficar em 0..100.
 */
data class ComparisonWeights(
    val shapeWeight: Double = 0.50,
    val featureWeight: Double = 0.30,
    val colorWeight: Double = 0.20
)

/**
 * Um objeto detectado na tela (ou numa imagem carregada no Vision Lab).
 *
 * x, y, width e height descrevem o retângulo delimitador (bounding box).
 * centerX/centerY são o ponto onde um clique seria executado.
 * confidence vai de 0 a 100 e representa o quão certo o engine está da detecção.
 */
data class DetectedObject(
    val id: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val centerX: Int,
    val centerY: Int,
    val area: Double,
    val shape: DetectedShape,
    val confidence: Double,
    val dominantColorRgb: Int
)

/**
 * Resultado de uma comparação entre dois objetos/imagens (usado no matching de pares
 * e na comparação simples do Vision Lab).
 */
data class MatchResult(
    val sourceId: Int,
    val targetId: Int,
    val shapeScore: Double,
    val featureScore: Double,
    val colorScore: Double,
    val overallScore: Double
)

/**
 * Um par encontrado por findVisualPairs(): dois objetos que o algoritmo considera
 * a mesma coisa, mesmo que suas posições tenham mudado entre capturas.
 */
data class VisualPair(
    val topObject: DetectedObject,
    val bottomObject: DetectedObject,
    val matchScore: Double
)

/**
 * Resultado agregado de rodar todos os engines de reconhecimento sobre uma imagem,
 * usado para preencher a tela do Vision Lab.
 */
data class VisionAnalysisResult(
    val objects: List<DetectedObject> = emptyList(),
    val pairs: List<VisualPair> = emptyList(),
    val processingTimeMillis: Long = 0L
)
