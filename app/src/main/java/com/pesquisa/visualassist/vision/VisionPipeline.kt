package com.pesquisa.visualassist.vision

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Contrato comum a todos os detectores (objetos, texto, pessoas).
 * Requisitos: 2.1, 3.1, 4.1
 */
interface VisionDetector {
  fun warmup() {}
  fun detect(frame: CameraFrame): List<Detection>
  fun close() {}
}

/**
 * Orquestra a inferência fora do thread de render.
 * Mantém buffer de 1 frame (dropa antigos) para não acumular latência.
 *
 * Requisitos: 2.2 (>= 10 FPS, sem travar render)
 */
class VisionPipeline(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private val detectors = mutableListOf<VisionDetector>()
  private val latestFrame = AtomicReference<CameraFrame?>(null)
  @Volatile private var running = false

  fun register(detector: VisionDetector) {
    detectors.add(detector)
  }

  /** Recebe frame do CameraController; substitui o pendente (drop de frames antigos). */
  fun submit(frame: CameraFrame, onResult: (List<Detection>) -> Unit) {
    latestFrame.set(frame)
    if (!running) startLoop(onResult)
  }

  private fun startLoop(onResult: (List<Detection>) -> Unit) {
    running = true
    scope.launch(Dispatchers.Default) {
      detectors.forEach { it.warmup() }
      while (running) {
        val frame = latestFrame.getAndSet(null)
        if (frame == null) {
          continue // sem frame novo
        }
        val all = detectors.flatMap { runCatching { it.detect(frame) }.getOrDefault(emptyList()) }
        if (all.isNotEmpty()) onResult(all)
      }
    }
  }

  fun stop() {
    running = false
    detectors.forEach { it.close() }
  }
}
