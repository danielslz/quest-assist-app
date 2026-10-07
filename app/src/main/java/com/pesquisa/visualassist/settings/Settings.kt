package com.pesquisa.visualassist.settings

import com.pesquisa.visualassist.audio.Verbosity

/**
 * Configurações do app (valores + validação). Classe pura/imutável, testável
 * sem Android. A persistência fica no [SettingsStore].
 *
 * Requisitos: 5.3 (verbosidade), 6.3 (cloud on/off), 7 (offline por padrão).
 */
data class Settings(
  /** Verbosidade dos anúncios de áudio. */
  val verbosity: Verbosity = Verbosity.NORMAL,
  /** Limiar de confiança da detecção de objetos [0.1 .. 0.9]. */
  val confidenceThreshold: Float = 0.5f,
  /** Detecção contínua de objetos ligada. */
  val objectDetectionEnabled: Boolean = true,
  /** Modo cloud (descrição de cena via LLM) — OFF por padrão (Requisito 7.4). */
  val cloudEnabled: Boolean = false,
  /** Endpoint do LLM multimodal (OpenAI-compatible). Vazio = não configurado. */
  val cloudEndpoint: String = "",
  /** Nome do modelo multimodal no endpoint. */
  val cloudModel: String = "llava",
) {
  companion object {
    const val MIN_CONFIDENCE = 0.1f
    const val MAX_CONFIDENCE = 0.9f

    /** Garante o limiar dentro da faixa válida. */
    fun clampConfidence(value: Float): Float =
      value.coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
  }

  /** Retorna uma cópia com o limiar validado. */
  fun withConfidence(value: Float): Settings =
    copy(confidenceThreshold = clampConfidence(value))
}
