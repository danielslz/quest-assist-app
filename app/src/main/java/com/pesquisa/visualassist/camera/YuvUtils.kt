package com.pesquisa.visualassist.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Utilitários para converter frames YUV_420_888 da câmera. */
object YuvUtils {

  /**
   * Converte uma [Image] YUV_420_888 em um ByteArray no layout NV21
   * (Y plano seguido de VU intercalado), formato aceito por ML Kit e
   * facilmente convertível para outros runtimes.
   */
  fun toNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val ySize = width * height
    val uvSize = width * height / 4

    val nv21 = ByteArray(ySize + uvSize * 2)

    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer: ByteBuffer = yPlane.buffer
    val uBuffer: ByteBuffer = uPlane.buffer
    val vBuffer: ByteBuffer = vPlane.buffer

    // Copia o plano Y respeitando o rowStride.
    var pos = 0
    val yRowStride = yPlane.rowStride
    if (yRowStride == width) {
      yBuffer.get(nv21, 0, ySize)
      pos = ySize
    } else {
      for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(nv21, pos, width)
        pos += width
      }
    }

    // Intercala V e U (NV21 = ...VUVUVU).
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride
    for (row in 0 until height / 2) {
      for (col in 0 until width / 2) {
        val uvIndex = row * uvRowStride + col * uvPixelStride
        nv21[pos++] = vBuffer.get(uvIndex)
        nv21[pos++] = uBuffer.get(uvIndex)
      }
    }
    return nv21
  }

  /**
   * Converte bytes NV21 em [Bitmap] RGB (via JPEG intermediário).
   * Usado para alimentar o MediaPipe (que requer Bitmap/MPImage).
   */
  fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int, jpegQuality: Int = 85): Bitmap {
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), jpegQuality, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }
}