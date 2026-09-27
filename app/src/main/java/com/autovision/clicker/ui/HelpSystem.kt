package com.autovision.clicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sistema de ajuda contextual usado em todo o app ("Como funciona?").
 *
 * Cada função importante (autoclicker, reconhecimento de imagem, OCR, etc.) tem
 * uma entrada em [HelpTopic] com um texto simples explicando o que a função faz,
 * quando usar, como configurar e um exemplo prático. Qualquer tela pode mostrar
 * um ícone de ajuda ao lado de um título chamando [HelpIcon].
 */
data class HelpContent(
    val title: String,
    val whatItDoes: String,
    val whenToUse: String,
    val howToConfigure: String,
    val example: String,
    val whatHappens: String
)

/**
 * Catálogo central de tutoriais. Mantenha as explicações em linguagem simples,
 * como se fossem para alguém que nunca usou a ferramenta.
 */
object HelpTopic {
    val AUTOCLICKER = HelpContent(
        title = "Autoclicker",
        whatItDoes = "Toca sozinho em um ou mais pontos da tela, repetidamente, no intervalo de tempo que você escolher.",
        whenToUse = "Use quando você já sabe exatamente onde o toque deve acontecer (a posição não muda de lugar).",
        howToConfigure = "Defina as coordenadas X/Y do toque (ou vários pontos em sequência), o intervalo entre os toques e quantas vezes repetir.",
        example = "Você quer clicar no mesmo botão \"Coletar\" a cada 2 segundos. Configure o ponto sobre o botão e o intervalo de 2s — o app cuida do resto.",
        whatHappens = "O app envia toques reais nas coordenadas configuradas, no ritmo definido, até você pausar, parar ou atingir o limite de repetições."
    )

    val COORDINATE_CLICK = HelpContent(
        title = "Clique por coordenada",
        whatItDoes = "Executa um toque, toque duplo, toque longo ou arraste (swipe) num ponto fixo da tela.",
        whenToUse = "Use quando o elemento que você quer tocar sempre aparece exatamente na mesma posição.",
        howToConfigure = "Informe as coordenadas X/Y (e, no caso de swipe, também o ponto de destino).",
        example = "Um botão \"Próximo\" que sempre fica no canto inferior direito da tela.",
        whatHappens = "O app toca exatamente naquele ponto, sem procurar nada na tela antes."
    )

    val SMART_CLICK = HelpContent(
        title = "Clique inteligente",
        whatItDoes = "Em vez de usar sempre a mesma coordenada, o app primeiro procura um alvo (uma imagem, um texto, uma cor, uma forma ou um elemento do Android) e só depois clica na posição onde encontrou.",
        whenToUse = "Use quando o botão ou elemento que você quer clicar pode mudar de lugar na tela.",
        howToConfigure = "Escolha o tipo de alvo (imagem, texto, cor, forma, elemento ou região) e configure os detalhes desse tipo de busca.",
        example = "\"Encontrar a imagem do botão X e clicar nela.\" Mesmo que esse botão apareça em outro lugar da tela na próxima vez, o app procura de novo e acerta a posição nova.",
        whatHappens = "O app primeiro faz a busca visual configurada; se encontrar algo com confiança suficiente, clica no local encontrado. Se não encontrar, não clica em lugar nenhum."
    )

    val IMAGE_RECOGNITION = HelpContent(
        title = "Reconhecimento de imagem",
        whatItDoes = "Procura, na tela, uma imagem parecida com uma imagem de referência que você escolheu.",
        whenToUse = "Use quando quiser que o app encontre um botão ou ícone específico, mesmo que ele mude de posição.",
        howToConfigure = "Escolha uma imagem de referência (um recorte salvo) e defina a confiança mínima aceitável (0 a 100%).",
        example = "Você salvou uma imagem de um botão. Mesmo que o botão apareça em outra posição da tela, o app tentará encontrá-lo e clicar nele.",
        whatHappens = "O app compara a imagem de referência com a tela atual. Se achar uma correspondência acima da confiança mínima, ele informa a posição encontrada e executa a ação configurada; abaixo disso, não clica."
    )

