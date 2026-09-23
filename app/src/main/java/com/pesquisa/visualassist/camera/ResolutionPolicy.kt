package com.pesquisa.visualassist.camera

/**
 * Política pura de seleção de resolução (Requisito 1.4).
 * Independe de classes Android para ser testável em unit tests na JVM.
 *
 * Regra: preferir a resolução testada; senão a maior com o mesmo aspecto
 * (4:3 por padrão); senão a maior disponível.
 */
object ResolutionPolicy {

  data class Res(val width: Int, val height: Int)

  val PREFERRED = Res(1280, 960)

  fun choose(
    supported: List<Res>,
    preferred: Res = PREFERRED,
  ): Res {
    if (supported.isEmpty()) return preferred
    supported.firstOrNull { it.width == preferred.width && it.height == preferred.height }
      ?.let { return it }

    val aspectW = preferred.width
    val aspectH = preferred.height
    supported
      .filter { it.height != 0 && it.width * aspectH == it.height * aspectW }
      .maxByOrNull { it.width.toLong() * it.height }
      ?.let { return it }

    return supported.maxByOrNull { it.width.toLong() * it.height } ?: preferred
  }
}
