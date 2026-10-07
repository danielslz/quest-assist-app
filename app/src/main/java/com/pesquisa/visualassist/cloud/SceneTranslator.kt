package com.pesquisa.visualassist.cloud

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Traduz a descrição de cena de inglês para português (offline, ML Kit).
 * O modelo VLM leve (SmolVLM) tende a responder em inglês; traduzimos antes de
 * falar. O modelo de tradução é baixado uma vez e fica em cache no device.
 */
class SceneTranslator {

  private val translator = Translation.getClient(
    TranslatorOptions.Builder()
      .setSourceLanguage(TranslateLanguage.ENGLISH)
      .setTargetLanguage(TranslateLanguage.PORTUGUESE)
      .build()
  )

  @Volatile private var modelReady = false

  init {
    // Baixa o modelo EN->PT (só na 1a vez; requer rede nesse momento).
    translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
      .addOnSuccessListener { modelReady = true; Log.i(TAG, "modelo de tradução pronto") }
      .addOnFailureListener { Log.w(TAG, "falha ao baixar modelo de tradução: ${it.message}") }
  }

  /**
   * Traduz [text] para pt. Se o modelo não estiver pronto ou falhar, chama
   * [onResult] com o texto original (fallback) — melhor em inglês que nada.
   */
  fun toPortuguese(text: String, onResult: (String) -> Unit) {
    translator.translate(text)
      .addOnSuccessListener { onResult(it) }
      .addOnFailureListener {
        Log.w(TAG, "falha na tradução: ${it.message}")
        onResult(text)
      }
  }

  fun close() = runCatching { translator.close() }

  companion object {
    private const val TAG = "VisualAssistTranslate"
  }
}
