package com.autovision.clicker.models

/**
 * Estados possíveis exibidos no Dashboard, conforme o planejamento original do projeto:
 * Desativado / Pronto / Executando / Pausado / Erro.
 */
enum class ModuleStatus {
    DISABLED,
    READY,
    RUNNING,
    PAUSED,
    ERROR
}

/**
 * Intervalos de análise de tela suportados pela captura (em milissegundos).
 */
enum class CaptureInterval(val millis: Long, val label: String) {
    MS_50(50L, "50 ms"),
    MS_100(100L, "100 ms"),
    MS_250(250L, "250 ms"),
    MS_500(500L, "500 ms"),
    MS_1000(1000L, "1000 ms")
}

/**
 * Estado consolidado da tela de Dashboard. A Activity observa isto e desenha os cards.
 */
data class DashboardState(
    val automationStatus: ModuleStatus = ModuleStatus.DISABLED,
    val captureStatus: ModuleStatus = ModuleStatus.DISABLED,
    val recognitionStatus: ModuleStatus = ModuleStatus.DISABLED,
    val captureInterval: CaptureInterval = CaptureInterval.MS_250,
    val framesCaptured: Int = 0,
    val logs: List<String> = emptyList()
)
