package com.autovision.clicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import java.util.UUID

/**
 * Tela "Automações" (Fase 1): lista os perfis de automação já criados, permite
 * montar um novo perfil simples (autoclicker tradicional em um ponto) e controlar
 * a execução (iniciar / pausar / parar) usando o AutomationEngine já existente.
 *
 * O editor visual completo de blocos (Fase 4) ainda não existe — por enquanto,
 * "Criar automação" gera um perfil básico de clique repetido em coordenadas,
 * que já usa a mesma engine e os mesmos modelos que serão reaproveitados depois.
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

@Composable
private fun CreateProfileForm(onCreate: (AutomationProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var intervalMs by remember { mutableStateOf("1000") }
    var repeatCount by remember { mutableStateOf("0") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it.filter(Char::isDigit) },
                    label = { Text("X") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it.filter(Char::isDigit) },
                    label = { Text("Y") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
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
