package com.autovision.clicker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Seções principais de navegação do AutoVision Clicker (Fase 1 — reorganização
 * da interface). Cada seção é uma tela dentro da mesma Activity, trocada por
 * uma bottom navigation bar, para manter tudo simples de navegar no celular.
 */
enum class AppSection(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Filled.Home),
    AUTOMATIONS("Automações", Icons.Filled.PlayArrow),
    RECOGNITION("Reconhecimento", Icons.Filled.Search),
    VISION_LAB("Vision Lab", Icons.Filled.Star),
    SETTINGS("Ajustes", Icons.Filled.Settings)
}
