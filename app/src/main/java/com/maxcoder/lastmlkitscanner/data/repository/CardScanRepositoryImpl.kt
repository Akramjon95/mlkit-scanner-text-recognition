package com.maxcoder.lastmlkitscanner.data.repository

import com.maxcoder.lastmlkitscanner.data.analyzer.CardAnalyzer
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import com.maxcoder.lastmlkitscanner.domain.repository.CardScanRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardScanRepositoryImpl @Inject constructor(
    private val analyzer: CardAnalyzer
) : CardScanRepository {

    override val scanProgress: StateFlow<CardData> = analyzer.progress
    override fun reset() = analyzer.reset()
    override fun imageAnalyzer() = analyzer
}