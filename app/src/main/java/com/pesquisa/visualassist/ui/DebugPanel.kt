package com.pesquisa.visualassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pesquisa.visualassist.vision.DebugFrameState
import com.pesquisa.visualassist.vision.OverlayRenderer

/**
 * Painel de debug (Opção A): imagem da câmera de passthrough (fluida) com as
 * bounding boxes das últimas detecções sobrepostas + botões "Ler texto" e "Fechar".
 *
 * O preview da câmera atualiza ~15 fps; as caixas atualizam no ritmo da inferência
 * (~0.8 fps). Compor as duas dá um preview fluido com caixas que "seguem".
 */
@Composable
fun DebugPanel(onReadText: () -> Unit = {}, onClose: () -> Unit = {}) {
  val camera = DebugFrameState.cameraFrame
  val detections = DebugFrameState.detections

  // Compõe câmera + boxes. Recalcula quando o frame ou as detecções mudam.
  val composed = remember(camera, detections) {
    camera?.let { OverlayRenderer.draw(it, detections) }
  }

  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
      if (composed != null) {
        Image(
          bitmap = composed.asImageBitmap(),
          contentDescription = "Câmera com detecções",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
        )
      } else {
        Text("Aguardando câmera...", color = Color.White)
      }
    }
    Button(onClick = onReadText, modifier = Modifier.fillMaxWidth()) {
      Text("Ler texto")
    }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
      Text("Fechar app")
    }
  }
}
