package com.pesquisa.visualassist.vision

import android.content.Context

/**
 * Detecção de PRESENÇA de pessoas (não identificação/reconhecimento).
 * MediaPipe Face Detector (BlazeFace) ou Pose Detection.
 *
 * IMPORTANTE (Requisitos 4.2, 4.3, 7):
 *  - NÃO identificar indivíduos.
 *  - NÃO persistir imagens de rostos.
 *  - Apenas anunciar presença e posição relativa.
 */
class PersonDetector(private val context: Context) : VisionDetector {

  override fun warmup() {
    // TODO(task 5.4): inicializar MediaPipe Face/Pose detector.
  }

  override fun detect(frame: CameraFrame): List<Detection> {
    // TODO(task 5.4): detectar presença -> Detection(kind = PERSON, direction = ...)
    return emptyList()
  }
}
