package com.pesquisa.visualassist.vision

import android.content.Context

/**
 * Detecção de objetos on-device.
 * Opções (design.md): MediaPipe Object Detection (EfficientDet-Lite) ou
 * YOLO exportado para ONNX rodando via ONNX Runtime Mobile.
 *
 * Modelo em: app/src/main/assets/models/  (converter na distrobox quest-ml)
 * Requisitos: 2.1, 2.3, 2.4
 */
class ObjectDetector(
  private val context: Context,
  private val confidenceThreshold: Float = 0.5f,
) : VisionDetector {

  override fun warmup() {
    // TODO(task 5.2): carregar modelo de assets/models e inicializar o runtime.
  }

  override fun detect(frame: CameraFrame): List<Detection> {
    // TODO(task 5.2): inferência -> filtrar por confiança -> calcular Direction.
    return emptyList()
  }

  override fun close() {
    // TODO: liberar recursos do runtime.
  }
}
