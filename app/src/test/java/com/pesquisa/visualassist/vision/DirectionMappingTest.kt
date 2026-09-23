package com.pesquisa.visualassist.vision

import org.junit.Assert.assertEquals
import org.junit.Test

/** Testes da lógica pura de direção (DirectionMapper). Requisito 2.3. */
class DirectionMappingTest {

  @Test
  fun objectOnLeft_isLeft() {
    assertEquals(Direction.LEFT, DirectionMapper.fromCenterX(0.1f))
  }

  @Test
  fun objectInCenter_isCenter() {
    assertEquals(Direction.CENTER, DirectionMapper.fromCenterX(0.5f))
  }

  @Test
  fun objectOnRight_isRight() {
    assertEquals(Direction.RIGHT, DirectionMapper.fromCenterX(0.9f))
  }

  @Test
  fun fromBox_usesCenterAndImageWidth() {
    // box de 100 a 300 num frame de 1280 -> centro 200/1280 = 0.156 -> LEFT
    assertEquals(Direction.LEFT, DirectionMapper.fromBox(100f, 300f, 1280))
    // box central
    assertEquals(Direction.CENTER, DirectionMapper.fromBox(560f, 720f, 1280))
    // box à direita
    assertEquals(Direction.RIGHT, DirectionMapper.fromBox(1000f, 1200f, 1280))
  }

  @Test
  fun fromBox_zeroWidthIsCenter() {
    assertEquals(Direction.CENTER, DirectionMapper.fromBox(10f, 20f, 0))
  }
}
