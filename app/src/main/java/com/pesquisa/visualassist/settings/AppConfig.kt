package com.pesquisa.visualassist.settings

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Configuração externa opcional, lida de um arquivo JSON no armazenamento do app
 * (equivalente a um ".env"): editável via `adb push` sem recompilar.
 *
 * Caminho: /sdcard/Android/data/com.pesquisa.visualassist/files/config.json
 * Exemplo:
 * {
 *   "cloudEnabled": true,
 *   "cloudEndpoint": "http://192.168.0.10:8080",
 *   "cloudModel": "llava"
 * }
 *
 * Útil para configurar o endpoint do LLM em dev/pesquisa (digitar URL em VR é
 * inviável). Valores presentes sobrescrevem as Settings persistidas.
 */
object AppConfig {
  private const val TAG = "VisualAssistConfig"
  const val FILE_NAME = "config.json"

  /** Aplica o config.json (se existir) às [SettingsStore]. */
  fun applyIfPresent(context: Context, store: SettingsStore) {
    val file = context.getExternalFilesDir(null)?.resolve(FILE_NAME) ?: return
    if (!file.exists()) {
      Log.i(TAG, "config.json não encontrado em ${file.absolutePath} (usando defaults)")
      return
    }
    runCatching {
      val json = JSONObject(file.readText())
      store.update { s ->
        s.copy(
          cloudEnabled = json.optBoolean("cloudEnabled", s.cloudEnabled),
          cloudEndpoint = json.optString("cloudEndpoint", s.cloudEndpoint),
          cloudModel = json.optString("cloudModel", s.cloudModel),
        )
      }
      Log.i(TAG, "config.json aplicado: endpoint=${store.current.cloudEndpoint} cloud=${store.current.cloudEnabled}")
    }.onFailure { Log.e(TAG, "config.json inválido: ${it.message}") }
  }
}
