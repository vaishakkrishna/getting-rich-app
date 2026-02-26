package com.example.gettingrichapp.session

import com.example.gettingrichapp.audio.AdviceSpeaker
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.model.Advice
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.strategy.HandEvaluator
import com.example.gettingrichapp.strategy.StrategyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BlackjackSession(
    private val frameProvider: FrameProvider,
    private val cardDetector: CardDetector,
    private val cardCounter: CardCounter,
    private val handEvaluator: HandEvaluator,
    private val strategyEngine: StrategyEngine,
    private val adviceSpeaker: AdviceSpeaker
) : GameSession {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    override val sessionState: StateFlow<SessionState> = _sessionState

    private val _roundState = MutableStateFlow(RoundState())
    override val roundState: StateFlow<RoundState> = _roundState

    override suspend fun startSession() {
        try {
            frameProvider.startStream()
            _sessionState.value = SessionState.Streaming
        } catch (e: Exception) {
            _sessionState.value = SessionState.Error("Failed to start stream: ${e.message}")
        }
    }

    override suspend fun advise() {
        if (_sessionState.value !is SessionState.Streaming &&
            _sessionState.value !is SessionState.AdviceReady
        ) return

        _sessionState.value = SessionState.Analyzing

        try {
            // 1. Capture frame
            val frameData = frameProvider.captureFrame()
            if (frameData == null) {
                _sessionState.value = SessionState.Error("No frame available")
                return
            }

            // 2. Detect cards
            val detectionResult = cardDetector.detect(frameData.bitmap)
            val detectedCards = detectionResult.cards.map { it.card }

            if (detectedCards.isEmpty()) {
                _sessionState.value = SessionState.Error("No cards detected")
                return
            }

            // 3. Process through counter (dedup + update count)
            cardCounter.processDetectedCards(detectedCards)

            // 4. Get all cards seen this round from counter state
            val countState = cardCounter.countState.value
            val allRoundCards = countState.cardsSeenThisRound

            // Separate player cards and dealer upcard
            // Convention: the last card detected that differs from the player hand
            // is considered the dealer upcard. For simplicity, if we have >=2 cards,
            // treat all but the last as player cards and the last as dealer upcard.
            val (playerCards, dealerUpcard) = splitPlayerAndDealer(allRoundCards)

            // 5. Evaluate hand
            val handEvaluation = handEvaluator.evaluate(playerCards)

            // 6. Get recommendation (only if we have a dealer upcard)
            val action = if (dealerUpcard != null) {
                strategyEngine.recommend(handEvaluation, dealerUpcard, playerCards.size)
            } else {
                // Can't recommend without dealer upcard, just show cards
                null
            }

            // 7. Build advice
            val advice = Advice(
                action = action ?: com.example.gettingrichapp.model.Action.STAND,
                playerCards = playerCards,
                dealerUpcard = dealerUpcard,
                handEvaluation = handEvaluation,
                runningCount = countState.runningCount,
                trueCount = countState.trueCount
            )

            // 8. Update round state
            _roundState.value = RoundState(
                playerCards = playerCards,
                dealerUpcard = dealerUpcard,
                currentAdvice = advice
            )

            // 9. Speak advice (only if we have a recommendation)
            if (action != null) {
                adviceSpeaker.speak(advice)
            }

            // 10. Update session state
            _sessionState.value = SessionState.AdviceReady(advice)

        } catch (e: Exception) {
            _sessionState.value = SessionState.Error("Advise failed: ${e.message}")
        }
    }

    override fun nextHand() {
        cardCounter.nextHand()
        _roundState.value = RoundState()
        if (_sessionState.value !is SessionState.Error) {
            _sessionState.value = SessionState.Streaming
        }
    }

    override fun resetCount() {
        cardCounter.resetCount()
        _roundState.value = RoundState()
        if (_sessionState.value !is SessionState.Error) {
            _sessionState.value = SessionState.Streaming
        }
    }

    override suspend fun stopSession() {
        frameProvider.stopStream()
        _sessionState.value = SessionState.Idle
        _roundState.value = RoundState()
    }

    /**
     * Split detected cards into player hand and dealer upcard.
     *
     * Heuristic: if we have 3+ cards, the first card is the dealer upcard
     * and the rest are the player's hand. With 2 cards, both are player cards
     * (dealer upcard not yet detected). With 1 card, it's a player card.
     */
    private fun splitPlayerAndDealer(cards: List<Card>): Pair<List<Card>, Card?> {
        return when {
            cards.size >= 3 -> {
                val dealerUpcard = cards.first()
                val playerCards = cards.drop(1)
                playerCards to dealerUpcard
            }
            else -> cards to null
        }
    }
}
