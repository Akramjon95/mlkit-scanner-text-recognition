package com.maxcoder.lastmlkitscanner.domain.repository

import androidx.camera.core.ImageAnalysis
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import kotlinx.coroutines.flow.StateFlow

interface CardScanRepository {
    val scanProgress: StateFlow<CardData>
    fun reset()
    fun imageAnalyzer(): ImageAnalysis.Analyzer
    fun setFrameHint(screenW: Int, screenH: Int)
}