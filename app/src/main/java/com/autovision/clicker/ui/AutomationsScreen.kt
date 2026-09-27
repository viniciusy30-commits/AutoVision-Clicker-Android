package com.autovision.clicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autovision.clicker.models.ActionType
import com.autovision.clicker.models.AutomationAction
import com.autovision.clicker.models.AutomationProfile
import com.autovision.clicker.models.AutomationProgress
import com.autovision.clicker.models.AutomationRunState
import com.autovision.clicker.models.ConditionOperator
import com.autovision.clicker.models.MatchCondition
import com.autovision.clicker.models.SearchRegion
import java.util.UUID

/**
 * Tela "Automações" (Fase 1 + Fase 2): lista os perfis de automação já criados,
 * permite montar perfis simples e controlar a execução (iniciar / pausar /
 * parar) usando o AutomationEngine já existente.
 *
 * Fase 2 adiciona ao formulário de criação:
 * - Região de busca opcional (em vez de varrer a tela inteira);
 * - Confiança mínima ajustável por slider;
 * - Um modo "condição combinada" simples (duas condições de imagem + E/OU/NÃO)
 *   que gera uma ação CONDITIONAL_CLICK.
 *
 * O editor visual completo de blocos (Fase 4) ainda não existe — por enquanto,
 * este formulário gera um único tipo de ação por vez, já usando os mesmos
 * modelos e a mesma engine que serão reaproveitados depois.
 */
