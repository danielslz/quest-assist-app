package com.pesquisa.visualassist.cloud

import com.pesquisa.visualassist.vision.Detection
import com.pesquisa.visualassist.vision.Direction

/**
 * Fallback offline (Requisito 6.2): monta uma descrição simples da cena a partir
 * das detecções on-device, quando a nuvem está indisponível ou desligada.
 * Função pura — testável.
 */
object OnDeviceSceneDescriber {

  fun describe(detections: List<Detection>): String {
    if (detections.isEmpty()) return "Não identifiquei objetos à sua frente."

    // Agrupa por rótulo + direção, conta ocorrências, e monta frases curtas.
    val byLabel = detections.groupBy { it.label }
    val partes = byLabel.entries.take(4).map { (label, items) ->
      val dir = items.first().direction.toText()
      if (items.size > 1) "${items.size} ${label}s $dir" else "$label $dir"
    }
    return "À sua volta: " + partes.joinToString("; ") + "."
  }

  private fun Direction.toText(): String = when (this) {
    Direction.LEFT -> "à esquerda"
    Direction.RIGHT -> "à direita"
    Direction.CENTER -> "à frente"
  }
}
