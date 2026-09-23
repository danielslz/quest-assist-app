package com.pesquisa.visualassist.vision

import android.content.Context

/**
 * OCR on-device com ML Kit Text Recognition (Requisitos 3.1-3.4).
 * Acionado sob demanda (não a cada frame) — leitura de placas/letreiros.
 * ML Kit suporta scripts latinos (pt/en).
 */
class TextRecognizer(private val context: Context) : VisionDetector {

  override fun detect(frame: CameraFrame): List<Detection> {
    // TODO(task 5.3): converter frame -> InputImage -> TextRecognition.process()
    //  - juntar blocos de texto; retornar Detection(kind = TEXT, label = texto)
    //  - se vazio: sinalizar "nenhum texto detectado" na camada de áudio.
    return emptyList()
  }
}
