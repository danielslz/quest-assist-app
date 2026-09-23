package com.pesquisa.visualassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pesquisa.visualassist.vision.DebugFrameState

/**
 * Painel de debug (Opção A): mostra a imagem da câmera de passthrough com
 * as bounding boxes das detecções desenhadas por cima. Útil para validação
 * visual (o app é para deficientes visuais; este painel é ferramenta de dev).
 */
@Composable
fun DebugPanel() {
  val frame = DebugFrameState.annotated
  Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
    if (frame != null) {
      Image(
        bitmap = frame.asImageBitmap(),
        contentDescription = "Câmera com detecções",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
      )
    } else {
      Text("Aguardando câmera...", color = Color.White)
    }
  }
}
