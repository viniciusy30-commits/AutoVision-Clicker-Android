package com.autovision.clicker

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Application customizada, responsável por inicializar a biblioteca nativa do
 * OpenCV assim que o processo do app sobe. Todo o resto do código de visão
 * (ImageRecognitionEngine, ColorRecognitionEngine, ShapeRecognitionEngine) assume
 * que o OpenCV já está carregado quando é chamado.
 */
class AutoVisionApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val loaded = OpenCVLoader.initLocal()
        if (loaded) {
            Log.i(TAG, "OpenCV inicializado com sucesso")
        } else {
            Log.e(TAG, "Falha ao inicializar o OpenCV — reconhecimento visual não vai funcionar")
        }
    }

    companion object {
        private const val TAG = "AutoVisionApplication"
    }
}
