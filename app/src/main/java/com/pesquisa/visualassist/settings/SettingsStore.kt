package com.pesquisa.visualassist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pesquisa.visualassist.audio.Verbosity

/**
 * Persiste e expõe as [Settings] do app (SharedPreferences). O valor atual é
 * observável (Compose) para o painel reagir. Mudanças são aplicadas por
 * callbacks registrados (ex.: ObjectDetector, AudioFeedbackManager).
 *
 * Requisitos: 5.3, 6.3, 7.
 */
class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("visualassist_settings", Context.MODE_PRIVATE)

  var current by mutableStateOf(load())
    private set

  private val listeners = mutableListOf<(Settings) -> Unit>()

  /** Registra um observador que recebe as settings ao mudar (e imediatamente). */
  fun observe(listener: (Settings) -> Unit) {
    listeners.add(listener)
    listener(current)
  }

  fun update(transform: (Settings) -> Settings) {
    val next = transform(current)
    current = next
    save(next)
    listeners.forEach { it(next) }
  }

  private fun load(): Settings = Settings(
    verbosity = runCatching {
      Verbosity.valueOf(prefs.getString(KEY_VERBOSITY, Verbosity.NORMAL.name)!!)
    }.getOrDefault(Verbosity.NORMAL),
    confidenceThreshold = Settings.clampConfidence(
      prefs.getFloat(KEY_CONFIDENCE, 0.5f)
    ),
    objectDetectionEnabled = prefs.getBoolean(KEY_OBJ_ENABLED, true),
    cloudEnabled = prefs.getBoolean(KEY_CLOUD, false),
    cloudEndpoint = prefs.getString(KEY_CLOUD_ENDPOINT, "") ?: "",
    cloudModel = prefs.getString(KEY_CLOUD_MODEL, "llava") ?: "llava",
  )

  private fun save(s: Settings) {
    prefs.edit()
      .putString(KEY_VERBOSITY, s.verbosity.name)
      .putFloat(KEY_CONFIDENCE, s.confidenceThreshold)
      .putBoolean(KEY_OBJ_ENABLED, s.objectDetectionEnabled)
      .putBoolean(KEY_CLOUD, s.cloudEnabled)
      .putString(KEY_CLOUD_ENDPOINT, s.cloudEndpoint)
      .putString(KEY_CLOUD_MODEL, s.cloudModel)
      .apply()
  }

  companion object {
    private const val KEY_VERBOSITY = "verbosity"
    private const val KEY_CONFIDENCE = "confidence"
    private const val KEY_OBJ_ENABLED = "object_detection_enabled"
    private const val KEY_CLOUD = "cloud_enabled"
    private const val KEY_CLOUD_ENDPOINT = "cloud_endpoint"
    private const val KEY_CLOUD_MODEL = "cloud_model"
  }
}
