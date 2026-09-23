package com.pesquisa.visualassist.audio

import com.pesquisa.visualassist.vision.Detection
import com.pesquisa.visualassist.vision.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testa o AudioFeedbackManager com um SpeechEngine fake (síncrono),
 * validando que a fila é consumida em série e que detecções viram falas.
 */
class AudioFeedbackManagerTest {

  /** Engine fake: registra o que foi falado e completa imediatamente. */
  private class FakeEngine : SpeechEngine {
    val spoken = mutableListOf<String>()
    override val isReady = true
    override var onReady: (() -> Unit)? = null
    override fun speak(text: String, pan: Float, onDone: () -> Unit) {
      spoken.add(text)
      onDone() // síncrono: dispara o próximo item da fila
    }
    override fun stop() {}
    override fun shutdown() {}
  }

  private fun box() = floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f)

  @Test
  fun announceIsSpoken() {
    val engine = FakeEngine()
    val mgr = AudioFeedbackManager(engine)
    mgr.announce("Assistente visual iniciado.")
    assertEquals(listOf("Assistente visual iniciado."), engine.spoken)
  }

  @Test
  fun reportObjectProducesDirectionalPhrase() {
    val engine = FakeEngine()
    val mgr = AudioFeedbackManager(engine)
    mgr.setVerbosity(Verbosity.NORMAL)
    val det = Detection("cadeira", 0.9f, box(), Direction.LEFT, Detection.Kind.OBJECT)
    mgr.report(listOf(det))
    assertEquals(listOf("cadeira à sua esquerda"), engine.spoken)
  }

  @Test
  fun personReportAnnouncesPresence() {
    val engine = FakeEngine()
    val mgr = AudioFeedbackManager(engine)
    val det = Detection("face", 0.9f, box(), Direction.CENTER, Detection.Kind.PERSON)
    mgr.report(listOf(det))
    assertEquals(listOf("Pessoa à sua frente"), engine.spoken)
  }

  @Test
  fun announceBeforeEngineReady_isSpokenWhenReady() {
    // Engine que começa NÃO pronto; anúncio deve ser falado quando ficar pronto.
    val engine = object : SpeechEngine {
      val spoken = mutableListOf<String>()
      override var isReady = false
      override var onReady: (() -> Unit)? = null
      override fun speak(text: String, pan: Float, onDone: () -> Unit) {
        spoken.add(text); onDone()
      }
      override fun stop() {}
      override fun shutdown() {}
      fun becomeReady() { isReady = true; onReady?.invoke() }
    }
    val mgr = AudioFeedbackManager(engine)
    mgr.announce("Assistente visual iniciado.")
    assertTrue(engine.spoken.isEmpty()) // ainda não pronto
    engine.becomeReady()
    assertEquals(listOf("Assistente visual iniciado."), engine.spoken)
  }
}
