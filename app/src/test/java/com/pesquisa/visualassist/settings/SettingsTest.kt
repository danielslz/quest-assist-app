package com.pesquisa.visualassist.settings

import com.pesquisa.visualassist.audio.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

  @Test
  fun defaultsAreSafe() {
    val s = Settings()
    assertEquals(Verbosity.NORMAL, s.verbosity)
    assertEquals(0.5f, s.confidenceThreshold, 0.0001f)
    assertEquals(true, s.objectDetectionEnabled)
    assertEquals(false, s.cloudEnabled) // offline por padrão (Req 7.4)
  }

  @Test
  fun clampConfidenceRespectsBounds() {
    assertEquals(Settings.MIN_CONFIDENCE, Settings.clampConfidence(0.01f), 0.0001f)
    assertEquals(Settings.MAX_CONFIDENCE, Settings.clampConfidence(0.99f), 0.0001f)
    assertEquals(0.6f, Settings.clampConfidence(0.6f), 0.0001f)
  }

  @Test
  fun withConfidenceClamps() {
    val s = Settings().withConfidence(2.0f)
    assertEquals(Settings.MAX_CONFIDENCE, s.confidenceThreshold, 0.0001f)
  }
}
