package com.autovision.clicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tela "Reconhecimento" (Fase 1): apresenta, de forma organizada, todos os tipos
 * de reconhecimento visual planejados para o AutoVision Clicker, cada um com seu
 * tutorial "Como funciona?". A implementação de cada engine (imagem, cor e forma
 * já existem; texto/OCR, elementos e pares chegam nas próximas fases) é reaproveitada
 * conforme cada uma for evoluída — esta tela é o painel central para configurá-las
 * e entendê-las, sem misturar tudo na tela inicial.
 */
@Composable
fun RecognitionScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Reconhecimento", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Escolha como o app deve encontrar os alvos na tela. Toque no ícone de ajuda para entender cada tipo.",
            style = MaterialTheme.typography.bodyMedium
        )

        RecognitionCard(
            title = "Clique inteligente",
            description = "Combina qualquer um dos tipos abaixo para localizar e clicar num alvo que muda de posição.",
            help = HelpTopic.SMART_CLICK,
            status = "Disponível"
        )
        RecognitionCard(
            title = "Reconhecimento de imagem",
            description = "Encontra uma imagem de referência na tela, mesmo com pequenas diferenças.",
            help = HelpTopic.IMAGE_RECOGNITION,
            status = "Disponível"
        )
        RecognitionCard(
            title = "Reconhecimento de cores",
            description = "Encontra a maior região de uma cor específica na tela.",
            help = HelpTopic.COLOR_RECOGNITION,
            status = "Disponível"
        )
        RecognitionCard(
            title = "Reconhecimento de formas",
            description = "Detecta círculos, quadrados, retângulos, triângulos e outros polígonos.",
            help = HelpTopic.SHAPE_RECOGNITION,
            status = "Disponível no Vision Lab"
        )
        RecognitionCard(
            title = "Reconhecimento de texto (OCR)",
            description = "Lê textos na tela e localiza palavras ou frases específicas.",
            help = HelpTopic.OCR_TEXT,
            status = "Em desenvolvimento — próxima fase"
        )
        RecognitionCard(
            title = "Reconhecimento de elementos Android",
            description = "Localiza botões e campos reais via serviço de acessibilidade.",
            help = HelpTopic.ELEMENT_RECOGNITION,
            status = "Em desenvolvimento — próxima fase"
        )
        RecognitionCard(
            title = "Reconhecimento de pares",
            description = "Identifica figuras iguais entre duas áreas da tela, mesmo fora de ordem.",
            help = HelpTopic.PAIR_RECOGNITION,
            status = "Em desenvolvimento — próxima fase"
        )
        RecognitionCard(
            title = "Região de busca",
            description = "Limita a área da tela onde a busca visual acontece.",
            help = HelpTopic.SEARCH_REGION,
            status = "Em desenvolvimento — próxima fase"
        )
        RecognitionCard(
            title = "Combinação de condições",
            description = "Junta várias buscas com E / OU / NÃO para alvos mais precisos.",
            help = HelpTopic.CONDITIONS,
            status = "Em desenvolvimento — próxima fase"
        )
        RecognitionCard(
            title = "Confidence",
            description = "Define o quanto o app precisa ter certeza antes de agir.",
            help = HelpTopic.CONFIDENCE,
            status = "Disponível"
        )
    }
}

@Composable
private fun RecognitionCard(title: String, description: String, help: HelpContent, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                HelpIcon(content = help)
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Text(text = status, style = MaterialTheme.typography.labelMedium)
        }
    }
}