    val OCR_TEXT = HelpContent(
        title = "Reconhecimento de texto (OCR)",
        whatItDoes = "Lê os textos visíveis na tela e procura por uma palavra ou frase específica, como \"Continuar\" ou \"OK\".",
        whenToUse = "Use quando o alvo é identificado por um texto, não por uma imagem ou posição fixa.",
        howToConfigure = "Digite o texto a procurar, escolha se a busca deve ser exata ou parcial, se deve ignorar maiúsculas/minúsculas, e onde clicar em relação ao texto encontrado (centro, acima, abaixo, esquerda, direita ou um deslocamento X/Y).",
        example = "Procurar o texto \"Comprar\" na tela e clicar assim que ele aparecer, não importa onde esteja.",
        whatHappens = "O app varre a tela em busca do texto configurado. Ao encontrar, calcula a posição de clique de acordo com o deslocamento escolhido e executa a ação."
    )

    val ELEMENT_RECOGNITION = HelpContent(
        title = "Reconhecimento de elementos Android",
        whatItDoes = "Usa o serviço de acessibilidade do Android para encontrar botões e campos reais da tela (não apenas imagens), identificando-os pelo texto, descrição, id interno ou tipo.",
        whenToUse = "Use quando quiser uma automação mais confiável dentro de apps que o Android consegue \"enxergar\" pela acessibilidade.",
        howToConfigure = "Escolha como localizar o elemento: por texto visível, descrição de acessibilidade, id do recurso, tipo do componente, se é clicável, posição ou estado.",
        example = "Encontrar o botão \"Enviar\" pelo texto exato, mesmo que sua posição na tela mude entre versões do app.",
        whatHappens = "Quando o elemento é encontrado e possui uma ação de clique nativa, o app usa essa ação diretamente (mais confiável que simular um toque na coordenada)."
    )

    val COLOR_RECOGNITION = HelpContent(
        title = "Reconhecimento de cores",
        whatItDoes = "Procura, na tela, uma região que tenha a cor configurada (em RGB ou HSV), dentro de uma tolerância.",
        whenToUse = "Use quando o alvo é identificável por cor, como uma barra de vida, um alerta colorido ou um botão de cor única.",
        howToConfigure = "Escolha a cor alvo, a tolerância (quanto a cor pode variar) e, se quiser, uma área mínima e máxima para a região encontrada.",
        example = "\"Encontrar objeto vermelho\" — o app localiza a maior mancha vermelha na tela e retorna sua posição.",
        whatHappens = "O app varre a tela procurando pixels dentro da faixa de cor configurada e agrupa a maior região encontrada, retornando o centro dela."
    )

    val SHAPE_RECOGNITION = HelpContent(
        title = "Reconhecimento de formas",
        whatItDoes = "Detecta formas geométricas na tela, como círculos, quadrados, retângulos, triângulos e outros polígonos.",
        whenToUse = "Use quando o alvo tem um formato característico, independente da cor exata.",
        howToConfigure = "Escolha o tipo de forma esperada (ou deixe em aberto para detectar qualquer forma) e a confiança mínima.",
        example = "Detectar um botão circular no meio de vários outros elementos quadrados.",
        whatHappens = "O app analisa os contornos da imagem e classifica cada forma encontrada, retornando posição, tamanho e confiança de cada uma."
    )

    val PAIR_RECOGNITION = HelpContent(
        title = "Reconhecimento de pares",
        whatItDoes = "Compara duas áreas da tela (por exemplo, um grupo de cima e um de baixo) e descobre quais figuras são iguais entre si, mesmo que estejam em posições, ordens, cores ou tamanhos diferentes.",
        whenToUse = "Use em jogos de memória ou combinação de pares, onde as figuras trocam de posição a cada rodada.",
        howToConfigure = "Defina as duas regiões a comparar (ou deixe o app dividir a tela automaticamente) e a sequência de ação a executar para cada par encontrado.",
        example = "Linha de cima tem os símbolos A, B, C; linha de baixo tem C, A, B em outra ordem. O app identifica que A da linha de cima corresponde ao A da linha de baixo, e assim por diante.",
        whatHappens = "O app compara as características visuais (forma, não posição) de cada figura das duas regiões e monta os pares correspondentes antes de agir."
    )

