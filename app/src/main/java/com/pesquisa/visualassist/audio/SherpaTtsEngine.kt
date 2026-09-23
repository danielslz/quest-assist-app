package com.pesquisa.visualassist.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors

/**
 * Motor de TTS offline usando sherpa-onnx (modelo VITS/Piper em português).
 * Requisito 5.1 — o Quest não possui motor TTS do sistema.
 *
 * Os arquivos do modelo devem estar em `app/src/main/assets/tts/`:
 *  - model.onnx        (modelo VITS)
 *  - tokens.txt        (tokens)
 *  - espeak-ng-data/   (dados do fonemizador, para modelos Piper)
 *
 * Baixe um modelo pt_BR em:
 *  https://github.com/k2-fsa/sherpa-onnx/releases (vits-piper-pt_BR-*)
 * e copie o conteúdo para assets/tts/ (ver README).
 */
class SherpaTtsEngine(
  private val context: Context,
  private val assetsDir: String = "tts",
  private val modelName: String = "model.onnx",
  private val tokensName: String = "tokens.txt",
  private val dataDir: String = "tts/espeak-ng-data",
) : SpeechEngine {

  private var tts: OfflineTts? = null
  private var track: AudioTrack? = null
  private val worker = Executors.newSingleThreadExecutor()

  @Volatile private var ready = false
  override val isReady: Boolean get() = ready
  override var onReady: (() -> Unit)? = null

  init {
    worker.execute { initEngine(context.assets) }
  }

  private fun initEngine(assets: AssetManager) {
    try {
      val vits = OfflineTtsVitsModelConfig(
        model = "$assetsDir/$modelName",
        tokens = "$assetsDir/$tokensName",
        // dataDir precisa ser um caminho no filesystem para modelos Piper;
        // se o modelo usar dataDir, copie os assets para o armazenamento interno.
        dataDir = copyDataDirIfPresent(),
      )
      val modelConfig = OfflineTtsModelConfig(vits = vits, numThreads = 2, debug = false)
      val config = OfflineTtsConfig(model = modelConfig)
      tts = OfflineTts(assetManager = assets, config = config)

      val sampleRate = tts!!.sampleRate()
      track = buildAudioTrack(sampleRate)
      ready = true
      Log.i(TAG, "TTS pronto (sampleRate=$sampleRate)")
      onReady?.invoke() // reprocessa anúncios enfileirados antes de ficar pronto
    } catch (e: Throwable) {
      Log.e(TAG, "Falha ao inicializar TTS offline. Modelo presente em assets/$assetsDir?", e)
      ready = false
    }
  }

  /** espeak-ng-data precisa estar no filesystem; copia de assets se existir. */
  private fun copyDataDirIfPresent(): String {
    return try {
      val out = context.filesDir.resolve("espeak-ng-data")
      if (!out.exists()) {
        val list = context.assets.list(dataDir) ?: emptyArray()
        if (list.isEmpty()) return "" // modelo não usa espeak (ex.: alguns VITS)
        copyAssetDir(dataDir, out.absolutePath)
      }
      out.absolutePath
    } catch (e: Exception) {
      ""
    }
  }

  private fun copyAssetDir(src: String, dst: String) {
    val assets = context.assets
    val items = assets.list(src) ?: return
    java.io.File(dst).mkdirs()
    for (item in items) {
      val srcPath = "$src/$item"
      val children = assets.list(srcPath)
      if (children.isNullOrEmpty()) {
        assets.open(srcPath).use { input ->
          java.io.File(dst, item).outputStream().use { input.copyTo(it) }
        }
      } else {
        copyAssetDir(srcPath, "$dst/$item")
      }
    }
  }

  private fun buildAudioTrack(sampleRate: Int): AudioTrack {
    val minBuf = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_MONO,
      AudioFormat.ENCODING_PCM_FLOAT,
    )
    // PCM_FLOAT = 4 bytes/sample. Buffer generoso (~1s) para fala contínua,
    // evitando underruns e o modo FAST (inadequado para streaming de TTS).
    val bufBytes = maxOf(minBuf, sampleRate * 4)
    return AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setSampleRate(sampleRate)
          .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      )
      .setBufferSizeInBytes(bufBytes)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }

  override fun speak(text: String, pan: Float, onDone: () -> Unit) {
    val engine = tts
    val at = track
    if (!ready || engine == null || at == null) {
      onDone(); return
    }
    worker.execute {
      try {
        val audio = engine.generate(text = text, sid = 0, speed = 1.0f)
        at.setVolume(AudioTrack.getMaxVolume())
        at.play()
        at.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        Thread.sleep(50) // deixa o buffer drenar
        at.stop()
      } catch (e: Exception) {
        Log.e(TAG, "Erro ao sintetizar: ${e.message}", e)
      } finally {
        onDone()
      }
    }
  }

  override fun stop() {
    runCatching { track?.pause(); track?.flush() }
  }

  override fun shutdown() {
    stop()
    worker.execute {
      runCatching { track?.release() }
      runCatching { tts?.release() }
    }
    worker.shutdown()
  }

  companion object {
    private const val TAG = "VisualAssistTTS"
  }
}
