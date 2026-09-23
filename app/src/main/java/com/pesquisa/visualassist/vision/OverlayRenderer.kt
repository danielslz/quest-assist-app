package com.pesquisa.visualassist.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Desenha bounding boxes e rótulos das detecções sobre o frame da câmera,
 * para um painel de debug (Opção A). Puramente visual — não afeta o áudio.
 */
object OverlayRenderer {

  private val boxPaint = Paint().apply {
    color = Color.GREEN
    style = Paint.Style.STROKE
    strokeWidth = 4f
    isAntiAlias = true
  }
  private val textBgPaint = Paint().apply {
    color = Color.argb(160, 0, 0, 0)
    style = Paint.Style.FILL
  }
  private val textPaint = Paint().apply {
    color = Color.WHITE
    textSize = 32f
    isAntiAlias = true
  }

  /**
   * Retorna uma cópia do [source] com as caixas desenhadas.
   * As boxes das detecções são normalizadas [0..1]; escalamos para o bitmap.
   */
  fun draw(source: Bitmap, detections: List<Detection>): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val w = out.width
    val h = out.height

    for (d in detections) {
      val left = d.box[0] * w
      val top = d.box[1] * h
      val right = d.box[2] * w
      val bottom = d.box[3] * h
      canvas.drawRect(left, top, right, bottom, boxPaint)

      val label = "${d.label} ${(d.confidence * 100).toInt()}%"
      val tw = textPaint.measureText(label)
      val th = textPaint.textSize
      canvas.drawRect(left, (top - th - 8).coerceAtLeast(0f), left + tw + 12, top, textBgPaint)
      canvas.drawText(label, left + 6, (top - 8).coerceAtLeast(th), textPaint)
    }
    return out
  }
}
