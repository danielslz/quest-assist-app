package com.pesquisa.visualassist.cloud

import org.json.JSONArray
import org.json.JSONObject

/**
 * Monta a requisição e interpreta a resposta de um endpoint LLM multimodal
 * no formato **OpenAI-compatible** (`/v1/chat/completions`). Funciona tanto para
 * um protótipo local (Ramalama) quanto para APIs cloud.
 *
 * Classe pura (sem rede/Android) — testável. Requisito 6.1.
 */
object SceneDescriptionProtocol {

  /** Prompt padrão para descrição de cena acessível (conciso, em português). */
  const val DEFAULT_PROMPT =
    "Descreva de forma breve e objetiva, em português, o ambiente e os principais " +
      "objetos e pessoas visíveis nesta imagem, para uma pessoa com deficiência visual. " +
      "Priorize o que é útil para locomoção e segurança. Máximo 2 frases."

  /**
   * Monta o corpo JSON da requisição.
   * @param model nome do modelo (ex.: "llava", "qwen2-vl").
   * @param jpegBase64 imagem em base64 (sem prefixo data:).
   * @param prompt instrução.
   */
  fun buildRequestBody(model: String, jpegBase64: String, prompt: String = DEFAULT_PROMPT): String {
    val imageUrl = JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")
    val content = JSONArray()
      .put(JSONObject().put("type", "text").put("text", prompt))
      .put(JSONObject().put("type", "image_url").put("image_url", imageUrl))
    val message = JSONObject().put("role", "user").put("content", content)
    return JSONObject()
      .put("model", model)
      .put("messages", JSONArray().put(message))
      .put("max_tokens", 150)
      .put("temperature", 0.2)
      .toString()
  }

  /**
   * Extrai o texto da resposta (choices[0].message.content).
   * Retorna null se o formato não casar.
   */
  fun parseResponse(json: String): String? = runCatching {
    JSONObject(json)
      .getJSONArray("choices")
      .getJSONObject(0)
      .getJSONObject("message")
      .getString("content")
      .trim()
      .ifBlank { null }
  }.getOrNull()
}
