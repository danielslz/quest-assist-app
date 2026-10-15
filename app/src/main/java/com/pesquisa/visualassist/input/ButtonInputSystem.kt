package com.pesquisa.visualassist.input

import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller

/**
 * Sistema que lê os botões do controle a cada frame e dispara [AppAction] via
 * [ButtonActionMapper], usando edge detection (só no instante em que o botão é
 * pressionado). Via de acionamento acessível alternativa (Tarefa 9.5), para
 * validar o fluxo ação→função→voz antes dos microgestures.
 *
 * Botões frontais fáceis de achar sem enxergar (não os gatilhos, que o sistema
 * usa para selecionar na UI):
 *  A → descrever · X → ler texto · B → liga/desliga detecção · Y → repetir ·
 *  clique analógico esquerdo → tutorial · clique analógico direito → fechar.
 */
class ButtonInputSystem(
  private val onAction: (AppAction) -> Unit,
) : SystemBase() {

  private val mapper = ButtonActionMapper(
    buttonA = ButtonBits.ButtonA,
    buttonX = ButtonBits.ButtonX,
    buttonB = ButtonBits.ButtonB,
    buttonY = ButtonBits.ButtonY,
    thumbClickLeft = ButtonBits.ButtonThumbLClick,
    thumbClickRight = ButtonBits.ButtonThumbRClick,
  )

  // Estado anterior dos botões por entidade (edge detection).
  private val lastState = HashMap<Long, Int>()

  override fun execute() {
    val controllers = Query.where { has(Controller.id) }.eval()
    for (entity in controllers) {
      val controller = runCatching { entity.getComponent<Controller>() }.getOrNull() ?: continue
      if (!controller.isActive) continue

      val now = controller.buttonState
      val prev = lastState[entity.id] ?: 0
      // Botões que passaram de solto (0) para pressionado (1) neste frame.
      val pressed = now and prev.inv()
      lastState[entity.id] = now

      if (pressed != 0) {
        mapper.map(pressed)?.let(onAction)
      }
    }
  }
}
