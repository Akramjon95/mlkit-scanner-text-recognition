package com.maxcoder.lastmlkitscanner.data.analyzer

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.maxcoder.lastmlkitscanner.core.extensions.toRotatedBitmap
import com.maxcoder.lastmlkitscanner.core.utils.validateLuhn
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardAnalyzer @Inject constructor() : ImageAnalysis.Analyzer {

    private val _progress = MutableStateFlow(CardData())
    val progress: StateFlow<CardData> = _progress.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val resultsMap = mutableMapOf<String, Int>()
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile private var isLocked = false
    private var lastProcessedTime = 0L
    @Volatile private var frameScreenW = 0
    @Volatile private var frameScreenH = 0

    companion object {
        private const val VOTE_THRESHOLD = 2
        private const val THROTTLE_MS = 1000L
        private const val TAG = "CardAnalyzer"
    }

    fun setFrameHint(screenW: Int, screenH: Int) {
        frameScreenW = screenW
        frameScreenH = screenH
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isLocked) { imageProxy.close(); return }

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < THROTTLE_MS) { imageProxy.close(); return }
        lastProcessedTime = now

        val bitmap = imageProxy.toRotatedBitmap() ?: run { imageProxy.close(); return }
        val frameTarget = if (frameScreenW > 0 && frameScreenH > 0)
            cropToFrame(bitmap, frameScreenW, frameScreenH)
        else bitmap

        executor.execute {
            val variations = CardImagePreprocessor.preprocessAllVariations(frameTarget)
            recognizer.process(InputImage.fromBitmap(variations[0], 0))
                .addOnSuccessListener { visionText ->
                    if (!isLocked) {
                        val number = findCardNumber(visionText)
                        if (number != null) handleDetected(number, visionText)
                        else tryRemainingVariations(variations, 1)
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    fun reset() {
        isLocked = false
        lastProcessedTime = 0L
        resultsMap.clear()
    }

    private fun cropToFrame(bitmap: Bitmap, screenW: Int, screenH: Int): Bitmap {
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        // PreviewView FILL_CENTER: scale by max factor so the frame fully covers the view
        val scale = maxOf(screenW / bmpW, screenH / bmpH)
        val offsetX = (screenW - bmpW * scale) / 2f
        val offsetY = (screenH - bmpH * scale) / 2f

        val frameW = screenW * 0.85f
        val frameH = frameW / 1.586f
        val frameLeft = (screenW - frameW) / 2f
        val frameTop = (screenH - frameH) / 2f

        val cropLeft = ((frameLeft - offsetX) / scale).toInt().coerceIn(0, bitmap.width - 1)
        val cropTop = ((frameTop - offsetY) / scale).toInt().coerceIn(0, bitmap.height - 1)
        val cropRight = ((frameLeft + frameW - offsetX) / scale).toInt().coerceIn(cropLeft + 1, bitmap.width)
        val cropBottom = ((frameTop + frameH - offsetY) / scale).toInt().coerceIn(cropTop + 1, bitmap.height)

        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
    }

    private fun tryRemainingVariations(variations: List<Bitmap>, index: Int) {
        if (index >= variations.size || isLocked) return
        recognizer.process(InputImage.fromBitmap(variations[index], 0))
            .addOnSuccessListener { visionText ->
                if (!isLocked) {
                    val number = findCardNumber(visionText)
                    if (number != null) handleDetected(number, visionText)
                    else tryRemainingVariations(variations, index + 1)
                }
            }
    }

    private fun handleDetected(number: String, visionText: Text) {
        val count = resultsMap.getOrDefault(number, 0) + 1
        resultsMap[number] = count
        Log.d(TAG, "Detected $number ($count/$VOTE_THRESHOLD)")
        _progress.tryEmit(CardData(voteCount = count, total = VOTE_THRESHOLD))
        if (count >= VOTE_THRESHOLD) {
            isLocked = true
            val card = CardData(number = number, expiry = findDate(visionText))
            _progress.tryEmit(card)
            resultsMap.clear()
        }
    }
}

// ── OCR helpers ───────────────────────────────────────────────────────────────

private fun String.fixOCR(): String = this
    .replace('O', '0').replace('o', '0')
    .replace('B', '8').replace('b', '8')
    .replace('S', '5').replace('s', '5')
    .replace('I', '1').replace('l', '1')
    .replace('G', '6')
    .replace('Z', '2')
    .replace(" ", "").replace("-", "").replace(".", "")
    .trim()

private fun findCardNumber(visionText: Text): String? {
    // Strategy 1: line-by-line 16-digit match
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            val fixed = line.text.fixOCR()
            if (fixed.length == 16 && fixed.all { it.isDigit() }) {
                if (validateLuhn(fixed)) return fixed
                bruteForceCorrect(fixed)?.let { return it }
            }
        }
    }

    // Strategy 2: four consecutive 4-digit groups
    val fourDigitGroups = mutableListOf<String>()
    for (block in visionText.textBlocks) {
        for (line in block.lines) {
            for (element in line.elements) {
                val fixed = element.text.fixOCR()
                if (fixed.length == 4 && fixed.all { it.isDigit() }) {
                    fourDigitGroups.add(fixed)
                }
            }
        }
    }
    if (fourDigitGroups.size >= 4) {
        for (i in 0..fourDigitGroups.size - 4) {
            val candidate = fourDigitGroups.subList(i, i + 4).joinToString("")
            if (validateLuhn(candidate)) return candidate
            bruteForceCorrect(candidate)?.let { return it }
        }
    }

    // Strategy 3: merge all digits and regex-search for 16-digit run
    val allDigits = visionText.textBlocks.joinToString("") { block ->
        block.text.fixOCR().filter { it.isDigit() }
    }
    val match = Regex("\\d{16}").find(allDigits)
    if (match != null) {
        if (validateLuhn(match.value)) return match.value
        return bruteForceCorrect(match.value)
    }

    return null
}

private fun bruteForceCorrect(number: String): String? {
    if (number.length != 16) return null
    val similar = mapOf(
        '0' to listOf('8', '6', '9'),
        '1' to listOf('7', '4'),
        '2' to listOf('7'),
        '3' to listOf('8'),
        '4' to listOf('1', '9'),
        '5' to listOf('6', '8'),
        '6' to listOf('0', '8', '9'),
        '7' to listOf('1'),
        '8' to listOf('0', '3', '6', '9'),
        '9' to listOf('0', '4', '6', '8')
    )
    // Single-digit correction
    for (i in number.indices) {
        for (c in similar[number[i]] ?: continue) {
            val corrected = number.toCharArray().also { it[i] = c }.concatToString()
            if (validateLuhn(corrected)) return corrected
        }
    }
    // Two-digit correction
    for (i in number.indices) {
        for (j in i + 1 until number.length) {
            for (c1 in similar[number[i]] ?: continue) {
                for (c2 in similar[number[j]] ?: continue) {
                    val corrected = number.toCharArray().also { it[i] = c1; it[j] = c2 }.concatToString()
                    if (validateLuhn(corrected)) return corrected
                }
            }
        }
    }
    return null
}

private fun findDate(visionText: Text): String {
    return Regex("(0[1-9]|1[0-2])/[0-9]{2}").find(visionText.text)?.value ?: ""
}