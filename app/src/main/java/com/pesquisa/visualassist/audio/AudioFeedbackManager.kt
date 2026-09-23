package com.pesquisa.visualassist.audio

import com.pesquisa.visualassist.vision.Detection
import com.pesquisa.visualassist.vision.Direction
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gerencia o feedback por áudio (Requisitos 5.1-5.4).
 *
 * Usa um [SpeechEngine] (TTS offline sherpa-onnx) — o Quest não tem TTS do sistema.
 * A fila (`AnnouncementQueue`) com prioridade/debounce/verbosidade é agnóstica ao
 * motor e permanece testável em unit tests.
 */
class AudioFeedbackManager(
  private val engine: SpeechEngine,
  debounceMs: Long = 3000L,
) {
  private val queue = AnnouncementQueue(debounceMs)
  private val speaking = AtomicBoolean(false)

  /** Beep direcional opcional via Spatial SDK (task 4.2). */
  var spatialCue: ((AudioDirection) -> Unit)? = null

  init {
    // Quando o engine terminar de inicializar, consome anúncios pendentes
    // (ex.: "Assistente visual iniciado" enfileirado antes de o TTS ficar pronto).
    engine.onReady = { pump() }
  }

  fun setVerbosity(v: Verbosity) = queue.setVerbosity(v)

  /** Fala uma mensagem livre (ex.: permissão). Prioridade alta, sem expirar. */
  fun announce(message: String, priority: AudioPriority = AudioPriority.HIGH) {
    enqueue(Announcement(message, priority = priority, ttlMs = null))
  }

  /** Converte detecções em anúncios, priorizando relevância (Requisitos 2.3, 4.1). */
  fun report(detections: List<Detection>) {
    if (detections.isEmpty()) return

    // Relevância = combina confiança, proximidade (área da box) e centralidade.
    fun relevance(d: Detection): Float {
      val w = (d.box[2] - d.box[0]).coerceAtLeast(0f)
      val h = (d.box[3] - d.box[1]).coerceAtLeast(0f)
      val area = w * h // 0..1: objetos maiores/mais próximos pesam mais
      val cx = (d.box[0] + d.box[2]) / 2f
      val centrality = 1f - (kotlin.math.abs(cx - 0.5f) * 2f) // 1 no centro, 0 nas bordas
      return d.confidence * 0.5f + area * 0.3f + centrality * 0.2f
    }

    // Pessoas são sempre relevantes (segurança/social); depois, os objetos mais relevantes.
    val people = detections.filter { it.kind == Detection.Kind.PERSON }
    val objects = detections.filter { it.kind != Detection.Kind.PERSON }
      .sortedByDescending { relevance(it) }
      .take(1) // no máximo 1 objeto por ciclo (fala menos)

    (people + objects).forEach { d ->
      enqueue(
        Announcement(
          text = phraseFor(d),
          priority = if (d.kind == Detection.Kind.PERSON) AudioPriority.HIGH else AudioPriority.NORMAL,
          direction = d.direction.toAudioDirection(),
          dedupeKey = d.label,
          minVerbosity = if (d.kind == Detection.Kind.OBJECT) Verbosity.NORMAL else Verbosity.MINIMAL,
          ttlMs = 1800L, // menor atraso: detecção obsoleta após 1.8s não é falada
        )
      )
    }
  }

  private fun enqueue(a: Announcement) {
    if (queue.offer(a)) pump()
  }

  /** Consome a fila de forma serial (uma fala por vez). */
  private fun pump() {
    if (!engine.isReady) return
    if (!speaking.compareAndSet(false, true)) return
    val a = queue.poll()
    if (a == null) {
      speaking.set(false)
      return
    }
    if (a.direction != AudioDirection.CENTER) spatialCue?.invoke(a.direction)
    val pan = when (a.direction) {
      AudioDirection.LEFT -> -1.0f
      AudioDirection.RIGHT -> 1.0f
      AudioDirection.CENTER -> 0.0f
    }
    engine.speak(a.text, pan) {
      speaking.set(false)
      pump() // próximo item
    }
  }

  private fun phraseFor(d: Detection): String {
    val dir = when (d.direction) {
      Direction.LEFT -> "à sua esquerda"
      Direction.RIGHT -> "à sua direita"
      Direction.CENTER -> "à sua frente"
    }
    return when (d.kind) {
      Detection.Kind.OBJECT -> "${d.label} $dir"
      Detection.Kind.PERSON -> "Pessoa $dir"
      Detection.Kind.TEXT -> d.label
    }
  }

  fun shutdown() {
    queue.clear()
    engine.shutdown()
  }
}

private fun Direction.toAudioDirection(): AudioDirection = when (this) {
  Direction.LEFT -> AudioDirection.LEFT
  Direction.RIGHT -> AudioDirection.RIGHT
  Direction.CENTER -> AudioDirection.CENTER
}
