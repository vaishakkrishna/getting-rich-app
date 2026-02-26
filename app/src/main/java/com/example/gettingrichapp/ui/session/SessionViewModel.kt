package com.example.gettingrichapp.ui.session

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gettingrichapp.ServiceLocator
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.camera.StreamState
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.detection.DetectedCard
import com.example.gettingrichapp.session.GameSession
import com.example.gettingrichapp.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionViewModel(
    private val gameSession: GameSession,
    private val cardCounter: CardCounter,
    private val frameProvider: FrameProvider,
    private val cardDetector: CardDetector
) : ViewModel() {

    private val _showResetConfirmation = MutableStateFlow(false)

    private val _previewFrame = MutableStateFlow<Bitmap?>(null)
    val previewFrame: StateFlow<Bitmap?> = _previewFrame.asStateFlow()

    private val _detectionOverlays = MutableStateFlow<List<DetectedCard>>(emptyList())
    val detectionOverlays: StateFlow<List<DetectedCard>> = _detectionOverlays.asStateFlow()

    private val _frameSize = MutableStateFlow(Pair(0, 0))
    val frameSize: StateFlow<Pair<Int, Int>> = _frameSize.asStateFlow()

    private var previewJob: Job? = null
    private var detectionJob: Job? = null

    val uiState: StateFlow<SessionUiState> = combine(
        gameSession.sessionState,
        gameSession.roundState,
        cardCounter.countState,
        _showResetConfirmation,
        frameProvider.streamState
    ) { sessionState, roundState, countState, showReset, streamState ->
        SessionUiState(
            sessionState = sessionState,
            playerCards = roundState.playerCards,
            dealerUpcard = roundState.dealerUpcard,
            recommendedAction = roundState.currentAdvice?.action,
            runningCount = countState.runningCount,
            trueCount = countState.trueCount,
            isAdvising = sessionState is SessionState.Analyzing,
            showResetConfirmation = showReset,
            isPreviewActive = streamState == StreamState.STREAMING
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SessionUiState()
    )

    fun startSession() {
        // Subscribe to frameFlow BEFORE starting the stream so that
        // collectors are already active when the first frame arrives.
        startPreviewLoop()
        startDetectionLoop()
        viewModelScope.launch {
            gameSession.startSession()
        }
    }

    private fun startPreviewLoop() {
        previewJob = viewModelScope.launch {
            frameProvider.frameFlow
                .conflate()
                .collect { frameData ->
                    _previewFrame.value = frameData.bitmap
                    _frameSize.value = Pair(frameData.width, frameData.height)
                }
        }
    }

    private fun startDetectionLoop() {
        detectionJob = viewModelScope.launch(Dispatchers.Default) {
            frameProvider.frameFlow
                .conflate()
                .collect { frameData ->
                    try {
                        val result = cardDetector.detect(frameData.bitmap, 0.60f)
                        _detectionOverlays.value = result.cards
                        if (result.cards.isNotEmpty()) {
                            Log.d("SessionVM", "Detected ${result.cards.size} cards in ${result.processingTimeMs}ms: ${result.cards.map { "${it.card} (${it.confidence})" }}")
                        }
                    } catch (e: Exception) {
                        Log.e("SessionVM", "Detection error", e)
                    }
                }
        }
    }

    fun onAdvise() {
        viewModelScope.launch {
            gameSession.advise()
        }
    }

    fun onNextHand() {
        gameSession.nextHand()
    }

    fun onResetCount() {
        _showResetConfirmation.value = true
    }

    fun onResetCountConfirmed() {
        _showResetConfirmation.value = false
        gameSession.resetCount()
    }

    fun onResetCountDismissed() {
        _showResetConfirmation.value = false
    }

    fun onStopSession() {
        previewJob?.cancel()
        previewJob = null
        detectionJob?.cancel()
        detectionJob = null
        _previewFrame.value = null
        _detectionOverlays.value = emptyList()
        viewModelScope.launch {
            gameSession.stopSession()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SessionViewModel(
                    gameSession = ServiceLocator.gameSession,
                    cardCounter = ServiceLocator.cardCounter,
                    frameProvider = ServiceLocator.frameProvider,
                    cardDetector = ServiceLocator.cardDetector
                )
            }
        }
    }
}
