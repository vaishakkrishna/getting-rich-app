package com.example.gettingrichapp.session

import com.example.gettingrichapp.audio.AdviceSpeaker
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.detection.DetectedCard
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

            // 2. Detect cards (use 0.60 threshold — the model is undertrained
            //    and typically produces confidence scores in the 0.65-0.80 range)
            val detectionResult = cardDetector.detect(frameData.bitmap, confidenceThreshold = 0.60f)

            if (detectionResult.cards.isEmpty()) {
                _sessionState.value = SessionState.Error("No cards detected")
                return
            }

            // 3. Deduplicate detections — each physical card has two number corners,
            //    so the model often produces two bounding boxes for the same card.
            //    Group by card identity and keep the highest-confidence detection.
            val dedupedDetections = deduplicateDetections(detectionResult.cards)

            if (dedupedDetections.size < 3) {
                _sessionState.value = SessionState.Error(
                    "Need at least 3 cards (2 player + 1 dealer), detected ${dedupedDetections.size}"
                )
                return
            }

            // 4. Split into player/dealer by bounding box area.
            //    The dealer's card is farther away → smallest bbox.
            //    The two largest bboxes are the player's cards.
            val (playerDetections, dealerDetection) = splitPlayerAndDealer(dedupedDetections)
            val playerCards = playerDetections.map { it.card }
            val dealerUpcard = dealerDetection.card

            // 5. Process through counter (dedup + update count)
            cardCounter.processDetectedCards(playerCards + dealerUpcard)

            // 6. Evaluate hand
            val handEvaluation = handEvaluator.evaluate(playerCards)

            // 7. Get recommendation
            val action = strategyEngine.recommend(handEvaluation, dealerUpcard, playerCards.size)

            // 8. Build advice
            val countState = cardCounter.countState.value
            val advice = Advice(
                action = action,
                playerCards = playerCards,
                dealerUpcard = dealerUpcard,
                handEvaluation = handEvaluation,
                runningCount = countState.runningCount,
                trueCount = countState.trueCount
            )

            // 9. Update round state
            _roundState.value = RoundState(
                playerCards = playerCards,
                dealerUpcard = dealerUpcard,
                currentAdvice = advice
            )

            // 10. Speak advice
            adviceSpeaker.speak(advice)

            // 11. Update session state
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
     * Deduplicate detections of the same card. A physical card has two number
     * corners, so the model often fires twice for one card. Group detections
     * by card identity (value + suit) and keep the one with highest confidence.
     */
    private fun deduplicateDetections(detections: List<DetectedCard>): List<DetectedCard> {
        return detections
            .groupBy { it.card }
            .map { (_, group) -> group.maxBy { it.confidence } }
    }

    /**
     * Split detected cards into player hand and dealer upcard using bounding
     * box area. The dealer's card is farther away and appears smaller in frame.
     * Sort by bbox area descending — the two largest are the player's cards,
     * the smallest is the dealer upcard.
     */
    private fun splitPlayerAndDealer(
        detections: List<DetectedCard>
    ): Pair<List<DetectedCard>, DetectedCard> {
        val sorted = detections.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
        val playerDetections = sorted.take(2)
        val dealerDetection = sorted.last()
        return playerDetections to dealerDetection
    }
}
