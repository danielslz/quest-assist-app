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
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.toolkit.DpDisplayOptions
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
  private val translator = com.pesquisa.visualassist.cloud.SceneTranslator()
  private lateinit var settings: com.pesquisa.visualassist.settings.SettingsStore
  private lateinit var objectDetector: com.pesquisa.visualassist.vision.ObjectDetector

  /** Último frame recebido, para OCR sob demanda (não persistido em disco). */
  @Volatile private var lastFrame: CameraFrame? = null
  @Volatile private var reading = false
  @Volatile private var lastPreviewMs = 0L
  private var panelEntity: Entity? = null

  /** Distância (Z) e escala atuais do painel, ajustadas pelos botões. */
  private var panelDistance = 1.5f
  private var panelScale = 1.0f

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
    settings = com.pesquisa.visualassist.settings.SettingsStore(this)
    // Config externa opcional (config.json via adb) — sobrescreve endpoint/modelo cloud.
    com.pesquisa.visualassist.settings.AppConfig.applyIfPresent(this, settings)
    // Detector de objetos on-device (Tarefa 5.2)
    objectDetector = com.pesquisa.visualassist.vision.ObjectDetector(this)
    vision.register(objectDetector)

    // Aplica as configurações (Tarefa 7) aos componentes, reagindo a mudanças.
    settings.observe { s ->
      audio.setVerbosity(s.verbosity)
      objectDetector.confidenceThreshold = s.confidenceThreshold
      vision.detectionEnabled = s.objectDetectionEnabled
    }
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

    // Painel de debug: câmera + bounding boxes + controles.
    // Posição/tamanho são controlados por botões no painel (determinístico e
    // acessível — o grab por gesto do SDK é inconsistente neste fluxo).
    panelEntity = Entity.createPanelEntity(
      R.id.debug_panel,
      Transform(Pose(Vector3(0f, 1.2f, 1.5f))),
      com.meta.spatial.toolkit.Grabbable(), // permite mover por gesto se o usuário conseguir
    )

    requestCameraPermissionThenStart()
  }

  /** Painel de debug que exibe a câmera com as detecções (Opção A). */
  override fun registerPanels(): List<PanelRegistration> = listOf(
    ComposeViewPanelRegistration(
      R.id.debug_panel,
      composeViewCreator = { _, ctx ->
        ComposeView(ctx).apply {
          setContent {
            DebugPanel(
              settings = settings,
              onReadText = { readText() },
              onDescribeScene = { describeScene() },
              onClose = { finishApp() },
              onNearer = { movePanel(dz = -0.3f) },
              onFarther = { movePanel(dz = 0.3f) },
              onBigger = { scalePanel(1.15f) },
              onSmaller = { scalePanel(0.87f) },
              onLeft = { movePanel(dx = -0.3f) },
              onRight = { movePanel(dx = 0.3f) },
              onUp = { movePanel(dy = 0.2f) },
              onDown = { movePanel(dy = -0.2f) },
              onCenter = { centerPanel() },
            )
          }
        }
      },
      settingsCreator = {
        UIPanelSettings(
          shape = QuadShapeOptions(width = 1.4f, height = 1.05f), // 4:3 físico (maior)
          style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeTransparent),
          // Resolução lógica (dp) explícita → dá ao Compose um espaço de layout
          // bem definido (sem isso o layout quebra: botões achatados).
          display = DpDisplayOptions(width = 800f, height = 600f, dpi = 400),
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
          val nv21 = YuvUtils.toNv21(image)
          if (nv21.isEmpty()) return  // frame descartado (buffer reciclado)
          val frame = CameraFrame(
            width = width,
            height = height,
            timestampNs = image.timestamp,
            yuv = nv21,
            intrinsics = camera.intrinsics,
          )
          lastFrame = frame
          // Preview fluido: publica o bitmap da câmera com throttle leve (~15fps),
          // independente da inferência (que roda ~0.8fps).
          val now = System.currentTimeMillis()
          if (now - lastPreviewMs >= PREVIEW_INTERVAL_MS) {
            lastPreviewMs = now
            runCatching {
              com.pesquisa.visualassist.vision.DebugFrameState.updateCamera(
                YuvUtils.nv21ToBitmap(frame.yuv, frame.width, frame.height)
              )
            }
          }
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

  /** Posição atual do painel (X lateral, Y altura, Z distância). */
  private var panelX = 0f
  private var panelY = 1.2f

  /** Move o painel nos eixos (botões). */
  private fun movePanel(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f) {
    panelX = (panelX + dx).coerceIn(-2.5f, 2.5f)
    panelY = (panelY + dy).coerceIn(0.2f, 2.5f)
    panelDistance = (panelDistance + dz).coerceIn(0.5f, 4.0f)
    panelEntity?.setComponent(Transform(Pose(Vector3(panelX, panelY, panelDistance))))
  }

  /** Recentraliza o painel na frente do usuário (posição padrão). */
  private fun centerPanel() {
    panelX = 0f; panelY = 1.2f; panelDistance = 1.5f
    panelEntity?.setComponent(Transform(Pose(Vector3(panelX, panelY, panelDistance))))
  }

  /** Aumenta/diminui o painel (botões). factor multiplicativo. */
  private fun scalePanel(factor: Float) {
    panelScale = (panelScale * factor).coerceIn(0.5f, 2.5f)
    panelEntity?.setComponent(com.meta.spatial.toolkit.Scale(Vector3(panelScale, panelScale, panelScale)))
  }

  /**
   * Descrição de cena sob demanda (Tarefa 6). Usa a nuvem se habilitada +
   * conectividade; senão, descreve pelos objetos detectados (offline).
   * Requisitos: 6.1, 6.2, 6.3, 6.4
   */
  private fun describeScene() {
    val cfg = settings.current
    val frame = lastFrame
    // Fallback offline: cloud desligado, sem endpoint, sem rede ou sem frame.
    val describer = if (cfg.cloudEnabled && cfg.cloudEndpoint.isNotBlank() && frame != null)
      com.pesquisa.visualassist.cloud.CloudSceneDescriber(this, cfg.cloudEndpoint, cfg.cloudModel)
    else null

    if (describer == null || !describer.hasConnectivity()) {
      // Offline / sem consentimento → descrição on-device pelos objetos.
      val desc = com.pesquisa.visualassist.cloud.OnDeviceSceneDescriber.describe(
        com.pesquisa.visualassist.vision.DebugFrameState.detections
      )
      val modo = if (!cfg.cloudEnabled) "" else " (modo offline)"
      audio.announce(desc + modo)
      return
    }

    audio.announce("Descrevendo a cena.")
    val bitmap = YuvUtils.nv21ToBitmap(frame!!.yuv, frame.width, frame.height)
    describer.describe(
      bitmap,
      onResult = { desc -> translator.toPortuguese(desc) { audio.announce(it) } },
      onError = { msg ->
        // Em erro de nuvem, cai para o on-device.
        val desc = com.pesquisa.visualassist.cloud.OnDeviceSceneDescriber.describe(
          com.pesquisa.visualassist.vision.DebugFrameState.detections
        )
        audio.announce("$msg $desc")
      },
    )
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
    translator.close()
    appScope.cancel()
  }

  companion object {
    private const val REQ_CAMERA = 1001
    private const val PREVIEW_INTERVAL_MS = 66L // ~15 fps para o preview de debug
  }
}
