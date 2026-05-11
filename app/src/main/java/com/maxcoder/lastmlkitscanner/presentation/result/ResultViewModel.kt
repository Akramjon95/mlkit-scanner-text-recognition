package com.maxcoder.lastmlkitscanner.presentation.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxcoder.lastmlkitscanner.domain.model.CardData
import com.maxcoder.lastmlkitscanner.domain.usecase.GetScanProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanProgress: GetScanProgressUseCase,
) : ViewModel() {

    // Compose Destinations stores Parcelable nav args in SavedStateHandle keyed by property name
    private val card: CardData = savedStateHandle.get<CardData>("card")
        ?: error("ResultScreen requires a 'card' nav argument")

    private val _state = MutableStateFlow(ResultContract.State(card = card))
    val state: StateFlow<ResultContract.State> = _state.asStateFlow()

    private val _effects = Channel<ResultContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: ResultContract.Event) {
       when(event) {
           ResultContract.Event.Confirm -> viewModelScope.launch {
               _state.update { it.copy(card = CardData(number = "", expiry = "")) }
               _effects.send(ResultContract.Effect.NavigateBack)
               getScanProgress.reset()
           }
           ResultContract.Event.Rescan -> viewModelScope.launch {
               _state.update { it.copy(card = CardData(number = "", expiry = "")) }
               _effects.send(ResultContract.Effect.NavigateBack)
               getScanProgress.reset()
           }
       }
    }
}