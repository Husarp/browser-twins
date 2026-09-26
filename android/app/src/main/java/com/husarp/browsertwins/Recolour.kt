package com.husarp.browsertwins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

// The same picture with its colours moved round the colour wheel, like LinkPilot's Tray.Recolour
// (LinkPilot/Tray.cs): hue in degrees, strength (saturation) and brightness in percent.
object Recolour {
    fun apply(src: Bitmap, hue: Int, strength: Int, brightness: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = hueMatrix(hue.toFloat()).apply {
            postConcat(ColorMatrix().apply { setSaturation(strength / 100f) })
            val b = brightness / 100f
            postConcat(ColorMatrix(floatArrayOf(
                b, 0f, 0f, 0f, 0f,
                0f, b, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return out
    }

    // Standard luminance-preserving hue-rotation matrix.
    private fun hueMatrix(degrees: Float): ColorMatrix {
        val a = Math.toRadians(degrees.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return ColorMatrix(floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}
