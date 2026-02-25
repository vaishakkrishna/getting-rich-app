package com.example.gettingrichapp

import android.content.Context
import com.example.gettingrichapp.audio.AdviceSpeaker
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.camera.MockFrameProvider
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.counting.CountState
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.detection.StubCardDetector
import com.example.gettingrichapp.glasses.GlassesConnection
import com.example.gettingrichapp.glasses.MockGlassesConnection
import com.example.gettingrichapp.model.Advice
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.session.GameSession
import com.example.gettingrichapp.session.RoundState
import com.example.gettingrichapp.session.SessionState
import com.example.gettingrichapp.strategy.StrategyEngine
import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.HandEvaluation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ServiceLocator {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val glassesConnection: GlassesConnection by lazy { MockGlassesConnection() }

    val frameProvider: FrameProvider by lazy { MockFrameProvider() }

    val cardDetector: CardDetector by lazy { StubCardDetector() }

    val cardCounter: CardCounter by lazy { StubCardCounter() }

    val strategyEngine: StrategyEngine by lazy { StubStrategyEngine() }

    val adviceSpeaker: AdviceSpeaker by lazy { StubAdviceSpeaker() }

    val gameSession: GameSession by lazy { StubGameSession() }
}

// Stub implementations for Phase 0 — will be replaced with real impls in later phases

private class StubCardCounter : CardCounter {
    private val _countState = MutableStateFlow(CountState())
    override val countState: StateFlow<CountState> = _countState
    override fun setNumDecks(numDecks: Int) {
        _countState.value = _countState.value.copy(numDecks = numDecks)
    }
    override fun processDetectedCards(detectedCards: List<Card>): List<Card> = detectedCards
    override fun nextHand() {}
    override fun resetCount() {
        _countState.value = CountState(numDecks = _countState.value.numDecks)
    }
}

private class StubStrategyEngine : StrategyEngine {
    override fun recommend(playerHand: HandEvaluation, dealerUpcard: Card, handSize: Int): Action {
        return Action.STAND
    }
}

private class StubAdviceSpeaker : AdviceSpeaker {
    override fun initialize() {}
    override fun speak(advice: Advice) {}
    override var includeCountInSpeech: Boolean = false
    override fun release() {}
}

private class StubGameSession : GameSession {
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    override val sessionState: StateFlow<SessionState> = _sessionState

    private val _roundState = MutableStateFlow(RoundState())
    override val roundState: StateFlow<RoundState> = _roundState

    override suspend fun startSession() {
        _sessionState.value = SessionState.Streaming
    }
    override suspend fun advise() {}
    override fun nextHand() {
        _roundState.value = RoundState()
    }
    override fun resetCount() {}
    override suspend fun stopSession() {
        _sessionState.value = SessionState.Idle
        _roundState.value = RoundState()
    }
}
