package com.maxcoder.lastmlkitscanner.data.analyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.sqrt

object CardImagePreprocessor {

    fun preprocessCardImage(bitmap: Bitmap): Bitmap = enhanceForEmbossed(bitmap)

    /**
     * Returns up to 13 variations ordered from cheapest/most-reliable to most specialised.
     * CardAnalyzer stops at the first Luhn-valid hit so flat printed cards exit early.
     *
     *  1. original
     *  2. grayscale + high contrast + sharpen
     *  3. OpenCV CLAHE
     *  4. OpenCV CLAHE + adaptive threshold        (dark text)
     *  5. OpenCV CLAHE + adaptive threshold inv.   (white text)
     *  6. OpenCV CLAHE + Canny edges
     *  7. OpenCV CLAHE + Canny edges inverted
     *  8. inverted enhanced
     *  9. high contrast
     * 10. manual CLAHE
     * 11. manual CLAHE + adaptive threshold
     * 12. manual CLAHE + adaptive threshold inv.
     * 13. manual CLAHE + Sobel edges
     */
    fun preprocessAllVariations(bitmap: Bitmap): List<Bitmap> {
        val enhanced       = enhanceForEmbossed(bitmap)
        val invertEnhanced = invertBitmap(enhanced)
        val highContrast   = sharpen(applyContrast(toGrayscale(bitmap), 3.0f, -150f))
        val clahe          = applyCLAHE(bitmap)
        val claheThresh    = applyAdaptiveThreshold(clahe)
        val claheThreshInv = invertBitmap(claheThresh)
        val edgeEnhanced   = applyEdgeDetection(clahe)

        val cvClahe        = OpenCVPreprocessor.claheEnhanced(bitmap)
        val cvAdaptive     = OpenCVPreprocessor.claheAdaptive(bitmap)
        val cvAdaptiveInv  = cvAdaptive?.let { invertBitmap(it) }
        val cvCanny        = OpenCVPreprocessor.cannyEdges(bitmap)
        val cvCannyInv     = cvCanny?.let { invertBitmap(it) }

        return listOfNotNull(
            bitmap,
            enhanced,
            cvClahe,
            cvAdaptive,
            cvAdaptiveInv,
            cvCanny,
            cvCannyInv,
            invertEnhanced,
            highContrast,
            clahe,
            claheThresh,
            claheThreshInv,
            edgeEnhanced
        )
    }

    // ── CLAHE ─────────────────────────────────────────────────────────────────

    /**
     * Contrast Limited Adaptive Histogram Equalisation.
     * Divides the image into [tileSize]×[tileSize] tiles, builds a clip-limited
     * histogram LUT per tile, then bilinearly interpolates the four surrounding
     * tile LUTs for every output pixel.
     */
    fun applyCLAHE(bitmap: Bitmap, tileSize: Int = 64, clipLimit: Float = 4.0f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        toGrayscale(bitmap).getPixels(src, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { (src[it] shr 16) and 0xFF }

        val tilesX = (w + tileSize - 1) / tileSize
        val tilesY = (h + tileSize - 1) / tileSize
        val luts = Array(tilesY) { ty ->
            Array(tilesX) { tx ->
                buildCLAHELut(
                    gray, w,
                    tx * tileSize, ty * tileSize,
                    minOf((tx + 1) * tileSize, w),
                    minOf((ty + 1) * tileSize, h),
                    clipLimit
                )
            }
        }

        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val fx = x.toFloat() / tileSize - 0.5f
                val fy = y.toFloat() / tileSize - 0.5f
                val tx0 = fx.toInt().coerceIn(0, tilesX - 1)
                val ty0 = fy.toInt().coerceIn(0, tilesY - 1)
                val tx1 = (tx0 + 1).coerceIn(0, tilesX - 1)
                val ty1 = (ty0 + 1).coerceIn(0, tilesY - 1)
                val wx = (fx - tx0).coerceIn(0f, 1f)
                val wy = (fy - ty0).coerceIn(0f, 1f)
                val v = gray[y * w + x]
                val mapped = (
                    luts[ty0][tx0][v] * (1 - wx) * (1 - wy) +
                    luts[ty0][tx1][v] * wx        * (1 - wy) +
                    luts[ty1][tx0][v] * (1 - wx)  * wy       +
                    luts[ty1][tx1][v] * wx         * wy
                ).toInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (mapped shl 16) or (mapped shl 8) or mapped
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(out, 0, w, 0, 0, w, h)
        }
    }

    private fun buildCLAHELut(
        gray: IntArray, imgW: Int,
        x0: Int, y0: Int, x1: Int, y1: Int,
        clipLimit: Float
    ): IntArray {
        val tilePixels = (x1 - x0) * (y1 - y0)
        val hist = IntArray(256)
        for (y in y0 until y1) for (x in x0 until x1) hist[gray[y * imgW + x]]++
        val clip = (clipLimit * tilePixels / 256).toInt().coerceAtLeast(1)
        var excess = 0
        for (i in 0..255) { if (hist[i] > clip) { excess += hist[i] - clip; hist[i] = clip } }
        val perBin = excess / 256
        val rem = excess % 256
        for (i in 0..255) hist[i] += perBin
        for (i in 0 until rem) hist[i]++
        val lut = IntArray(256)
        var cdf = 0; var cdfMin = 0; var first = true
        for (i in 0..255) {
            cdf += hist[i]
            if (first && cdf > 0) { cdfMin = cdf; first = false }
            lut[i] = if (tilePixels > cdfMin)
                ((cdf - cdfMin).toFloat() / (tilePixels - cdfMin) * 255f).toInt().coerceIn(0, 255)
            else 0
        }
        return lut
    }

