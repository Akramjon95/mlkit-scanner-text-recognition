package com.maxcoder.lastmlkitscanner.domain.usecase

import androidx.camera.core.ImageAnalysis
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import com.maxcoder.lastmlkitscanner.domain.repository.CardScanRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetScanProgressUseCase @Inject constructor(
    private val repository: CardScanRepository
) {
    operator fun invoke(): StateFlow<CardData> = repository.scanProgress
    fun reset() = repository.reset()
    fun imageAnalyzer(): ImageAnalysis.Analyzer = repository.imageAnalyzer()
    fun setFrameHint(screenW: Int, screenH: Int) = repository.setFrameHint(screenW, screenH)
}