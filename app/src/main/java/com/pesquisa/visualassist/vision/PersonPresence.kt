package com.pesquisa.visualassist.vision

/** Anúncio de presença de pessoas: frase + direção (para o pan do áudio). */
data class PersonAnnouncement(val text: String, val direction: Direction)

/**
 * Resume a PRESENÇA de pessoas detectadas e decide QUANDO falar. Lógica pura
 * (sem Android), testável em JVM.
 *
 * Privacidade (Requisitos 4.2, 4.3, 7): só conta pessoas e estima posição e
 * proximidade a partir das caixas. Não identifica ninguém e não guarda imagens.
 *
 * Regras de fala (evitam tagarelice sem atrasar o que é urgente):
 *  - Primeira aparição: fala imediatamente.
 *  - Pessoa que passa a estar PRÓXIMA (ou muda o lado do mais próximo): imediato.
 *  - Demais mudanças (contagem/lados): só após [confirmCycles] ciclos seguidos
 *    com o mesmo resumo, para ignorar o piscar da detecção.
 *  - Estado inalterado: silêncio (a ação "repetir" cobre quem quer ouvir de novo).
 *  - Sem ver ninguém por mais de [staleMs]: esquece o estado, e uma nova
 *    aparição volta a ser anunciada. Necessário porque o pipeline só chama o
 *    áudio quando há alguma detecção, então a ausência não chega como evento.
 *
 * A proximidade é uma heurística pela altura da caixa (fração da altura do
 * frame), NÃO calibrada: [nearHeight] precisa de validação no device.
 */
class PersonPresenceTracker(
  private val nearHeight: Float = NEAR_HEIGHT,
  private val staleMs: Long = STALE_MS,
  private val confirmCycles: Int = CONFIRM_CYCLES,
) {

  /** Contagem por lado e, se houver, o lado da pessoa mais próxima. */
  data class Summary(
    val left: Int,
    val center: Int,
    val right: Int,
    val nearDirection: Direction?,
  ) {
    val total: Int get() = left + center + right

    fun count(d: Direction): Int = when (d) {
      Direction.LEFT -> left
      Direction.CENTER -> center
      Direction.RIGHT -> right
    }
  }

  private var announced: Summary? = null
  private var candidate: Summary? = null
  private var candidateCycles = 0
  private var lastSeenMs: Long? = null

  /**
   * Processa as pessoas de um ciclo de detecção.
   * @param people detecções de pessoas (outros tipos são ignorados).
   * @param nowMs relógio atual em ms.
   * @return o anúncio a falar, ou null se não há nada novo.
   */
  fun update(people: List<Detection>, nowMs: Long): PersonAnnouncement? {
    val seen = lastSeenMs
    if (seen != null && nowMs - seen > staleMs) {
      announced = null
      candidate = null
      candidateCycles = 0
    }

    val current = summarize(people) ?: run {
      candidate = null
      candidateCycles = 0
      return null
    }
    lastSeenMs = nowMs

    val previous = announced
    if (current == previous) {
      candidate = null
      candidateCycles = 0
      return null
    }

    val becameNear = current.nearDirection != null &&
      current.nearDirection != previous?.nearDirection
    if (previous == null || becameNear) return announce(current)

    if (current == candidate) candidateCycles++ else {
      candidate = current
      candidateCycles = 1
    }
    return if (candidateCycles >= confirmCycles) announce(current) else null
  }

  private fun announce(s: Summary): PersonAnnouncement {
    announced = s
    candidate = null
    candidateCycles = 0
    return PersonAnnouncement(phrase(s), panDirection(s))
  }

  private fun summarize(people: List<Detection>): Summary? {
    val persons = people.filter { it.kind == Detection.Kind.PERSON }
    if (persons.isEmpty()) return null

    fun height(d: Detection): Float =
      if (d.box.size >= 4) (d.box[3] - d.box[1]).coerceAtLeast(0f) else 0f

    val nearest = persons
      .filter { height(it) >= nearHeight }
      .maxByOrNull { height(it) }

    return Summary(
      left = persons.count { it.direction == Direction.LEFT },
      center = persons.count { it.direction == Direction.CENTER },
      right = persons.count { it.direction == Direction.RIGHT },
      nearDirection = nearest?.direction,
    )
  }

  /** Lado para o pan: o da pessoa próxima; senão o único lado ocupado; senão frente. */
  private fun panDirection(s: Summary): Direction {
    s.nearDirection?.let { return it }
    val occupied = Direction.values().filter { s.count(it) > 0 }
    return if (occupied.size == 1) occupied[0] else Direction.CENTER
  }

  companion object {
    /** Altura da caixa (fração do frame) a partir da qual a pessoa é "próxima". */
    const val NEAR_HEIGHT = 0.6f
    const val STALE_MS = 4000L
    const val CONFIRM_CYCLES = 2

    /** Frase em português para o resumo. Função pura. */
    fun phrase(s: Summary): String {
      val near = s.nearDirection
      if (s.total == 1) {
        val only = Direction.values().first { s.count(it) > 0 }
        return if (near != null) "Pessoa próxima ${fullDir(only)}" else "Pessoa ${fullDir(only)}"
      }

      val head = countWord(s.total).replaceFirstChar { it.uppercase() } + " pessoas"
      val occupied = Direction.values().filter { s.count(it) > 0 }
      val body = if (occupied.size == 1) {
        "$head ${fullDir(occupied[0])}"
      } else {
        val parts = occupied.map { "${countWord(s.count(it))} ${shortDir(it)}" }
        "$head: ${joinNatural(parts)}"
      }
      return if (near != null) "$body. Atenção, pessoa próxima ${fullDir(near)}" else body
    }

    private fun fullDir(d: Direction) = when (d) {
      Direction.LEFT -> "à sua esquerda"
      Direction.CENTER -> "à sua frente"
      Direction.RIGHT -> "à sua direita"
    }

    private fun shortDir(d: Direction) = when (d) {
      Direction.LEFT -> "à esquerda"
      Direction.CENTER -> "à frente"
      Direction.RIGHT -> "à direita"
    }

    /** Numerais no feminino ("pessoas"); acima de 5 usa dígitos. */
    private fun countWord(n: Int) = when (n) {
      1 -> "uma"
      2 -> "duas"
      3 -> "três"
      4 -> "quatro"
      5 -> "cinco"
      else -> n.toString()
    }

    private fun joinNatural(parts: List<String>): String = when (parts.size) {
      0 -> ""
      1 -> parts[0]
      else -> parts.dropLast(1).joinToString(", ") + " e " + parts.last()
    }
  }
}
