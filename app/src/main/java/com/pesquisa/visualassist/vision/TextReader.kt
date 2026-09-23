package com.pesquisa.visualassist.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * OCR sob demanda com ML Kit Text Recognition (script latino: pt/en).
 * Acionado por ação do usuário (botão), NÃO no pipeline contínuo.
 * Requisitos: 3.1, 3.2, 3.3, 3.4
 */
class TextReader {

  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  /**
   * Reconhece texto no [bitmap]. Chama [onResult] com o texto (ou string vazia
   * se nada for encontrado) na thread principal.
   */
  fun read(bitmap: Bitmap, onResult: (String) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)
    recognizer.process(image)
      .addOnSuccessListener { visionText ->
        // Junta as linhas com espaçamento natural; limita para não falar textos enormes.
        val text = visionText.text.trim()
        onResult(text)
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Falha no OCR: ${e.message}")
        onResult("")
      }
  }

  fun close() {
    runCatching { recognizer.close() }
  }

  companion object {
    private const val TAG = "VisualAssistOCR"
  }
}
