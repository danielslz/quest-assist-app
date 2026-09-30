package com.pesquisa.visualassist.vision

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado observável do painel de debug. Separa o **frame da câmera** (atualizado
 * de forma fluida, ~15 fps) das **detecções** (atualizadas no ritmo da inferência,
 * ~0.8 fps). O painel compõe os dois → preview fluido com caixas que "seguem".
 */
object DebugFrameState {
  /** Último frame da câmera (RGB), para preview fluido. */
  var cameraFrame by mutableStateOf<Bitmap?>(null)
    private set

  /** Últimas detecções (normalizadas [0..1]), sobrepostas no preview. */
  var detections by mutableStateOf<List<Detection>>(emptyList())
    private set

  fun updateCamera(bitmap: Bitmap) {
    cameraFrame = bitmap
  }

  fun updateDetections(list: List<Detection>) {
    detections = list
  }
}
