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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pesquisa.visualassist.vision.DebugFrameState

/**
 * Painel de debug (Opção A): imagem da câmera de passthrough com as bounding
 * boxes das detecções + botão para fechar o app (útil em desenvolvimento).
 *
 * @param onClose chamado quando o usuário toca em "Fechar app".
 */
@Composable
fun DebugPanel(onClose: () -> Unit = {}) {
  val frame = DebugFrameState.annotated
  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
      Text("Fechar app")
    }
  }
}
