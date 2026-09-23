package com.pesquisa.visualassist.vision

/**
 * Frame da câmera de passthrough + metadados (ver design.md, CameraController).
 * A imagem chega em YUV420; converta para o formato que seu detector precisa.
 *
 * Requisitos: 1.3
 */
data class CameraFrame(
  val width: Int,
  val height: Int,
  val timestampNs: Long,
  /** Bytes YUV420 do frame. */
  val yuv: ByteArray,
  /** Intrínsecos da lente [fx, fy, cx, cy], se disponíveis. */
  val intrinsics: FloatArray? = null,
) {
  // equals/hashCode gerados manualmente por conter arrays
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CameraFrame) return false
    return timestampNs == other.timestampNs && width == other.width && height == other.height
  }

  override fun hashCode(): Int = (timestampNs xor (timestampNs ushr 32)).toInt()
}

/** Direção relativa derivada da bounding box + intrínsecos. */
enum class Direction { LEFT, CENTER, RIGHT }

/** Uma detecção genérica (objeto, texto ou pessoa). */
data class Detection(
  val label: String,
  val confidence: Float,
  /** Bounding box normalizada [0..1]: left, top, right, bottom. */
  val box: FloatArray,
  val direction: Direction,
  val kind: Kind,
) {
  enum class Kind { OBJECT, TEXT, PERSON }
}
