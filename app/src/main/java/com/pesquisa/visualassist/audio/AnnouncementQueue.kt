package com.pesquisa.visualassist.audio

/** Prioridade de um anúncio; maior prioridade fura a fila. */
enum class AudioPriority { LOW, NORMAL, HIGH }

/** Nível de verbosidade global (Requisito 5.3). */
enum class Verbosity { MINIMAL, NORMAL, DETAILED }

/** Pista direcional para pan/ posição do áudio. */
enum class AudioDirection { LEFT, CENTER, RIGHT }

/** Um item de fala a ser enunciado. */
data class Announcement(
  val text: String,
  val priority: AudioPriority = AudioPriority.NORMAL,
  val direction: AudioDirection = AudioDirection.CENTER,
  /** Chave para debounce (ex.: rótulo do objeto). Null = sem debounce. */
  val dedupeKey: String? = null,
  /** Só falado se a verbosidade atual for >= este nível. */
  val minVerbosity: Verbosity = Verbosity.MINIMAL,
)

/**
 * Fila de anúncios pura (sem dependências Android) — testável em unit tests.
 *
 * Responsabilidades (Requisitos 2.4, 5.3, 5.4):
 *  - Ordenar por prioridade (HIGH primeiro), mantendo ordem de chegada dentro
 *    da mesma prioridade (estável).
 *  - Debounce por `dedupeKey` dentro de uma janela de tempo.
 *  - Filtrar por verbosidade.
 *
 * O tempo é injetado (clock) para testabilidade determinística.
 */
class AnnouncementQueue(
  private val debounceMs: Long = 3000L,
  private val clock: () -> Long = { System.currentTimeMillis() },
) {
  private val pending = ArrayDeque<Announcement>()
  private val lastSpokenAt = HashMap<String, Long>()
  private var verbosity: Verbosity = Verbosity.NORMAL
  private var seq = 0L
  // (seq para ordenação estável por prioridade)
  private val order = HashMap<Announcement, Long>()

  fun setVerbosity(v: Verbosity) { verbosity = v }

  /** Enfileira respeitando verbosidade e debounce. Retorna true se aceito. */
  fun offer(a: Announcement): Boolean {
    if (verbosity.ordinal < a.minVerbosity.ordinal) return false

    val key = a.dedupeKey
    if (key != null) {
      val last = lastSpokenAt[key]
      if (last != null && clock() - last < debounceMs) return false
    }

    order[a] = seq++
    // Inserção mantendo prioridade (HIGH no início da sua faixa).
    val idx = pending.indexOfFirst { it.priority.ordinal < a.priority.ordinal }
    if (idx < 0) pending.addLast(a) else pending.add(idx, a)
    return true
  }

  /** Retira o próximo anúncio a falar, registrando o debounce. */
  fun poll(): Announcement? {
    val a = pending.removeFirstOrNull() ?: return null
    order.remove(a)
    a.dedupeKey?.let { lastSpokenAt[it] = clock() }
    return a
  }

  fun isEmpty(): Boolean = pending.isEmpty()
  fun size(): Int = pending.size
  fun clear() { pending.clear() }
}