    // ── Adaptive threshold ────────────────────────────────────────────────────

    /**
     * Mean adaptive threshold using a summed-area table for O(1) per-pixel queries.
     */
    fun applyAdaptiveThreshold(bitmap: Bitmap, blockSize: Int = 21, C: Int = 10): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { (src[it] shr 16) and 0xFF }
        val iw = w + 1
        val integral = IntArray(iw * (h + 1))
        for (y in 1..h) for (x in 1..w) {
            integral[y * iw + x] = gray[(y - 1) * w + (x - 1)] +
                integral[(y - 1) * iw + x] +
                integral[y * iw + (x - 1)] -
                integral[(y - 1) * iw + (x - 1)]
        }
        val half = blockSize / 2
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val x0 = (x - half).coerceAtLeast(0); val y0 = (y - half).coerceAtLeast(0)
            val x1 = (x + half).coerceAtMost(w - 1); val y1 = (y + half).coerceAtMost(h - 1)
            val area = (x1 - x0 + 1) * (y1 - y0 + 1)
            val sum = integral[(y1 + 1) * iw + (x1 + 1)] -
                      integral[y0 * iw + (x1 + 1)] -
                      integral[(y1 + 1) * iw + x0] +
                      integral[y0 * iw + x0]
            val px = if (gray[y * w + x] > sum / area - C) 255 else 0
            out[y * w + x] = (0xFF shl 24) or (px shl 16) or (px shl 8) or px
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
    }

    // ── Sobel edge detection ──────────────────────────────────────────────────

    /**
     * Sobel edge detector; output is inverted so raised edges become dark strokes on
     * a white background — the format ML Kit expects for text recognition.
     */
    fun applyEdgeDetection(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { (src[it] shr 16) and 0xFF }
        val out = IntArray(w * h) { (0xFF shl 24) or (255 shl 16) or (255 shl 8) or 255 }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val gx = (-gray[(y-1)*w+(x-1)] - 2*gray[y*w+(x-1)] - gray[(y+1)*w+(x-1)]
                      +gray[(y-1)*w+(x+1)] + 2*gray[y*w+(x+1)] + gray[(y+1)*w+(x+1)])
            val gy = (-gray[(y-1)*w+(x-1)] - 2*gray[(y-1)*w+x] - gray[(y-1)*w+(x+1)]
                      +gray[(y+1)*w+(x-1)] + 2*gray[(y+1)*w+x] + gray[(y+1)*w+(x+1)])
            val mag = sqrt((gx * gx + gy * gy).toDouble()).toInt().coerceIn(0, 255)
            val px = (255 - mag).coerceIn(0, 255)
            out[y * w + x] = (0xFF shl 24) or (px shl 16) or (px shl 8) or px
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun enhanceForEmbossed(bitmap: Bitmap): Bitmap =
        sharpen(applyContrast(toGrayscale(bitmap), 3.0f, -80f))

    fun invertBitmap(bitmap: Bitmap): Bitmap {
        val invertMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
             0f,-1f, 0f, 0f, 255f,
             0f, 0f,-1f, 0f, 255f,
             0f, 0f, 0f, 1f, 0f
        ))
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(invertMatrix)
        })
        return result
    }

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        })
        return result
    }

    fun increaseContrast(bitmap: Bitmap, contrast: Float = 2.5f, offset: Float = -120f): Bitmap =
        applyContrast(bitmap, contrast, offset)

    private fun applyContrast(bitmap: Bitmap, contrast: Float, offset: Float): Bitmap {
        val matrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return result
    }

    // Sharpening kernel (centre = 6):  [ 0 -1  0 / -1  6 -1 / 0 -1  0 ]
    fun sharpen(bitmap: Bitmap): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (x == 0 || x == w - 1 || y == 0 || y == h - 1) { out[y*w+x] = px[y*w+x]; continue }
            val c = px[y*w+x]; val t = px[(y-1)*w+x]; val b = px[(y+1)*w+x]
            val l = px[y*w+(x-1)]; val r = px[y*w+(x+1)]
            val nr = clamp(6*((c shr 16)and 0xFF)-((t shr 16)and 0xFF)-((b shr 16)and 0xFF)-((l shr 16)and 0xFF)-((r shr 16)and 0xFF))
            val ng = clamp(6*((c shr 8) and 0xFF)-((t shr 8) and 0xFF)-((b shr 8) and 0xFF)-((l shr 8) and 0xFF)-((r shr 8) and 0xFF))
            val nb = clamp(6*(c and 0xFF)-(t and 0xFF)-(b and 0xFF)-(l and 0xFF)-(r and 0xFF))
            out[y*w+x] = ((c shr 24 and 0xFF) shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(out, 0, w, 0, 0, w, h) }
    }

    private fun clamp(v: Int): Int = v.coerceIn(0, 255)
}