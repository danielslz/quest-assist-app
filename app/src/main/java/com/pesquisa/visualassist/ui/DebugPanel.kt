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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pesquisa.visualassist.audio.Verbosity
import com.pesquisa.visualassist.settings.Settings
import com.pesquisa.visualassist.settings.SettingsStore
import com.pesquisa.visualassist.vision.DebugFrameState
import com.pesquisa.visualassist.vision.OverlayRenderer

/**
 * Painel de debug + configurações. Design limpo: por padrão mostra o preview
 * grande e uma barra inferior enxuta (Ler texto · Ajustes · Fechar). Os controles
 * de configuração e de posição/tamanho ficam ocultos atrás de "Ajustes".
 */
@Composable
fun DebugPanel(
  settings: SettingsStore,
  onReadText: () -> Unit = {},
  onDescribeScene: () -> Unit = {},
  onClose: () -> Unit = {},
  onNearer: () -> Unit = {},
  onFarther: () -> Unit = {},
  onBigger: () -> Unit = {},
  onSmaller: () -> Unit = {},
  onLeft: () -> Unit = {},
  onRight: () -> Unit = {},
  onUp: () -> Unit = {},
  onDown: () -> Unit = {},
  onCenter: () -> Unit = {},
) {
  val camera = DebugFrameState.cameraFrame
  val detections = DebugFrameState.detections
  val s = settings.current
  var showSettings by remember { mutableStateOf(false) }

  val composed = remember(camera, detections) {
    camera?.let { OverlayRenderer.draw(it, detections) }
  }
  val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)

  Column(
    modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(6.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // --- PREVIEW (ocupa o espaço restante) ---
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

    // --- Painel de AJUSTES (oculto por padrão) ---
    if (showSettings) {
      Column(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        // Verbosidade + detecção
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Voz:", color = Color.White, fontSize = 12.sp)
          Verbosity.values().forEach { v ->
            val sel = s.verbosity == v
            Button(
              onClick = { settings.update { it.copy(verbosity = v) } },
              contentPadding = pad,
              colors = if (sel) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
            ) { Text(v.name.take(3), fontSize = 11.sp) }
          }
          Text("  Detecção", color = Color.White, fontSize = 12.sp)
          Switch(checked = s.objectDetectionEnabled, onCheckedChange = { on -> settings.update { it.copy(objectDetectionEnabled = on) } })
          Text("  Nuvem", color = Color.White, fontSize = 12.sp)
          Switch(checked = s.cloudEnabled, onCheckedChange = { on -> settings.update { it.copy(cloudEnabled = on) } })
        }
        // Confiança
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Confiança: ${(s.confidenceThreshold * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
          Slider(
            value = s.confidenceThreshold,
            onValueChange = { settings.update { st -> st.withConfidence(it) } },
            valueRange = Settings.MIN_CONFIDENCE..Settings.MAX_CONFIDENCE,
            modifier = Modifier.weight(1f),
          )
        }
        // Mover
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Mover:", color = Color.White, fontSize = 12.sp)
          Button(onClick = onLeft, modifier = Modifier.weight(1f), contentPadding = pad) { Text("←", fontSize = 14.sp) }
          Button(onClick = onRight, modifier = Modifier.weight(1f), contentPadding = pad) { Text("→", fontSize = 14.sp) }
          Button(onClick = onUp, modifier = Modifier.weight(1f), contentPadding = pad) { Text("↑", fontSize = 14.sp) }
          Button(onClick = onDown, modifier = Modifier.weight(1f), contentPadding = pad) { Text("↓", fontSize = 14.sp) }
          Button(onClick = onCenter, modifier = Modifier.weight(1.4f), contentPadding = pad) { Text("Centro", fontSize = 11.sp) }
        }
        // Distância + tamanho
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Painel:", color = Color.White, fontSize = 12.sp)
          Button(onClick = onNearer, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Perto", fontSize = 12.sp) }
          Button(onClick = onFarther, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Longe", fontSize = 12.sp) }
          Button(onClick = onSmaller, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Menor", fontSize = 12.sp) }
          Button(onClick = onBigger, modifier = Modifier.weight(1f), contentPadding = pad) { Text("Maior", fontSize = 12.sp) }
        }
      }
    }

    // --- Barra inferior enxuta (sempre visível) ---
    Row(
      modifier = Modifier.fillMaxWidth().wrapContentHeight(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(onClick = onReadText, modifier = Modifier.weight(1f), contentPadding = pad) {
        Text("Ler texto", fontSize = 13.sp)
      }
      Button(onClick = onDescribeScene, modifier = Modifier.weight(1f), contentPadding = pad) {
        Text("Descrever", fontSize = 13.sp)
      }
      Button(onClick = { showSettings = !showSettings }, modifier = Modifier.weight(1f), contentPadding = pad) {
        Text(if (showSettings) "Ocultar" else "Ajustes", fontSize = 13.sp)
      }
      Button(onClick = onClose, modifier = Modifier.weight(1f), contentPadding = pad) {
        Text("Fechar", fontSize = 13.sp)
      }
    }
  }
}
