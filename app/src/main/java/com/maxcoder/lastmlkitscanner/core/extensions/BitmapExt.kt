package com.maxcoder.lastmlkitscanner.core.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toRotatedBitmap(): Bitmap? {
    val nv21 = yuv420888ToNv21(this)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    return bitmap?.let {
        val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
        Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
    }
}

private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val pixelCount = image.width * image.height
    val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
    val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
    val yBuffer = image.planes[0].buffer
    val vBuffer = image.planes[2].buffer
    yBuffer.get(outputBuffer, 0, pixelCount)
    vBuffer.get(outputBuffer, pixelCount, vBuffer.remaining())
    return outputBuffer
}