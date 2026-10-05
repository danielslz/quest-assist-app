package com.pesquisa.visualassist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pesquisa.visualassist.audio.Verbosity
import com.pesquisa.visualassist.settings.Settings
import com.pesquisa.visualassist.settings.SettingsStore
import com.pesquisa.visualassist.vision.DebugFrameState
import com.pesquisa.visualassist.vision.OverlayRenderer

/**
 * Painel de debug + configurações. Layout: o preview da câmera domina a área
 * (~80%); os controles ficam numa faixa compacta e rolável embaixo.
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

  // Botão compacto reutilizável
  val smallPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // --- PREVIEW (domina ~80% da altura) ---
    Box(
      modifier = Modifier.fillMaxWidth().weight(4f),
      contentAlignment = Alignment.Center,
    ) {
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

    // --- CONTROLES (faixa compacta ~20%, rolável se precisar) ---
    Column(
      modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      // Linha 1: verbosidade (compacta) + ações
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Voz:", color = Color.White, fontSize = 12.sp)
        Verbosity.values().forEach { v ->
          val selected = s.verbosity == v
          Button(
            onClick = { settings.update { it.copy(verbosity = v) } },
            contentPadding = smallPadding,
            colors = if (selected) ButtonDefaults.buttonColors()
                     else ButtonDefaults.outlinedButtonColors(),
          ) { Text(v.name.take(3), fontSize = 11.sp) } // MIN / NOR / DET
        }
      }

      // Linha 2: limiar de confiança (label + slider na mesma linha)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Conf. ${(s.confidenceThreshold * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
        Slider(
          value = s.confidenceThreshold,
          onValueChange = { settings.update { st -> st.withConfidence(it) } },
          valueRange = Settings.MIN_CONFIDENCE..Settings.MAX_CONFIDENCE,
          modifier = Modifier.weight(1f),
        )
      }

      // Linha 3: toggle detecção + botões de ação (tudo numa linha)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Objetos", color = Color.White, fontSize = 12.sp)
        Switch(
          checked = s.objectDetectionEnabled,
          onCheckedChange = { on -> settings.update { it.copy(objectDetectionEnabled = on) } },
        )
        Button(onClick = onReadText, contentPadding = smallPadding) {
          Text("Ler texto", fontSize = 12.sp)
        }
        Button(onClick = onClose, contentPadding = smallPadding) {
          Text("Fechar", fontSize = 12.sp)
        }
      }
    }
  }
}
