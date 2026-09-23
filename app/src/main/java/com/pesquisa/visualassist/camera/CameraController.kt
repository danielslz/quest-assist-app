package com.pesquisa.visualassist.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wrapper da Passthrough Camera API (Camera2 do Horizon OS) para o app de
 * assistência visual. Adaptado do showcase oficial "Meta Spatial Scanner"
 * (CameraController), simplificado para entregar frames ao VisionPipeline
 * (sem preview/streaming).
 *
 * Pré-requisitos (Requisitos 1.3, 1.4, 1.5):
 *  - Horizon OS v74+, Quest 3/3S
 *  - Permissão de câmera concedida ANTES de initialize()/start()
 *  - Passthrough habilitado
 *
 * Ordem de uso: initialize() -> start(listener) -> stop() -> dispose()
 */
class CameraController(
  private val context: Context,
  private val cameraEye: CameraEye = CameraEye.LEFT,
) {
  companion object {
    private const val TAG = "VisualAssistCamera"
    private const val CAMERA_IMAGE_FORMAT = ImageFormat.YUV_420_888
    private const val CAMERA_SOURCE_KEY = "com.meta.extra_metadata.camera_source"
    private const val CAMERA_POSITION_KEY = "com.meta.extra_metadata.position"

    /** Resolução testada (Requisito 1.4: não assumir a maior disponível). */
    private val PREFERRED_SIZE = Size(1280, 960)
  }

  private val running = AtomicBoolean(false)
  val isRunning: Boolean get() = running.get()
  val isInitialized: Boolean get() = ::cameraManager.isInitialized && chosenCameraId != null

  private lateinit var cameraManager: CameraManager
  private val cameraEyeIds = HashMap<CameraEye, String>()
  private val cameraEyeCharacteristics = HashMap<CameraEye, CameraCharacteristics>()
  private var chosenCameraId: String? = null

  /** Resolução efetivamente selecionada da lista suportada. */
  var outputSize: Size = PREFERRED_SIZE
    private set

  /** Intrínsecos [fx, fy, cx, cy] quando disponíveis. */
  var intrinsics: FloatArray? = null
    private set

  private lateinit var cameraExecutor: ExecutorService
  private lateinit var imageReaderThread: HandlerThread
  private lateinit var imageReaderHandler: Handler

  private var camera: CameraDevice? = null
  private var session: CameraCaptureSession? = null
  private var imageReader: ImageReader? = null
  private val isProcessingFrame = AtomicBoolean(false)

  /**
   * Inicializa threads, o serviço de câmera e descobre a câmera de passthrough.
   * Chame após a permissão de câmera ser concedida.
   * Requisitos: 1.3
   * @throws RuntimeException se nenhuma câmera for encontrada ou o olho pedido não existir.
   */
  fun initialize() {
    cameraExecutor = Executors.newSingleThreadExecutor()
    imageReaderThread = HandlerThread("ImageReaderThread").apply {
      start()
      imageReaderHandler = Handler(looper)
    }

    cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    if (cameraManager.cameraIdList.isEmpty()) {
      throw RuntimeException("Nenhuma câmera do sistema encontrada")
    }

    // Identifica as câmeras de passthrough via vendor tags Meta (position 0/1).
    for (id in cameraManager.cameraIdList) {
      val characteristics = cameraManager.getCameraCharacteristics(id)
      val position = runCatching {
        characteristics.get(CameraCharacteristics.Key(CAMERA_POSITION_KEY, Int::class.java))
      }.getOrNull()
      val eye = when (position) {
        0 -> CameraEye.LEFT
        1 -> CameraEye.RIGHT
        else -> CameraEye.UNKNOWN
      }
      cameraEyeIds[eye] = id
      cameraEyeCharacteristics[eye] = characteristics
    }

    val id = cameraEyeIds[cameraEye]
      ?: throw RuntimeException("Câmera de passthrough para o olho ${cameraEye.name} não encontrada")
    chosenCameraId = id

    resolveCameraProperties(cameraEye)
  }

  /**
   * Abre a sessão e começa a entregar frames ao listener.
   * Requisitos: 1.3
   */
  fun start(imageAvailableListener: ImageAvailableListener) {
    if (!isInitialized) throw RuntimeException("Câmera não inicializada")
    if (running.get()) {
      Log.w(TAG, "Câmera já está rodando")
      return
    }
    CoroutineScope(Dispatchers.Main).launch {
      startInternal(imageAvailableListener)
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun startInternal(listener: ImageAvailableListener) {
    try {
      running.set(true)
      val id = chosenCameraId!!
      camera = openCamera(cameraManager, id, cameraExecutor)

      imageReader = ImageReader.newInstance(
        outputSize.width, outputSize.height, CAMERA_IMAGE_FORMAT, 2,
      )
      val targets = listOf(imageReader!!.surface)

      session = createCaptureSession(camera!!, targets, cameraExecutor)

      val requestBuilder = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
        targets.forEach { addTarget(it) }
      }
      session!!.setSingleRepeatingRequest(
        requestBuilder.build(), cameraExecutor, object : CameraCaptureSession.CaptureCallback() {},
      )

      imageReader?.setOnImageAvailableListener({ reader ->
        if (isProcessingFrame.get()) return@setOnImageAvailableListener
        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
        isProcessingFrame.set(true)
        listener.onNewImage(image, image.width, image.height) {
          image.close()
          isProcessingFrame.set(false)
        }
      }, imageReaderHandler)
    } catch (e: Exception) {
      Log.e(TAG, "Falha ao iniciar câmera", e)
      stop()
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun openCamera(
    manager: CameraManager, cameraId: String, executor: Executor,
  ): CameraDevice = suspendCoroutine { cont ->
    manager.openCamera(cameraId, executor, object : CameraDevice.StateCallback() {
      override fun onOpened(device: CameraDevice) = cont.resume(device)
      override fun onDisconnected(device: CameraDevice) { stop() }
      override fun onError(device: CameraDevice, error: Int) {
        cont.resumeWithException(RuntimeException("Erro ao abrir câmera $cameraId: $error"))
      }
    })
  }

  private suspend fun createCaptureSession(
    device: CameraDevice, targets: List<android.view.Surface>, executor: Executor,
  ): CameraCaptureSession = suspendCoroutine { cont ->
    device.createCaptureSession(
      SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR,
        targets.map { OutputConfiguration(it) },
        executor,
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(s: CameraCaptureSession) = cont.resume(s)
          override fun onConfigureFailed(s: CameraCaptureSession) {
            cont.resumeWithException(RuntimeException("Falha ao configurar sessão da câmera"))
          }
        },
      ),
    )
  }

  /**
   * Seleciona a resolução (testada) da lista suportada e extrai intrínsecos.
   * Requisitos: 1.4
   */
  private fun resolveCameraProperties(eye: CameraEye) {
    val characteristics = cameraEyeCharacteristics[eye]!!
    val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
    val sizes = map?.getOutputSizes(CAMERA_IMAGE_FORMAT) ?: emptyArray()
    Log.d(TAG, "Resoluções suportadas: ${sizes.joinToString()}")

    // Requisito 1.4: escolher via política pura (preferir 1280x960; senão maior 4:3).
    val chosen = ResolutionPolicy.choose(
      sizes.map { ResolutionPolicy.Res(it.width, it.height) },
    )
    outputSize = Size(chosen.width, chosen.height)

    intrinsics = runCatching {
      characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
    }.getOrNull()

    Log.d(TAG, "Resolução escolhida: $outputSize; intrínsecos: ${intrinsics?.joinToString()}")
  }

  /** Fecha a sessão (mantém threads para possível restart). */
  fun stop() {
    running.set(false)
    CoroutineScope(Dispatchers.Main).launch {
      imageReader?.close(); imageReader = null
    }
    session?.close(); session = null
    camera?.close(); camera = null
  }

  /** Fecha tudo e encerra as threads. Chame no onDestroy. */
  fun dispose() {
    stop()
    if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    if (::imageReaderThread.isInitialized) imageReaderThread.quitSafely()
  }

  /** Recebe frames da câmera. O callback finally DEVE ser chamado ao terminar. */
  interface ImageAvailableListener {
    fun onNewImage(image: Image, width: Int, height: Int, finally: () -> Unit)
  }
}
