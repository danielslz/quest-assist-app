package com.pesquisa.visualassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pesquisa.visualassist.audio.Verbosity
import com.pesquisa.visualassist.settings.Settings
import com.pesquisa.visualassist.settings.SettingsStore
import com.pesquisa.visualassist.vision.DebugFrameState
import com.pesquisa.visualassist.vision.OverlayRenderer

/**
 * Painel de debug + configurações (Tarefas 5.2/5.3/7).
 * Mostra a câmera com bounding boxes (preview fluido) e controles: verbosidade,
 * limiar de confiança, ligar/desligar detecção, e ações (ler texto, fechar).
 */
@Composable
fun DebugPanel(
  settings: SettingsStore,
  onReadText: () -> Unit = {},
  onClose: () -> Unit = {},
) {
  val camera = DebugFrameState.cameraFrame
  val detections = DebugFrameState.detections
  val s = settings.current

  val composed = remember(camera, detections) {
    camera?.let { OverlayRenderer.draw(it, detections) }
  }

  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Preview da câmera
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

    // --- Configurações (Tarefa 7) ---
    // Verbosidade
    Text("Verbosidade", color = Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Verbosity.values().forEach { v ->
        val selected = s.verbosity == v
        Button(
          onClick = { settings.update { it.copy(verbosity = v) } },
          colors = if (selected) ButtonDefaults.buttonColors()
                   else ButtonDefaults.outlinedButtonColors(),
        ) { Text(v.name) }
      }
    }

    // Limiar de confiança
    Text("Confiança mínima: ${(s.confidenceThreshold * 100).toInt()}%", color = Color.White)
    Slider(
      value = s.confidenceThreshold,
      onValueChange = { settings.update { st -> st.withConfidence(it) } },
      valueRange = Settings.MIN_CONFIDENCE..Settings.MAX_CONFIDENCE,
      modifier = Modifier.fillMaxWidth(),
    )

    // Toggle detecção de objetos
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Detecção de objetos", color = Color.White)
      Switch(
        checked = s.objectDetectionEnabled,
        onCheckedChange = { on -> settings.update { it.copy(objectDetectionEnabled = on) } },
      )
    }

    // Ações
    Button(onClick = onReadText, modifier = Modifier.fillMaxWidth()) { Text("Ler texto") }
    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fechar app") }
  }
}
