package com.pesquisa.visualassist.vision

/**
 * Deriva a direção relativa (esquerda/centro/direita) a partir do centro
 * horizontal de uma bounding box normalizada. Função pura e testável.
 * Requisito 2.3.
 */
object DirectionMapper {
  fun fromCenterX(centerX: Float): Direction = when {
    centerX < 0.33f -> Direction.LEFT
    centerX > 0.66f -> Direction.RIGHT
    else -> Direction.CENTER
  }

  /** boxLeft/boxRight em pixels; imageWidth = largura do frame. */
  fun fromBox(boxLeft: Float, boxRight: Float, imageWidth: Int): Direction {
    if (imageWidth <= 0) return Direction.CENTER
    val centerX = ((boxLeft + boxRight) / 2f) / imageWidth
    return fromCenterX(centerX)
  }
}
