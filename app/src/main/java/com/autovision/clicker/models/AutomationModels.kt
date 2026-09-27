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
    CONDITIONAL_CLICK,
    SEQUENCE
}

/**
 * Região retangular da tela onde uma busca visual (IMAGE_MATCH, COLOR_MATCH,
 * CONDITIONAL_CLICK) deve procurar, em vez de varrer a tela inteira.
 *
 * Coordenadas em pixels de tela, mesmo referencial usado pelos cliques
 * (AutoVisionAccessibilityService). Quando [AutomationAction.searchRegion] é
 * `null`, a busca considera a tela inteira — comportamento igual ao da Fase 1.
 *
 * Restringir a região deixa a busca mais rápida (menos pixels para varrer) e
 * evita falsos positivos quando existe mais de uma ocorrência parecida em
 * outro canto da tela.
 */
data class SearchRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Operador lógico usado para combinar duas condições em [MatchCondition.Combined].
 */
enum class ConditionOperator {
    AND,
    OR
}

/**
 * Uma condição visual combinável, usada por [ActionType.CONDITIONAL_CLICK] para
 * decidir se deve clicar. Cada condição folha (Image/Color) descreve uma busca
 * visual; [Combined] junta duas condições com E/OU; [Negated] inverte o
 * resultado de uma condição (equivalente a um NÃO).
 *
 * Exemplo: "encontrar imagem A E NÃO encontrar imagem B" vira:
 * Combined(
 *     operator = AND,
 *     left = Image(referenceImagePath = "A", minConfidence = 80.0),
 *     right = Negated(Image(referenceImagePath = "B", minConfidence = 80.0))
 * )
 */
sealed class MatchCondition {

    /** Verdadeira quando a imagem de referência é encontrada na tela (ou região) com confiança suficiente. */
    data class Image(
        val referenceImagePath: String?,
        val minConfidence: Double = 70.0,
        val searchRegion: SearchRegion? = null
    ) : MatchCondition()

    /** Verdadeira quando a cor alvo é encontrada na tela (ou região) dentro da tolerância. */
    data class Color(
        val targetColorRgb: Int,
        val colorTolerance: Int = 30,
        val searchRegion: SearchRegion? = null
    ) : MatchCondition()

    /** Combina duas condições com E (ambas precisam ser verdadeiras) ou OU (uma das duas basta). */
    data class Combined(
        val operator: ConditionOperator,
        val left: MatchCondition,
        val right: MatchCondition
    ) : MatchCondition()

    /** Inverte o resultado de [condition] — verdadeira quando [condition] NÃO for satisfeita. */
    data class Negated(val condition: MatchCondition) : MatchCondition()
}

/**
 * Uma ação configurável dentro de uma automação.
 *
 * Nem todo campo é usado por todo tipo de ação — cada [ActionType] só lê os que
 * fazem sentido para ele:
 * - CLICK / DOUBLE_CLICK / LONG_PRESS: [x], [y]
 * - SWIPE: [x],[y] (origem), [endX],[endY] (destino), [durationMillis]
 * - WAIT: [durationMillis]
 * - IMAGE_MATCH: [referenceImagePath] (ou bitmap externo), [minConfidence],
 *   [searchRegion] (opcional; null = tela inteira); ao achar, clica no centro
 *   do melhor match encontrado na região (ou tela).
 * - COLOR_MATCH: [targetColorRgb], [colorTolerance], [searchRegion] (opcional);
 *   ao achar, clica no centro da maior região daquela cor encontrada.
 * - CONDITIONAL_CLICK: [condition] (árvore de E/OU/NÃO); só clica em [x],[y]
 *   (ou no centro do match, se a condição raiz tiver uma posição associada)
 *   quando a condição combinada for satisfeita.
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
    val searchRegion: SearchRegion? = null,
    val condition: MatchCondition? = null,
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
