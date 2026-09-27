package com.autovision.clicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autovision.clicker.models.CaptureInterval

/**
 * Tela "Ajustes" (Fase 1): reúne configurações que antes estavam soltas na tela
 * inicial — ativação do serviço de Acessibilidade (obrigatório para os cliques
 * automáticos funcionarem) e o intervalo de análise da captura de tela.
 */
@Composable
fun SettingsScreen(
    captureInterval: CaptureInterval,
    onIntervalSelected: (CaptureInterval) -> Unit,
    onOpenAccessibilitySettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Ajustes", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Serviço de Acessibilidade", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Precisa estar ativado para o app conseguir tocar e arrastar automaticamente na tela.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onOpenAccessibilitySettingsClick, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Ativar serviço de Acessibilidade")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Intervalo de análise da tela", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Com que frequência o app analisa a tela durante a captura. Intervalos menores são mais precisos, porém consomem mais bateria.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureInterval.entries.forEach { interval ->
                        val selected = interval == captureInterval
                        Button(onClick = { onIntervalSelected(interval) }) {
                            Text(text = if (selected) "[${interval.label}]" else interval.label)
                        }
                    }
                }
            }
        }
    }
}
