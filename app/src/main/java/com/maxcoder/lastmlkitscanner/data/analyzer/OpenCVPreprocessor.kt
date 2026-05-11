package com.maxcoder.lastmlkitscanner.data.analyzer

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OpenCV-based preprocessing. Call [init] once at app startup via [App].
 * Every method returns null gracefully if OpenCV is unavailable so
 * [CardImagePreprocessor] falls back to its manual implementations.
 */
object OpenCVPreprocessor {

    private const val TAG = "OpenCVPreprocessor"

    @Volatile var isReady = false
        private set

    fun init() {
        isReady = try {
            OpenCVLoader.initDebug().also { ok ->
                if (ok) Log.d(TAG, "OpenCV initialised successfully")
                else    Log.w(TAG, "OpenCV init returned false — falling back to manual processing")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV native library missing: ${e.message}")
            false
        }
    }

    fun claheEnhanced(bitmap: Bitmap): Bitmap? = runCVOp("claheEnhanced") {
        val rgba = bitmap.toMat()
        val gray = rgba.cvtGray()
        val enhanced = Mat()
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(gray, enhanced)
        val result = enhanced.grayToRgba().toBitmap(bitmap.width, bitmap.height)
        listOf(rgba, gray, enhanced).releaseAll()
        result
    }

    fun claheAdaptive(bitmap: Bitmap): Bitmap? = runCVOp("claheAdaptive") {
        val rgba = bitmap.toMat()
        val gray = rgba.cvtGray()
        val enhanced = Mat()
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(gray, enhanced)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            enhanced, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 10.0
        )
        val result = binary.grayToRgba().toBitmap(bitmap.width, bitmap.height)
        listOf(rgba, gray, enhanced, binary).releaseAll()
        result
    }

    fun cannyEdges(bitmap: Bitmap): Bitmap? = runCVOp("cannyEdges") {
        val rgba = bitmap.toMat()
        val gray = rgba.cvtGray()
        val enhanced = Mat()
        Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(gray, enhanced)
        val blurred = Mat()
        Imgproc.GaussianBlur(enhanced, blurred, Size(3.0, 3.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        Core.bitwise_not(edges, edges)
        val result = edges.grayToRgba().toBitmap(bitmap.width, bitmap.height)
        listOf(rgba, gray, enhanced, blurred, edges).releaseAll()
        result
    }

    private inline fun runCVOp(name: String, block: () -> Bitmap): Bitmap? {
        if (!isReady) return null
        return runCatching(block)
            .onFailure { Log.w(TAG, "$name failed: ${it.message}") }
            .getOrNull()
    }

    private fun Bitmap.toMat(): Mat { val mat = Mat(); Utils.bitmapToMat(this, mat); return mat }
    private fun Mat.cvtGray(): Mat { val g = Mat(); Imgproc.cvtColor(this, g, Imgproc.COLOR_RGBA2GRAY); return g }
    private fun Mat.grayToRgba(): Mat { val r = Mat(); Imgproc.cvtColor(this, r, Imgproc.COLOR_GRAY2RGBA); return r }
    private fun Mat.toBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bmp); release(); return bmp
    }
    private fun List<Mat>.releaseAll() = forEach { it.release() }
}