package com.maxcoder.lastmlkitscanner.presentation.result

import com.maxcoder.lastmlkitscanner.domain.model.CardData

object ResultContract {

    data class State(
        val card: CardData
    )

    sealed class Event {
        object Confirm : Event()
        object Rescan : Event()
    }

    sealed class Effect {
        object NavigateBack : Effect()
    }
}