package com.pesquisa.visualassist

import android.Manifest
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.vr.VRFeature
import com.pesquisa.visualassist.audio.AudioFeedbackManager
import com.pesquisa.visualassist.audio.SherpaTtsEngine
import com.pesquisa.visualassist.camera.CameraController
import com.pesquisa.visualassist.camera.YuvUtils
import com.pesquisa.visualassist.ui.DebugPanel
import com.pesquisa.visualassist.vision.CameraFrame
import com.pesquisa.visualassist.vision.VisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Activity principal do app de assistência visual (Meta Spatial SDK).
 *
 * Ciclo: registerFeatures() -> onCreate() -> onSceneReady() (cena pronta) ->
 * pede permissão de câmera -> abre a Passthrough Camera -> frames -> visão -> áudio.
 *
 * Requisitos: 1.1, 1.2, 1.3, 1.5
 */
class VisualAssistActivity : AppSystemActivity() {

  private val appScope = CoroutineScope(SupervisorJob())

  private lateinit var audio: AudioFeedbackManager
  private lateinit var vision: VisionPipeline
  private lateinit var camera: CameraController
  private val textReader = com.pesquisa.visualassist.vision.TextReader()

  /** Último frame recebido, para OCR sob demanda (não persistido em disco). */
  @Volatile private var lastFrame: CameraFrame? = null
  @Volatile private var reading = false

  /** VRFeature é obrigatório para o AppSystemActivity inicializar o render de VR. */
  override fun registerFeatures(): List<SpatialFeature> {
    return listOf(VRFeature(this), ComposeFeature())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    android.util.Log.i("VisualAssist", "onCreate chamado")
    audio = AudioFeedbackManager(SherpaTtsEngine(this))
    vision = VisionPipeline(this, appScope)
    camera = CameraController(this)
    // Detector de objetos on-device (Tarefa 5.2)
    vision.register(com.pesquisa.visualassist.vision.ObjectDetector(this))
  }

  /**
   * Cena pronta: configura ambiente mínimo (passthrough é habilitado por padrão),
   * liga o áudio espacial e SÓ ENTÃO pede a permissão de câmera.
   * Requisitos: 1.5, 5.2
   */
  override fun onSceneReady() {
    super.onSceneReady()
    android.util.Log.i("VisualAssist", "onSceneReady: montando cena e habilitando passthrough")

    // Habilita passthrough (mostra o mundo real) — sem isto a tela fica preta.
    scene.enablePassthrough(true)

    // Espaço de referência (permite recentralizar) e iluminação mínima.
    scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
    scene.setLightingEnvironment(
      ambientColor = Vector3(0.5f),
      sunColor = Vector3(3.0f, 3.0f, 3.0f),
      sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
      environmentIntensity = 0.3f,
    )

    // Áudio espacial (beep direcional) — Requisito 5.2.
    // TODO(task 4.2): adicionar assets/audio/cue.wav (mono, 48kHz) e ativar o beep.
    // Enquanto o asset não existe, NÃO carregamos (SceneAudioAsset lança assert nativo
    // se o arquivo faltar). A direção continua sendo indicada pelo pan estéreo do TTS.

    // Boas-vindas por áudio e fluxo de permissão.
    audio.announce("Assistente visual iniciado.")

    // Painel de debug (Opção A): câmera + bounding boxes, ~2m à frente.
    Entity.create(
      listOf(
        Panel(R.id.debug_panel),
        Transform(Pose(Vector3(0f, 1.2f, 2f))),
      )
    )

    requestCameraPermissionThenStart()
  }

  /** Painel de debug que exibe a câmera com as detecções (Opção A). */
  override fun registerPanels(): List<PanelRegistration> = listOf(
    ComposeViewPanelRegistration(
      R.id.debug_panel,
      composeViewCreator = { _, ctx ->
        ComposeView(ctx).apply { setContent { DebugPanel(onReadText = { readText() }, onClose = { finishApp() }) } }
      },
      settingsCreator = {
        UIPanelSettings(
          shape = QuadShapeOptions(width = 1.28f, height = 0.96f), // 4:3 como a câmera
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          display = DpPerMeterDisplayOptions(),
        )
      },
    ),
  )

  /** Requisitos: 1.1, 1.2 — permissão com feedback por áudio. */
  private fun requestCameraPermissionThenStart() {
    if (hasCameraPermission()) {
      startCamera()
    } else {
      audio.announce("Preciso de permissão para usar a câmera.")
      requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }
  }

  private fun hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray,
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQ_CAMERA) {
      if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
        startCamera()
      } else {
        audio.announce("Permissão negada. Reabra o app para tentar novamente.")
      }
    }
  }

  /** Requisitos: 1.3, 1.5 — inicializa e abre o stream, alimentando o pipeline. */
  private fun startCamera() {
    try {
      camera.initialize()
    } catch (e: Exception) {
      audio.announce("Não consegui acessar a câmera do headset.")
      return
    }

    camera.start(object : CameraController.ImageAvailableListener {
      override fun onNewImage(image: Image, width: Int, height: Int, finally: () -> Unit) {
        try {
          val frame = CameraFrame(
            width = width,
            height = height,
            timestampNs = image.timestamp,
            yuv = YuvUtils.toNv21(image),
            intrinsics = camera.intrinsics,
          )
          lastFrame = frame
          vision.submit(frame) { detections -> audio.report(detections) }
        } finally {
          finally()
        }
      }
    })
    audio.announce("Câmera ativa.")
  }

  /** OCR sob demanda (Requisitos 3.1-3.3): lê o texto do último frame por voz. */
  private fun readText() {
    if (reading) return
    val frame = lastFrame
    if (frame == null) {
      audio.announce("Câmera ainda não está pronta.")
      return
    }
    reading = true
    audio.announce("Lendo texto.")
    val bitmap = YuvUtils.nv21ToBitmap(frame.yuv, frame.width, frame.height)
    textReader.read(bitmap) { text ->
      if (text.isBlank()) {
        audio.announce("Nenhum texto detectado.")
      } else {
        // Texto reconhecido: fala com prioridade alta (ação explícita do usuário).
        audio.announce(text)
      }
      reading = false
    }
  }

  /** Encerra o app de forma limpa (botão do painel de debug). */
  private fun finishApp() {
    runCatching { camera.stop() }
    runCatching { audio.announce("Encerrando.") }
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
    camera.dispose()
    vision.stop()
    audio.shutdown()
    textReader.close()
    appScope.cancel()
  }

  companion object {
    private const val REQ_CAMERA = 1001
  }
}
