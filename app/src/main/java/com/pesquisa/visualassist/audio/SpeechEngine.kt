package com.pesquisa.visualassist.audio

/**
 * Abstração do motor de síntese de voz. Permite trocar a implementação
 * (sherpa-onnx offline, ou um mock nos testes) sem alterar o AudioFeedbackManager.
 *
 * Requisito 5.1 — TTS offline embarcado (o Quest não tem TTS do sistema).
 */
interface SpeechEngine {
  /** Indica se o motor está pronto para sintetizar. */
  val isReady: Boolean

  /**
   * Callback invocado quando o motor termina de inicializar e fica pronto.
   * Usado pelo AudioFeedbackManager para reprocessar anúncios enfileirados
   * antes do engine estar pronto (evita perder o primeiro anúncio).
   */
  var onReady: (() -> Unit)?

  /**
   * Sintetiza e reproduz [text]. [pan] em [-1..1] (esquerda..direita) para
   * pista direcional. Chama [onDone] ao terminar (para a fila serial).
   */
  fun speak(text: String, pan: Float, onDone: () -> Unit)

  /** Interrompe a fala atual. */
  fun stop()

  /** Libera recursos nativos. */
  fun shutdown()
}