    val SEARCH_REGION = HelpContent(
        title = "Região de busca",
        whatItDoes = "Limita onde o app deve procurar um alvo, em vez de varrer a tela inteira.",
        whenToUse = "Use para acelerar a busca e evitar falsos positivos em outras partes da tela.",
        howToConfigure = "Escolha tela inteira, metade superior, inferior, esquerda, direita, ou desenhe uma área personalizada (ROI).",
        example = "Procurar um ícone apenas na barra inferior do app, ignorando o resto da tela.",
        whatHappens = "A busca configurada (imagem, cor, forma, texto etc.) só analisa pixels dentro da região escolhida — o alvo pode estar em qualquer posição dentro dela."
    )

    val SEQUENCES = HelpContent(
        title = "Sequências",
        whatItDoes = "Encadeia várias ações, uma depois da outra, formando um fluxo completo de automação.",
        whenToUse = "Use quando uma única ação não é suficiente — por exemplo, quando é preciso clicar, esperar e depois clicar em outro lugar.",
        howToConfigure = "Adicione ações à sequência (procurar, clicar, esperar, esperar até encontrar/desaparecer, swipe, condição, repetir, parar) e organize a ordem.",
        example = "Procurar imagem → clicar → esperar 500ms → procurar texto → clicar → swipe.",
        whatHappens = "O app executa cada ação da sequência em ordem, esperando cada uma terminar antes de seguir para a próxima."
    )

    val CONDITIONS = HelpContent(
        title = "Condições",
        whatItDoes = "Combina várias buscas visuais com E / OU / NÃO para decidir se uma ação deve acontecer.",
        whenToUse = "Use quando o alvo certo só existe quando mais de uma característica bate ao mesmo tempo.",
        howToConfigure = "Escolha as condições (imagem, cor, texto, forma) e o operador lógico entre elas.",
        example = "\"Encontrar um círculo vermelho contendo o texto OK\" — combina forma + cor + texto com E.",
        whatHappens = "O app só considera o alvo válido (e só clica) quando a combinação de condições configurada é satisfeita."
    )

    val WAIT_UNTIL_FOUND = HelpContent(
        title = "Esperar até encontrar",
        whatItDoes = "Pausa a automação até que um alvo específico apareça na tela (ou até estourar um tempo limite).",
        whenToUse = "Use quando uma tela demora para carregar e você não sabe exatamente quanto tempo vai levar.",
        howToConfigure = "Escolha o alvo a esperar (imagem, texto, cor, forma) e um tempo limite de segurança.",
        example = "Esperar até a imagem de \"carregamento concluído\" aparecer antes de continuar a sequência.",
        whatHappens = "O app fica verificando a tela em intervalos curtos; assim que encontra o alvo, segue para a próxima ação. Se o tempo limite passar, a automação registra um erro/timeout e segue conforme configurado."
    )

    val VISION_LAB = HelpContent(
        title = "Vision Lab",
        whatItDoes = "É uma área de testes separada da automação, onde você carrega uma imagem e testa os reconhecimentos (cor, forma, imagem, texto, pares) sem precisar rodar uma automação de verdade.",
        whenToUse = "Use antes de montar uma automação, para conferir se o app realmente identifica o alvo que você tem em mente.",
        howToConfigure = "Carregue uma imagem da galeria e veja os resultados desenhados diretamente sobre ela (caixas, centros, confiança).",
        example = "Testar se o app reconhece corretamente um ícone específico antes de usá-lo numa automação real.",
        whatHappens = "O app roda as engines de reconhecimento sobre a imagem carregada e mostra visualmente onde e com que confiança cada coisa foi encontrada."
    )

