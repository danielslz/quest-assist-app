package com.pesquisa.visualassist.input

/**
 * Mapeia botões do controle (recém-pressionados) para [AppAction]. Lógica pura
 * e testável: recebe o bitmask de botões que mudaram para "pressionado" e
 * resolve UMA ação (ordem determinística em caso de combinação).
 *
 * Os valores de bit são injetados (vêm de `ButtonBits` do SDK em runtime), para
 * manter esta classe livre de dependências Android e testável na JVM.
 *
 * Usa os botões frontais A/B/X/Y (NÃO os gatilhos, que o sistema usa para
 * selecionar itens na UI e causariam conflito):
 *  - A → DESCRIBE_SCENE
 *  - X → READ_TEXT
 *  - B → TOGGLE_DETECTION
 *  - Y → REPEAT_LAST
 *  - Clique do analógico esquerdo → TUTORIAL (abre/fecha o modo tutorial)
 *  - Clique do analógico direito → CLOSE_APP (com confirmação dupla)
 */
class ButtonActionMapper(
  private val buttonA: Int,
  private val buttonX: Int,
  private val buttonB: Int,
  private val buttonY: Int,
  private val thumbClickLeft: Int,
  private val thumbClickRight: Int,
) {

  /**
   * @param pressedBits bitmask de botões que acabaram de ser pressionados
   *   (transição solta→pressionada). Use edge detection antes de chamar.
   * @return a ação correspondente, ou null se nenhum botão mapeado.
   */
  fun map(pressedBits: Int): AppAction? = when {
    pressedBits and thumbClickLeft != 0 -> AppAction.TUTORIAL
    pressedBits and thumbClickRight != 0 -> AppAction.CLOSE_APP
    pressedBits and buttonA != 0 -> AppAction.DESCRIBE_SCENE
    pressedBits and buttonX != 0 -> AppAction.READ_TEXT
    pressedBits and buttonB != 0 -> AppAction.TOGGLE_DETECTION
    pressedBits and buttonY != 0 -> AppAction.REPEAT_LAST
    else -> null
  }
}
