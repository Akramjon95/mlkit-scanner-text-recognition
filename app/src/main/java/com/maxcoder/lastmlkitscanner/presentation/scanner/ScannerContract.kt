package com.maxcoder.lastmlkitscanner.presentation.scanner

import com.maxcoder.lastmlkitscanner.domain.model.CardData

object ScannerContract {

    data class State(
        val voteCount: Int = 0,
        val totalVotes: Int = 2,
        val cardDetected: Boolean = false,
        val isTorchOn: Boolean = false
    )

    sealed class Event {
        object ToggleTorch : Event()
        object Close : Event()
    }

    sealed class Effect {
        data class NavigateToResult(val card: CardData) : Effect()
        object NavigateBack : Effect()
    }
}