@Composable
fun AutomationsScreen(
    profiles: List<AutomationProfile>,
    progress: AutomationProgress,
    onCreateProfile: (AutomationProfile) -> Unit,
    onDeleteProfile: (AutomationProfile) -> Unit,
    onStart: (AutomationProfile) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Automações", style = MaterialTheme.typography.headlineSmall)
            HelpIcon(content = HelpTopic.SEQUENCES)
        }
        Text(
            text = "Crie e controle suas automações de clique.",
            style = MaterialTheme.typography.bodyMedium
        )

        RunProgressCard(progress = progress, onPause = onPause, onResume = onResume, onStop = onStop)

        Button(
            onClick = { showCreateForm = !showCreateForm },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (showCreateForm) "Cancelar" else "+ Nova automação")
        }

        if (showCreateForm) {
            CreateProfileForm(
                onCreate = { profile ->
                    onCreateProfile(profile)
                    showCreateForm = false
                }
            )
        }

        if (profiles.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nenhuma automação criada ainda. Toque em \"Nova automação\" para começar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isRunning = progress.runState == AutomationRunState.RUNNING,
                        onStart = { onStart(profile) },
                        onDelete = { onDeleteProfile(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunProgressCard(
    progress: AutomationProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "STATUS DA EXECUÇÃO", style = MaterialTheme.typography.labelLarge)
            Text(text = runStateLabel(progress.runState), style = MaterialTheme.typography.titleMedium)
            if (progress.totalActions > 0) {
                Text(
                    text = "Ação ${progress.currentActionIndex}/${progress.totalActions} · Loop ${progress.currentLoop}/${if (progress.totalLoops <= 0) "∞" else progress.totalLoops.toString()}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(text = progress.currentActionLabel, style = MaterialTheme.typography.bodySmall)
            }
            if (progress.lastMessage.isNotBlank()) {
                Text(text = progress.lastMessage, style = MaterialTheme.typography.bodySmall)
            }

            val isRunning = progress.runState == AutomationRunState.RUNNING
            val isPaused = progress.runState == AutomationRunState.PAUSED

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    OutlinedButton(onClick = onPause) { Text("Pausar") }
                }
                if (isPaused) {
                    OutlinedButton(onClick = onResume) { Text("Retomar") }
                }
                if (isRunning || isPaused) {
                    OutlinedButton(onClick = onStop) { Text("Parar") }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: AutomationProfile,
    isRunning: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${profile.actions.size} ação(ões) · ${if (profile.loopCount <= 0) "repete sem parar" else "repete ${profile.loopCount}x"}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !isRunning) { Text("Iniciar") }
                OutlinedButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

/** As três formas de criação de automação disponíveis nesta tela (Fase 2). */
private enum class CreateMode {
    SIMPLE_CLICK,
    IMAGE_MATCH,
    CONDITIONAL_CLICK
}

@Composable
private fun CreateProfileForm(onCreate: (AutomationProfile) -> Unit) {
    var mode by remember { mutableStateOf(CreateMode.SIMPLE_CLICK) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(
                    label = "Autoclicker",
                    selected = mode == CreateMode.SIMPLE_CLICK,
                    onClick = { mode = CreateMode.SIMPLE_CLICK }
                )
                ModeChip(
                    label = "Clique inteligente",
                    selected = mode == CreateMode.IMAGE_MATCH,
                    onClick = { mode = CreateMode.IMAGE_MATCH }
                )
                ModeChip(
                    label = "Condição combinada",
                    selected = mode == CreateMode.CONDITIONAL_CLICK,
                    onClick = { mode = CreateMode.CONDITIONAL_CLICK }
                )
            }

            when (mode) {
                CreateMode.SIMPLE_CLICK -> SimpleClickForm(onCreate)
                CreateMode.IMAGE_MATCH -> ImageMatchForm(onCreate)
                CreateMode.CONDITIONAL_CLICK -> ConditionalClickForm(onCreate)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun SimpleClickForm(onCreate: (AutomationProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var intervalMs by remember { mutableStateOf("1000") }
    var repeatCount by remember { mutableStateOf("0") }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Autoclicker simples", style = MaterialTheme.typography.titleSmall)
        HelpIcon(content = HelpTopic.AUTOCLICKER)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nome da automação") },
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = x,
            onValueChange = { x = it.filter(Char::isDigit) },
            label = { Text("X") },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        OutlinedTextField(
            value = y,
            onValueChange = { y = it.filter(Char::isDigit) },
            label = { Text("Y") },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
    OutlinedTextField(
        value = intervalMs,
        onValueChange = { intervalMs = it.filter(Char::isDigit) },
        label = { Text("Intervalo entre cliques (ms)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = repeatCount,
        onValueChange = { repeatCount = it.filter(Char::isDigit) },
        label = { Text("Repetições (0 = sem parar)") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            val profile = AutomationProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Automação sem nome" },
                loopCount = repeatCount.toIntOrNull() ?: 0,
                actions = listOf(
                    AutomationAction(
                        id = UUID.randomUUID().toString(),
                        type = ActionType.CLICK,
                        x = x.toIntOrNull() ?: 0,
                        y = y.toIntOrNull() ?: 0,
                        delayAfterMillis = intervalMs.toLongOrNull() ?: 1000L,
                        label = "Clicar em ($x, $y)"
                    )
                )
            )
            onCreate(profile)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = x.isNotBlank() && y.isNotBlank()
    ) {
        Text("Salvar automação")
    }
}

/**
 * Campos reutilizados por qualquer formulário que precise de uma região de
 * busca opcional. Quando o switch está desligado, [onRegionChange] recebe
 * `null` (busca na tela inteira) — comportamento idêntico ao da Fase 1.
 */
@Composable
private fun SearchRegionFields(onRegionChange: (SearchRegion?) -> Unit) {
    var useRegion by remember { mutableStateOf(false) }
    var regionX by remember { mutableStateOf("") }
    var regionY by remember { mutableStateOf("") }
    var regionWidth by remember { mutableStateOf("") }
    var regionHeight by remember { mutableStateOf("") }

    fun pushRegion() {
        onRegionChange(
            if (!useRegion) {
                null
            } else {
                SearchRegion(
                    x = regionX.toIntOrNull() ?: 0,
                    y = regionY.toIntOrNull() ?: 0,
                    width = regionWidth.toIntOrNull() ?: 0,
                    height = regionHeight.toIntOrNull() ?: 0
                )
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Restringir a uma região da tela", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = useRegion,
            onCheckedChange = {
                useRegion = it
                pushRegion()
            }
        )
    }
    if (useRegion) {
        Text(
            text = "Toque em \"Vision Lab\" para descobrir as coordenadas certas antes de preencher aqui.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = regionX,
                onValueChange = { regionX = it.filter(Char::isDigit); pushRegion() },
                label = { Text("X inicial") },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            OutlinedTextField(
                value = regionY,
                onValueChange = { regionY = it.filter(Char::isDigit); pushRegion() },
                label = { Text("Y inicial") },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = regionWidth,
                onValueChange = { regionWidth = it.filter(Char::isDigit); pushRegion() },
                label = { Text("Largura") },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            OutlinedTextField(
                value = regionHeight,
                onValueChange = { regionHeight = it.filter(Char::isDigit); pushRegion() },
                label = { Text("Altura") },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

/** Slider de confiança mínima reutilizável (0% a 100%). */
@Composable
private fun ConfidenceSlider(value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Confiança mínima: ${value.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        HelpIcon(content = HelpTopic.CONFIDENCE)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f)
}

@Composable
private fun ImageMatchForm(onCreate: (AutomationProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imagePath by remember { mutableStateOf("") }
    var confidence by remember { mutableStateOf(70f) }
    var region by remember { mutableStateOf<SearchRegion?>(null) }
    var repeatCount by remember { mutableStateOf("0") }
    var intervalMs by remember { mutableStateOf("1000") }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Clique inteligente por imagem", style = MaterialTheme.typography.titleSmall)
        HelpIcon(content = HelpTopic.SMART_CLICK)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nome da automação") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = imagePath,
        onValueChange = { imagePath = it },
        label = { Text("Caminho da imagem de referência") },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = "Salve um recorte no Vision Lab e cole o caminho do arquivo aqui.",
        style = MaterialTheme.typography.bodySmall
    )
    ConfidenceSlider(value = confidence, onValueChange = { confidence = it })
    SearchRegionFields(onRegionChange = { region = it })
    OutlinedTextField(
        value = intervalMs,
        onValueChange = { intervalMs = it.filter(Char::isDigit) },
        label = { Text("Intervalo entre tentativas (ms)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = repeatCount,
        onValueChange = { repeatCount = it.filter(Char::isDigit) },
        label = { Text("Repetições (0 = sem parar)") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            val profile = AutomationProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Automação sem nome" },
                loopCount = repeatCount.toIntOrNull() ?: 0,
                actions = listOf(
                    AutomationAction(
                        id = UUID.randomUUID().toString(),
                        type = ActionType.IMAGE_MATCH,
                        referenceImagePath = imagePath,
                        minConfidence = confidence.toDouble(),
                        searchRegion = region,
                        delayAfterMillis = intervalMs.toLongOrNull() ?: 1000L,
                        label = "Procurar imagem e clicar"
                    )
                )
            )
            onCreate(profile)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = imagePath.isNotBlank()
    ) {
        Text("Salvar automação")
    }
}

/**
 * Formulário de "condição combinada": duas condições de imagem, unidas por
 * E/OU, com opção de negar cada uma — cobre o caso pedido no planejamento
 * ("SE encontrar imagem A E NÃO encontrar imagem B, ENTÃO clique em X") sem
 * precisar do editor visual completo (esse fica para a Fase 4).
 */
@Composable
private fun ConditionalClickForm(onCreate: (AutomationProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var clickX by remember { mutableStateOf("") }
    var clickY by remember { mutableStateOf("") }

    var imagePathA by remember { mutableStateOf("") }
    var confidenceA by remember { mutableStateOf(70f) }
    var negateA by remember { mutableStateOf(false) }

    var operator by remember { mutableStateOf(ConditionOperator.AND) }

    var imagePathB by remember { mutableStateOf("") }
    var confidenceB by remember { mutableStateOf(70f) }
    var negateB by remember { mutableStateOf(false) }

    var repeatCount by remember { mutableStateOf("0") }
    var intervalMs by remember { mutableStateOf("1000") }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Condição combinada", style = MaterialTheme.typography.titleSmall)
        HelpIcon(content = HelpTopic.CONDITIONS)
    }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Nome da automação") },
        modifier = Modifier.fillMaxWidth()
    )

    Text(text = "CONDIÇÃO A", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = imagePathA,
        onValueChange = { imagePathA = it },
        label = { Text("Imagem de referência A") },
        modifier = Modifier.fillMaxWidth()
    )
    ConfidenceSlider(value = confidenceA, onValueChange = { confidenceA = it })
    NegateSwitch(checked = negateA, onCheckedChange = { negateA = it })

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Operador entre as condições", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(
                label = "E",
                selected = operator == ConditionOperator.AND,
                onClick = { operator = ConditionOperator.AND }
            )
            ModeChip(
                label = "OU",
                selected = operator == ConditionOperator.OR,
                onClick = { operator = ConditionOperator.OR }
            )
        }
    }

    Text(text = "CONDIÇÃO B", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = imagePathB,
        onValueChange = { imagePathB = it },
        label = { Text("Imagem de referência B") },
        modifier = Modifier.fillMaxWidth()
    )
    ConfidenceSlider(value = confidenceB, onValueChange = { confidenceB = it })
    NegateSwitch(checked = negateB, onCheckedChange = { negateB = it })

    Text(text = "AÇÃO SE A CONDIÇÃO FOR SATISFEITA", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = clickX,
            onValueChange = { clickX = it.filter(Char::isDigit) },
            label = { Text("Clicar em X") },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        OutlinedTextField(
            value = clickY,
            onValueChange = { clickY = it.filter(Char::isDigit) },
            label = { Text("Clicar em Y") },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
    OutlinedTextField(
        value = intervalMs,
        onValueChange = { intervalMs = it.filter(Char::isDigit) },
        label = { Text("Intervalo entre tentativas (ms)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = repeatCount,
        onValueChange = { repeatCount = it.filter(Char::isDigit) },
        label = { Text("Repetições (0 = sem parar)") },
        modifier = Modifier.fillMaxWidth()
    )

    Button(
        onClick = {
            fun leaf(path: String, confidence: Float, negate: Boolean): MatchCondition {
                val base: MatchCondition = MatchCondition.Image(
                    referenceImagePath = path,
                    minConfidence = confidence.toDouble()
                )
                return if (negate) MatchCondition.Negated(base) else base
            }

            val combined = MatchCondition.Combined(
                operator = operator,
                left = leaf(imagePathA, confidenceA, negateA),
                right = leaf(imagePathB, confidenceB, negateB)
            )

            val profile = AutomationProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Automação sem nome" },
                loopCount = repeatCount.toIntOrNull() ?: 0,
                actions = listOf(
                    AutomationAction(
                        id = UUID.randomUUID().toString(),
                        type = ActionType.CONDITIONAL_CLICK,
                        x = clickX.toIntOrNull() ?: 0,
                        y = clickY.toIntOrNull() ?: 0,
                        condition = combined,
                        delayAfterMillis = intervalMs.toLongOrNull() ?: 1000L,
                        label = "Clique condicional"
                    )
                )
            )
            onCreate(profile)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = imagePathA.isNotBlank() && imagePathB.isNotBlank() &&
            clickX.isNotBlank() && clickY.isNotBlank()
    ) {
        Text("Salvar automação")
    }
}

@Composable
private fun NegateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Inverter (NÃO encontrar)", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun runStateLabel(state: AutomationRunState): String = when (state) {
    AutomationRunState.IDLE -> "Parado"
    AutomationRunState.RUNNING -> "Executando"
    AutomationRunState.PAUSED -> "Pausado"
    AutomationRunState.WAITING_FOR_ACCESSIBILITY -> "Aguardando ativação da Acessibilidade"
    AutomationRunState.ERROR -> "Erro"
    AutomationRunState.FINISHED -> "Concluído"
}
