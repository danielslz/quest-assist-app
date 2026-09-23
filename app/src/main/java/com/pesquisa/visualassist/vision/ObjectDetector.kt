package com.pesquisa.visualassist.vision

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector as MpObjectDetector
import com.pesquisa.visualassist.camera.YuvUtils

/**
 * Detecção de objetos on-device com MediaPipe Tasks Vision (EfficientDet-Lite0).
 * Modelo em assets/models/efficientdet_lite0.tflite.
 *
 * Requisitos: 2.1, 2.3, 2.4
 */
class ObjectDetector(
  private val context: Context,
  private val confidenceThreshold: Float = 0.5f,
  private val maxResults: Int = 5,
  private val modelAsset: String = "models/efficientdet_lite0.tflite",
) : VisionDetector {

  private var detector: MpObjectDetector? = null

  override fun warmup() {
    try {
      val base = BaseOptions.builder().setModelAssetPath(modelAsset).build()
      val options = MpObjectDetector.ObjectDetectorOptions.builder()
        .setBaseOptions(base)
        .setRunningMode(RunningMode.IMAGE)
        .setScoreThreshold(confidenceThreshold)
        .setMaxResults(maxResults)
        .build()
      detector = MpObjectDetector.createFromOptions(context, options)
      Log.i(TAG, "ObjectDetector pronto")
    } catch (e: Throwable) {
      Log.e(TAG, "Falha ao carregar ObjectDetector (modelo em assets/$modelAsset?)", e)
      detector = null
    }
  }

  override fun detect(frame: CameraFrame): List<Detection> {
    val det = detector ?: return emptyList()
    val bitmap = YuvUtils.nv21ToBitmap(frame.yuv, frame.width, frame.height)
    val mpImage = BitmapImageBuilder(bitmap).build()

    val result = det.detect(mpImage)
    val out = ArrayList<Detection>()
    for (d in result.detections()) {
      val cat = d.categories().maxByOrNull { it.score() } ?: continue
      if (cat.score() < confidenceThreshold) continue
      val box = d.boundingBox() // RectF em pixels
      val direction = DirectionMapper.fromBox(box.left, box.right, frame.width)
      out.add(
        Detection(
          label = translateLabel(cat.categoryName()),
          confidence = cat.score(),
          box = floatArrayOf(
            box.left / frame.width, box.top / frame.height,
            box.right / frame.width, box.bottom / frame.height,
          ),
          direction = direction,
          kind = Detection.Kind.OBJECT,
        )
      )
    }
    return out
  }

  override fun close() {
    detector?.close()
    detector = null
  }

  companion object {
    private const val TAG = "VisualAssistObjDet"
  }
}

/**
 * Traduz rótulos comuns do COCO (inglês) para português para o TTS.
 * Cobre os objetos mais frequentes; rótulos não mapeados ficam em inglês.
 */
private val COCO_PT = mapOf(
  "person" to "pessoa", "bicycle" to "bicicleta", "car" to "carro",
  "motorcycle" to "motocicleta", "bus" to "ônibus", "truck" to "caminhão",
  "traffic light" to "semáforo", "bench" to "banco", "cat" to "gato",
  "dog" to "cachorro", "backpack" to "mochila", "umbrella" to "guarda-chuva",
  "handbag" to "bolsa", "bottle" to "garrafa", "cup" to "copo",
  "fork" to "garfo", "knife" to "faca", "spoon" to "colher", "bowl" to "tigela",
  "banana" to "banana", "apple" to "maçã", "chair" to "cadeira",
  "couch" to "sofá", "potted plant" to "planta", "bed" to "cama",
  "dining table" to "mesa", "toilet" to "vaso sanitário", "tv" to "televisão",
  "laptop" to "notebook", "mouse" to "mouse", "keyboard" to "teclado",
  "cell phone" to "celular", "microwave" to "micro-ondas", "oven" to "forno",
  "refrigerator" to "geladeira", "book" to "livro", "clock" to "relógio",
  "scissors" to "tesoura", "door" to "porta",
)

private fun translateLabel(label: String?): String {
  if (label == null) return "objeto"
  return COCO_PT[label.lowercase()] ?: label
}
