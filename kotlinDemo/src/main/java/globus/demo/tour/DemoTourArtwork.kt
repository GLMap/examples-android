package globus.demo.tour

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.roundToInt

/** Raster artwork only. GLMapImage handles geographic placement and rendering. */
internal class DemoTourArtwork(private val density: Float) {
    companion object {
        val ACCENT = Color.rgb(74, 82, 239)
        val INK = Color.rgb(33, 43, 61)
        val USER_BLUE = Color.rgb(46, 120, 247)
    }

    private fun image(width: Int, height: Int, draw: (Canvas, Paint) -> Unit): Bitmap =
        Bitmap.createBitmap((width * density).roundToInt(), (height * density).roundToInt(), Bitmap.Config.ARGB_8888)
            .apply {
                this.density = Bitmap.DENSITY_NONE
                val canvas = Canvas(this)
                canvas.scale(this@DemoTourArtwork.density, this@DemoTourArtwork.density)
                draw(canvas, Paint(Paint.ANTI_ALIAS_FLAG))
            }

    fun pin(): Bitmap = image(48, 58) { canvas, paint ->
        val path = Path().apply {
            moveTo(24f, 55f)
            cubicTo(17f, 44f, 4f, 37f, 4f, 24f)
            arcTo(4f, 4f, 44f, 44f, 180f, 180f, false)
            cubicTo(44f, 37f, 31f, 44f, 24f, 55f)
            close()
        }
        paint.color = ACCENT
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawPath(path, paint)
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        // Fork and knife, matching the restaurant pin in the iOS tour.
        canvas.drawLine(17f, 15f, 17f, 21f, paint)
        canvas.drawLine(21f, 15f, 21f, 33f, paint)
        canvas.drawLine(25f, 15f, 25f, 21f, paint)
        canvas.drawLine(17f, 21f, 25f, 21f, paint)
        canvas.drawLine(32f, 15f, 32f, 33f, paint)
        canvas.drawLine(29f, 15f, 29f, 24f, paint)
        canvas.drawLine(29f, 24f, 32f, 24f, paint)
    }

    fun startDot(): Bitmap = image(18, 18) { canvas, paint ->
        paint.color = Color.WHITE
        canvas.drawCircle(9f, 9f, 9f, paint)
        paint.color = ACCENT
        canvas.drawCircle(9f, 9f, 5f, paint)
    }

    fun finishRing(): Bitmap = image(36, 36) { canvas, paint ->
        paint.color = ACCENT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawCircle(18f, 18f, 16f, paint)
    }

    fun userDot(): Bitmap = image(60, 60) { canvas, paint ->
        paint.color = Color.argb(38, 46, 120, 247)
        canvas.drawCircle(30f, 30f, 24f, paint)
        paint.color = Color.WHITE
        paint.setShadowLayer(3f, 0f, 2f, Color.argb(64, 33, 43, 61))
        canvas.drawCircle(30f, 30f, 12f, paint)
        paint.clearShadowLayer()
        paint.color = USER_BLUE
        canvas.drawCircle(30f, 30f, 9f, paint)
    }

    fun userArrow(): Bitmap = image(60, 60) { canvas, paint ->
        val triangle = Path().apply {
            moveTo(30f, 1f)
            lineTo(23f, 14f)
            quadTo(30f, 11f, 37f, 14f)
            close()
        }
        paint.color = USER_BLUE
        canvas.drawPath(triangle, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(triangle, paint)
    }

    fun phaseIcon(phase: Int): Bitmap = image(24, 24) { canvas, paint ->
        paint.color = ACCENT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        when (phase) {
            0 -> {
                canvas.drawCircle(10f, 10f, 6f, paint)
                canvas.drawLine(15f, 15f, 21f, 21f, paint)
            }

            1 -> {
                canvas.drawCircle(14f, 4f, 2f, paint)
                canvas.drawLines(
                    floatArrayOf(
                        13f, 8f, 10f, 15f, 10f, 15f, 6f, 22f,
                        10f, 15f, 16f, 21f, 12f, 10f, 19f, 13f, 12f, 10f, 6f, 12f
                    ),
                    paint
                )
            }

            2 -> canvas.drawPath(
                Path().apply {
                    moveTo(12f, 2f)
                    lineTo(21f, 22f)
                    lineTo(12f, 17f)
                    lineTo(3f, 22f)
                    close()
                },
                paint
            )

            else -> {
                canvas.drawCircle(12f, 12f, 10f, paint)
                canvas.drawLines(floatArrayOf(6f, 12f, 10f, 16f, 10f, 16f, 18f, 8f), paint)
            }
        }
    }
}
