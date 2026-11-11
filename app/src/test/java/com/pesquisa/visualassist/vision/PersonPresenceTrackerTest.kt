package com.pesquisa.visualassist.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PersonPresenceTrackerTest {

  private val tracker = PersonPresenceTracker() // nearHeight 0.6, stale 4s, confirma 2 ciclos

  /** Pessoa na direção dada; [h] = altura da caixa (fração do frame, top = 0 → altura exata). */
  private fun person(dir: Direction, h: Float = 0.3f) = Detection(
    label = "pessoa",
    confidence = 0.9f,
    box = floatArrayOf(0.4f, 0f, 0.6f, h),
    direction = dir,
    kind = Detection.Kind.PERSON,
  )

  private fun obj(dir: Direction) = Detection(
    label = "cadeira", confidence = 0.9f, box = floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f),
    direction = dir, kind = Detection.Kind.OBJECT,
  )

  @Test fun semPessoas_naoFala() {
    assertNull(tracker.update(emptyList(), 0))
    assertNull(tracker.update(listOf(obj(Direction.CENTER)), 1000)) // objeto não conta
  }

  @Test fun primeiraAparicao_falaImediatamente() {
    val a = tracker.update(listOf(person(Direction.CENTER)), 0)
    assertEquals("Pessoa à sua frente", a?.text)
    assertEquals(Direction.CENTER, a?.direction)
  }

  @Test fun estadoInalterado_ficaEmSilencio() {
    tracker.update(listOf(person(Direction.LEFT)), 0)
    assertNull(tracker.update(listOf(person(Direction.LEFT)), 1200))
    assertNull(tracker.update(listOf(person(Direction.LEFT)), 2400))
  }

  @Test fun mudancaExigeConfirmacao_dosCiclos() {
    tracker.update(listOf(person(Direction.CENTER)), 0)
    val duas = listOf(person(Direction.LEFT), person(Direction.RIGHT))
    assertNull(tracker.update(duas, 1200)) // 1º ciclo: ainda não confirma
    val a = tracker.update(duas, 2400) // 2º ciclo igual: confirma
    assertEquals("Duas pessoas: uma à esquerda e uma à direita", a?.text)
  }

  @Test fun piscadaDeDeteccao_naoGeraAnuncio() {
    tracker.update(listOf(person(Direction.CENTER)), 0)
    val duas = listOf(person(Direction.CENTER), person(Direction.LEFT))
    assertNull(tracker.update(duas, 1200)) // aparece só 1 ciclo
    assertNull(tracker.update(listOf(person(Direction.CENTER)), 2400)) // volta ao anunciado
    assertNull(tracker.update(duas, 3600)) // recomeça a contagem, sem confirmar
  }

  @Test fun pessoaProxima_falaImediatamente() {
    tracker.update(listOf(person(Direction.CENTER, h = 0.3f)), 0) // longe
    val a = tracker.update(listOf(person(Direction.CENTER, h = 0.8f)), 1200)
    assertEquals("Pessoa próxima à sua frente", a?.text)
  }

  @Test fun pessoaProxima_primeiraAparicao() {
    val a = tracker.update(listOf(person(Direction.RIGHT, h = 0.9f)), 0)
    assertEquals("Pessoa próxima à sua direita", a?.text)
    assertEquals(Direction.RIGHT, a?.direction)
  }

  @Test fun limiteDeProximidade_ehInclusivo() {
    val a = tracker.update(listOf(person(Direction.CENTER, h = PersonPresenceTracker.NEAR_HEIGHT)), 0)
    assertEquals("Pessoa próxima à sua frente", a?.text)
  }

  @Test fun variasNoMesmoLado() {
    val a = tracker.update(listOf(person(Direction.CENTER), person(Direction.CENTER)), 0)
    assertEquals("Duas pessoas à sua frente", a?.text)
  }

  @Test fun tresLados() {
    val a = tracker.update(
      listOf(person(Direction.LEFT), person(Direction.CENTER), person(Direction.RIGHT)), 0,
    )
    assertEquals("Três pessoas: uma à esquerda, uma à frente e uma à direita", a?.text)
  }

  @Test fun maisDeCincoUsaDigitos() {
    val seis = List(6) { person(Direction.CENTER) }
    assertEquals("6 pessoas à sua frente", tracker.update(seis, 0)?.text)
  }

  @Test fun varias_comUmaProxima_acrescentaAlerta() {
    val a = tracker.update(
      listOf(person(Direction.LEFT, h = 0.3f), person(Direction.RIGHT, h = 0.8f)), 0,
    )
    assertEquals(
      "Duas pessoas: uma à esquerda e uma à direita. Atenção, pessoa próxima à sua direita",
      a?.text,
    )
    assertEquals(Direction.RIGHT, a?.direction) // pan na pessoa próxima
  }

  @Test fun semVerPessoaPorMuitoTempo_esqueceEReanuncia() {
    assertNotNull(tracker.update(listOf(person(Direction.CENTER)), 0))
    assertNull(tracker.update(emptyList(), 2000)) // ausência curta: lembra
    // Ficou > 4 s sem ver ninguém: estado esquecido, a nova aparição é anunciada.
    val a = tracker.update(listOf(person(Direction.CENTER)), 10_000)
    assertEquals("Pessoa à sua frente", a?.text)
  }

  @Test fun ausenciaCurta_naoReanuncia() {
    tracker.update(listOf(person(Direction.CENTER)), 0)
    tracker.update(emptyList(), 1200)
    assertNull(tracker.update(listOf(person(Direction.CENTER)), 2400))
  }

  @Test fun caixaSemCoordenadas_naoQuebra() {
    val d = Detection("pessoa", 0.9f, floatArrayOf(), Direction.CENTER, Detection.Kind.PERSON)
    assertEquals("Pessoa à sua frente", tracker.update(listOf(d), 0)?.text)
  }

  @Test fun panDoUnicoLadoOcupado() {
    val a = tracker.update(listOf(person(Direction.LEFT), person(Direction.LEFT)), 0)
    assertEquals(Direction.LEFT, a?.direction)
  }
}
