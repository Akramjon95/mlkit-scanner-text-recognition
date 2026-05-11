package com.maxcoder.lastmlkitscanner.presentation.scanner

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class ScannerViewModel @Inject constructor(
    private val getScanProgress: GetScanProgressUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerContract.State())
    val state: StateFlow<ScannerContract.State> = _state.asStateFlow()

    private val _effects = Channel<ScannerContract.Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            getScanProgress().collect { progress ->
                _state.update { it.copy(voteCount = progress.voteCount, totalVotes = progress.total) }
                if (progress.number.isNotEmpty()) {
                    _state.update { it.copy(cardDetected = true, voteCount = 2) }
                    _effects.send(ScannerContract.Effect.NavigateToResult(progress))
                }
            }
        }
    }

    fun onEvent(event: ScannerContract.Event) {
        when (event) {
            ScannerContract.Event.ToggleTorch ->
                _state.update { it.copy(isTorchOn = !it.isTorchOn) }
            ScannerContract.Event.Close ->
                viewModelScope.launch { _effects.send(ScannerContract.Effect.NavigateBack) }
        }
    }

    fun reset() {
        getScanProgress.reset()
        _state.value = ScannerContract.State()
    }

    fun imageAnalyzer(): ImageAnalysis.Analyzer {
        return getScanProgress.imageAnalyzer()
    }
}