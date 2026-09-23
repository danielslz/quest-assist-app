package com.pesquisa.visualassist.vision

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes da lógica pura (sem Android): direção a partir da bounding box.
 * Requisitos: 2.3
 *
 * NOTA: a função de mapeamento box->Direction será extraída para uma classe
 * utilitária testável na task 5.2. Este teste documenta o comportamento esperado.
 */
class DirectionMappingTest {

  private fun directionFromCenterX(centerX: Float): Direction = when {
    centerX < 0.33f -> Direction.LEFT
    centerX > 0.66f -> Direction.RIGHT
    else -> Direction.CENTER
  }

  @Test
  fun objectOnLeft_isLeft() {
    assertEquals(Direction.LEFT, directionFromCenterX(0.1f))
  }

  @Test
  fun objectInCenter_isCenter() {
    assertEquals(Direction.CENTER, directionFromCenterX(0.5f))
  }

  @Test
  fun objectOnRight_isRight() {
    assertEquals(Direction.RIGHT, directionFromCenterX(0.9f))
  }
}
