package com.pesquisa.visualassist.vision

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado observável com o último frame anotado (câmera + bounding boxes),
 * consumido pelo painel de debug (Opção A). Singleton simples de processo.
 */
object DebugFrameState {
  var annotated by mutableStateOf<Bitmap?>(null)
    private set

  fun update(bitmap: Bitmap) {
    annotated = bitmap
  }
}
