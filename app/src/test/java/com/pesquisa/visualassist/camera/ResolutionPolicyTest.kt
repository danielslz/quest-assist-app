package com.pesquisa.visualassist.camera

import com.pesquisa.visualassist.camera.ResolutionPolicy.Res
import org.junit.Assert.assertEquals
import org.junit.Test

/** Testa a política de seleção de resolução (Requisito 1.4). */
class ResolutionPolicyTest {

  @Test
  fun prefers1280x960WhenAvailable() {
    val sizes = listOf(Res(320, 240), Res(640, 480), Res(1280, 960), Res(1280, 1280))
    assertEquals(Res(1280, 960), ResolutionPolicy.choose(sizes))
  }

  @Test
  fun fallsBackToLargest4x3() {
    val sizes = listOf(Res(320, 240), Res(800, 600), Res(1280, 1280))
    assertEquals(Res(800, 600), ResolutionPolicy.choose(sizes))
  }

  @Test
  fun fallsBackToLargestWhenNo4x3() {
    val sizes = listOf(Res(1000, 1000), Res(1280, 1280))
    assertEquals(Res(1280, 1280), ResolutionPolicy.choose(sizes))
  }

  @Test
  fun emptyReturnsPreferred() {
    assertEquals(ResolutionPolicy.PREFERRED, ResolutionPolicy.choose(emptyList()))
  }
}
