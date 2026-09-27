package com.autovision.clicker.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade responsável por executar, de verdade, os toques e
 * swipes decididos pelo AutomationEngine. O Android só permite despachar gestos
 * sintéticos (cliques, swipes) através de um AccessibilityService — por isso ele
 * existe separado do resto do app.
 *
 * O serviço não decide nada sozinho: ele só expõe funções simples (click, swipe,
 * longPress, doubleClick) que o AutomationEngine chama. Toda a lógica de "o que
 * clicar e quando" fica no AutomationEngine.
 */
class AutoVisionAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * Referência estática para a instância viva do serviço, para o
         * AutomationEngine conseguir despachar gestos sem precisar de bind/unbind
         * manual. É seguro porque só existe uma instância deste serviço rodando
         * por vez (controlado pelo próprio Android).
         */
        @Volatile
        var instance: AutoVisionAccessibilityService? = null
            private set

        /** true quando o usuário já ativou o serviço nas configurações do sistema. */
        val isRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Este serviço não precisa reagir a eventos de acessibilidade da tela
        // (não lê a hierarquia de views); ele só executa gestos sob demanda.
    }

    override fun onInterrupt() {
        // Nada a limpar: os gestos em andamento são cancelados pelo próprio sistema.
    }

    /** Executa um toque simples em (x, y). [onResult] recebe true se o gesto foi despachado com sucesso. */
    fun click(x: Int, y: Int, durationMillis: Long = 60L, onResult: (Boolean) -> Unit = {}) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceAtLeast(1L))
        dispatchStroke(stroke, onResult)
    }

    /** Executa dois toques rápidos em sequência no mesmo ponto. */
    fun doubleClick(x: Int, y: Int, gapMillis: Long = 120L, onResult: (Boolean) -> Unit = {}) {
        click(x, y) { firstOk ->
            if (!firstOk) {
                onResult(false)
                return@click
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                click(x, y, onResult = onResult)
            }, gapMillis)
        }
    }

    /** Mantém o toque pressionado em (x, y) por [durationMillis]. */
    fun longPress(x: Int, y: Int, durationMillis: Long = 600L, onResult: (Boolean) -> Unit = {}) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceAtLeast(1L))
        dispatchStroke(stroke, onResult)
    }

    /** Arrasta de (startX, startY) até (endX, endY) em [durationMillis]. */
    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long = 300L,
        onResult: (Boolean) -> Unit = {}
    ) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMillis.coerceAtLeast(1L))
        dispatchStroke(stroke, onResult)
    }

    private fun dispatchStroke(stroke: GestureDescription.StrokeDescription, onResult: (Boolean) -> Unit) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onResult(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onResult(false)
                }
            },
            null
        )
        if (!dispatched) {
            onResult(false)
        }
    }
}
