package com.autovision.clicker.models

/**
 * Tipos de ação que o AutomationEngine sabe executar. Cada ação vira, no final,
 * um ou mais gestos despachados pelo AutoVisionAccessibilityService, ou uma
 * espera, ou uma busca visual (imagem/cor) que decide o que fazer a seguir.
 */
enum class ActionType {
    CLICK,
    DOUBLE_CLICK,
    LONG_PRESS,
    SWIPE,
    WAIT,
    IMAGE_MATCH,
    COLOR_MATCH,
    SEQUENCE
}

/**
 * Uma ação configurável dentro de uma automação.
 *
 * Nem todo campo é usado por todo tipo de ação — cada [ActionType] só lê os que
 * fazem sentido para ele:
 * - CLICK / DOUBLE_CLICK / LONG_PRESS: [x], [y]
 * - SWIPE: [x],[y] (origem), [endX],[endY] (destino), [durationMillis]
 * - WAIT: [durationMillis]
 * - IMAGE_MATCH: [referenceImagePath] (ou bitmap externo), [minConfidence]; ao achar,
 *   clica no centro do melhor match encontrado na tela atual.
 * - COLOR_MATCH: [targetColorRgb], [colorTolerance]; ao achar, clica no centro da
 *   maior região daquela cor encontrada na tela atual.
 * - SEQUENCE: [children], executadas em ordem.
 */
data class AutomationAction(
    val id: String,
    val type: ActionType,
    val x: Int = 0,
    val y: Int = 0,
    val endX: Int = 0,
    val endY: Int = 0,
    val durationMillis: Long = 300L,
    val referenceImagePath: String? = null,
    val minConfidence: Double = 70.0,
    val targetColorRgb: Int = 0,
    val colorTolerance: Int = 30,
    val repeatCount: Int = 1,
    val delayAfterMillis: Long = 200L,
    val children: List<AutomationAction> = emptyList(),
    val label: String = ""
)

/**
 * Uma automação completa: uma lista de ações executadas em ordem, com um número
 * de repetições do conjunto inteiro (0 = repete indefinidamente até ser parado).
 */
data class AutomationProfile(
    val id: String,
    val name: String,
    val actions: List<AutomationAction> = emptyList(),
    val loopCount: Int = 1
)

/** Estado de execução do AutomationEngine, observado pela UI. */
enum class AutomationRunState {
    IDLE,
    RUNNING,
    PAUSED,
    WAITING_FOR_ACCESSIBILITY,
    ERROR,
    FINISHED
}

/** Snapshot do progresso da automação, para a UI mostrar o que está acontecendo. */
data class AutomationProgress(
    val runState: AutomationRunState = AutomationRunState.IDLE,
    val currentLoop: Int = 0,
    val totalLoops: Int = 1,
    val currentActionIndex: Int = 0,
    val totalActions: Int = 0,
    val currentActionLabel: String = "",
    val lastMessage: String = ""
)
