package com.pesquisa.visualassist.cloud

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Descrição de cena via LLM multimodal (cloud/endpoint local), opt-in.
 * Requisitos 6.1-6.4: envia JPEG a endpoint OpenAI-compatible, lê a descrição;
 * verifica conectividade; só envia com consentimento (controlado pela Activity).
 */
class CloudSceneDescriber(
  private val context: Context,
  private val endpoint: String,
  private val model: String,
) {
  private val http = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  /** Há rede com internet? (Requisito 6.2) */
  fun hasConnectivity(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  /**
   * Descreve [bitmap] via LLM. Chama [onResult] com a descrição, ou [onError]
   * com uma mensagem amigável. Assíncrono (não bloqueia o chamador).
   */
  fun describe(bitmap: Bitmap, onResult: (String) -> Unit, onError: (String) -> Unit) {
    if (endpoint.isBlank()) {
      onError("Endpoint da nuvem não configurado."); return
    }
    val jpeg = ByteArrayOutputStream().use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray()
    }
    val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
    val body = SceneDescriptionProtocol.buildRequestBody(model, b64)
      .toRequestBody("application/json".toMediaType())
    val url = if (endpoint.endsWith("/chat/completions")) endpoint
              else endpoint.trimEnd('/') + "/v1/chat/completions"
    val req = Request.Builder().url(url).post(body).build()

    http.newCall(req).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        Log.e(TAG, "Falha de rede: ${e.message}")
        onError("Não consegui falar com o serviço de descrição.")
      }
      override fun onResponse(call: Call, response: Response) {
        response.use {
          val text = it.body?.string()?.let { b -> SceneDescriptionProtocol.parseResponse(b) }
          if (it.isSuccessful && text != null) onResult(text)
          else onError("O serviço de descrição não respondeu como esperado.")
        }
      }
    })
  }

  companion object {
    private const val TAG = "VisualAssistCloud"
  }
}