    val CONFIDENCE = HelpContent(
        title = "Confidence (confiança)",
        whatItDoes = "É um número de 0% a 100% que indica o quanto o app tem certeza de que encontrou o alvo certo.",
        whenToUse = "Use a confiança mínima para evitar cliques errados quando a imagem, cor ou forma não bate perfeitamente.",
        howToConfigure = "Defina um valor mínimo (por exemplo, 85%). Resultados abaixo disso são ignorados.",
        example = "Se a imagem encontrada tiver 60% de confiança e o mínimo configurado for 85%, o app não clica.",
        whatHappens = "Toda detecção visual mostra sua confiança. Se estiver abaixo do mínimo configurado, ou se houver ambiguidade, o app não executa a ação."
    )

    val OVERLAY = HelpContent(
        title = "Overlay flutuante",
        whatItDoes = "Mostra uma pequena janela flutuante por cima de outros aplicativos, com o status da automação e botões rápidos.",
        whenToUse = "Use para controlar a automação (iniciar, pausar, parar) sem precisar voltar ao AutoVision Clicker.",
        howToConfigure = "Ative a permissão de sobreposição quando solicitado pelo app.",
        example = "Enquanto joga outro app, você toca em \"Pausar\" direto no overlay, sem sair do jogo.",
        whatHappens = "O overlay fica sempre visível e permite parar a automação imediatamente, inclusive como botão de emergência."
    )

    val AUTOMATION_EDITOR = HelpContent(
        title = "Editor de automação",
        whatItDoes = "Permite montar uma automação visualmente, blocos um em cima do outro, na ordem em que serão executados.",
        whenToUse = "Use para criar ou ajustar uma automação com várias etapas, sem precisar mexer em código.",
        howToConfigure = "Adicione blocos (procurar, esperar, clicar, condição, repetir...), reordene arrastando, edite ou duplique blocos existentes.",
        example = "Um editor com os blocos: Encontrar imagem → Esperar 500ms → Clicar → Encontrar texto → Clicar → Swipe.",
        whatHappens = "Ao salvar, a sequência de blocos vira uma automação que pode ser executada, pausada e parada como qualquer outra."
    )

    val VISUAL_DEBUG = HelpContent(
        title = "Visual Debug",
        whatItDoes = "Mostra, por cima da imagem analisada, tudo o que o app está enxergando: caixas ao redor dos objetos, coordenadas, centros, textos lidos, formas e cores identificadas, junto com a confiança de cada detecção.",
        whenToUse = "Use quando uma automação não está encontrando o alvo esperado, para entender o que o app está vendo de fato.",
        howToConfigure = "Nenhuma configuração extra — é ativado automaticamente no Vision Lab e pode ser ligado durante uma automação real.",
        example = "A automação não clica onde deveria; o Visual Debug mostra que o app está encontrando um objeto parecido, mas com confiança baixa, num outro canto da tela.",
        whatHappens = "As caixas, textos e números de confiança aparecem desenhados sobre a imagem ou a tela ao vivo, sem alterar o funcionamento da automação."
    )
}

/**
 * Ícone de interrogação clicável que abre um [HelpDialog] com o conteúdo de [content].
 * Use ao lado de qualquer título de função para oferecer o tutorial "Como funciona?".
 */
@Composable
fun HelpIcon(content: HelpContent, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }, modifier = modifier.size(28.dp)) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "Como funciona: ${content.title}"
        )
    }

    if (showDialog) {
        HelpDialog(content = content, onDismiss = { showDialog = false })
    }
}

@Composable
fun HelpDialog(content: HelpContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Como funciona: ${content.title}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HelpSection(label = "O que essa função faz", body = content.whatItDoes)
                HelpSection(label = "Quando usar", body = content.whenToUse)
                HelpSection(label = "Como configurar", body = content.howToConfigure)
                HelpSection(label = "Exemplo prático", body = content.example)
                HelpSection(label = "O que acontece ao executar", body = content.whatHappens)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendi") }
        }
    )
}

@Composable
private fun HelpSection(label: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}
