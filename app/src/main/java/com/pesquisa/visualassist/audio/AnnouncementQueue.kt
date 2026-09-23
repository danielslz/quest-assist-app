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
  /**
   * Tempo de vida (ms). Se o anúncio ficar na fila mais que isto sem ser falado,
   * é descartado no poll (evita falar detecções obsoletas). null = nunca expira
   * (use para mensagens de sistema como permissões).
   */
  val ttlMs: Long? = 2500L,
)

/**
 * Fila de anúncios pura (sem dependências Android) — testável em unit tests.
 *
 * Resolve o descompasso detector-rápido/fala-lenta (Requisitos 2.4, 5.4):
 *  - **TTL**: anúncios obsoletos são descartados no poll — só se fala o recente.
 *  - **Debounce** por `dedupeKey`: não repete o mesmo rótulo por uma janela longa.
 *  - **Sem duplicatas pendentes** e **fila curta** (maxQueue): evita acúmulo.
 *  - Prioridade (HIGH fura a fila), ordem estável dentro da mesma prioridade.
 *
 * O tempo é injetado (clock) para testabilidade determinística.
 */
class AnnouncementQueue(
  private val debounceMs: Long = 5000L,
  private val clock: () -> Long = { System.currentTimeMillis() },
  private val maxQueue: Int = 3,
) {
  private data class Entry(val a: Announcement, val createdAt: Long, val seq: Long)

  private val pending = ArrayDeque<Entry>()
  private val lastSeenAt = HashMap<String, Long>()
  private var verbosity: Verbosity = Verbosity.NORMAL
  private var seq = 0L

  fun setVerbosity(v: Verbosity) { verbosity = v }

  /** Enfileira respeitando verbosidade e debounce. Retorna true se aceito. */
  fun offer(a: Announcement): Boolean {
    if (verbosity.ordinal < a.minVerbosity.ordinal) return false

    val now = clock()
    val key = a.dedupeKey
    if (key != null) {
      val last = lastSeenAt[key]
      if (last != null && now - last < debounceMs) return false
      if (pending.any { it.a.dedupeKey == key }) return false
      lastSeenAt[key] = now
    }

    if (pending.size >= maxQueue) {
      // Fila cheia: só entra se tiver prioridade maior que o item mais fraco.
      val weakest = pending.minByOrNull { it.a.priority.ordinal } ?: return false
      if (a.priority.ordinal <= weakest.a.priority.ordinal) return false
      pending.remove(weakest)
    }

    val entry = Entry(a, now, seq++)
    val idx = pending.indexOfFirst { it.a.priority.ordinal < a.priority.ordinal }
    if (idx < 0) pending.addLast(entry) else pending.add(idx, entry)
    return true
  }

  /**
   * Retira o próximo anúncio a falar, descartando os que expiraram (TTL).
   * Retorna null se não há nada recente a falar.
   */
  fun poll(): Announcement? {
    val now = clock()
    while (true) {
      val e = pending.removeFirstOrNull() ?: return null
      val ttl = e.a.ttlMs
      if (ttl != null && now - e.createdAt > ttl) {
        // expirado: descarta e tenta o próximo
        continue
      }
      e.a.dedupeKey?.let { lastSeenAt[it] = now }
      return e.a
    }
  }

  fun isEmpty(): Boolean = pending.isEmpty()
  fun size(): Int = pending.size
  fun clear() { pending.clear() }
}
