package com.pesquisa.visualassist.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonActionMapperTest {

  // Bits fictícios (potências de 2) espelhando ButtonBits do SDK.
  private val btnA = 1 shl 0
  private val btnX = 1 shl 1
  private val btnB = 1 shl 2
  private val btnY = 1 shl 3
  private val thumbLeft = 1 shl 4
  private val thumbRight = 1 shl 5
  private val mapper = ButtonActionMapper(btnA, btnX, btnB, btnY, thumbLeft, thumbRight)

  @Test fun botaoA_descreveCena() {
    assertEquals(AppAction.DESCRIBE_SCENE, mapper.map(btnA))
  }

  @Test fun botaoX_leTexto() {
    assertEquals(AppAction.READ_TEXT, mapper.map(btnX))
  }

  @Test fun botaoB_alternaDeteccao() {
    assertEquals(AppAction.TOGGLE_DETECTION, mapper.map(btnB))
  }

  @Test fun botaoY_repeteUltima() {
    assertEquals(AppAction.REPEAT_LAST, mapper.map(btnY))
  }

  @Test fun cliqueAnalogicoEsquerdo_chamaTutorial() {
    assertEquals(AppAction.TUTORIAL, mapper.map(thumbLeft))
  }

  @Test fun cliqueAnalogicoDireito_fechaApp() {
    assertEquals(AppAction.CLOSE_APP, mapper.map(thumbRight))
  }

  @Test fun nenhumBotaoMapeado_retornaNull() {
    assertNull(mapper.map(0))
    assertNull(mapper.map(1 shl 10)) // bit não mapeado (ex.: gatilho)
  }

  @Test fun combinacao_priorizaTutorial() {
    // O tutorial tem a maior prioridade na ordem determinística.
    assertEquals(AppAction.TUTORIAL, mapper.map(thumbLeft or btnA or btnB))
  }
}
