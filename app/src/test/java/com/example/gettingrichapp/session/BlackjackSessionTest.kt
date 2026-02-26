package com.example.gettingrichapp.session

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.gettingrichapp.audio.AdviceSpeaker
import com.example.gettingrichapp.camera.FrameData
import com.example.gettingrichapp.camera.FrameProvider
import com.example.gettingrichapp.camera.StreamState
import com.example.gettingrichapp.counting.HiLoCardCounter
import com.example.gettingrichapp.detection.CardDetector
import com.example.gettingrichapp.detection.DetectedCard
import com.example.gettingrichapp.detection.DetectionResult
import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Advice
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit
import com.example.gettingrichapp.strategy.BasicStrategyEngine
import com.example.gettingrichapp.strategy.HandEvaluator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlackjackSessionTest {

    private lateinit var frameProvider: FakeFrameProvider
    private lateinit var cardDetector: FakeCardDetector
    private lateinit var cardCounter: HiLoCardCounter
    private lateinit var adviceSpeaker: FakeAdviceSpeaker
    private lateinit var session: BlackjackSession

    @Before
    fun setUp() {
        frameProvider = FakeFrameProvider()
        cardDetector = FakeCardDetector()
        cardCounter = HiLoCardCounter()
        adviceSpeaker = FakeAdviceSpeaker()
        session = BlackjackSession(
            frameProvider = frameProvider,
            cardDetector = cardDetector,
            cardCounter = cardCounter,
            handEvaluator = HandEvaluator,
            strategyEngine = BasicStrategyEngine(),
            adviceSpeaker = adviceSpeaker
        )
    }

    // --- Session lifecycle ---

    @Test
    fun `initial state is Idle`() {
        assertEquals(SessionState.Idle, session.sessionState.value)
    }

    @Test
    fun `startSession transitions to Streaming`() = runTest {
        session.startSession()
        assertEquals(SessionState.Streaming, session.sessionState.value)
    }

    @Test
    fun `stopSession transitions back to Idle`() = runTest {
        session.startSession()
        session.stopSession()
        assertEquals(SessionState.Idle, session.sessionState.value)
    }

    @Test
    fun `stopSession clears round state`() = runTest {
        session.startSession()

        // Player: 10S + 6H (largest bboxes), Dealer: 9C (smallest bbox)
        cardDetector.nextDetections = listOf(
            card(CardValue.TEN, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS),
            card(CardValue.NINE, Suit.CLUBS)
        )
        session.advise()
        assertTrue(session.roundState.value.playerCards.isNotEmpty())

        session.stopSession()
        assertEquals(RoundState(), session.roundState.value)
    }

    // --- Advise flow ---

    @Test
    fun `advise with 3 cards produces advice`() = runTest {
        session.startSession()

        // Player: 10S + 6H (largest bboxes), Dealer: 9C (smallest bbox)
        // hard 16 vs 9 → SURRENDER
        cardDetector.nextDetections = listOf(
            card(CardValue.TEN, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS),
            card(CardValue.NINE, Suit.CLUBS)
        )

        session.advise()

        val state = session.sessionState.value
        assertTrue("Expected AdviceReady but was $state", state is SessionState.AdviceReady)

        val advice = (state as SessionState.AdviceReady).advice
        assertEquals(Action.SURRENDER, advice.action)
        assertEquals(Card(CardValue.NINE, Suit.CLUBS), advice.dealerUpcard)
        assertEquals(2, advice.playerCards.size)
    }

    @Test
    fun `advise with less than 3 cards produces error`() = runTest {
        session.startSession()

        // Only 2 cards — need at least 3 (2 player + 1 dealer)
        cardDetector.nextDetections = listOf(
            card(CardValue.TEN, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS)
        )

        session.advise()

        val state = session.sessionState.value
        assertTrue("Expected Error but was $state", state is SessionState.Error)
    }

    @Test
    fun `advise with no detected cards produces error`() = runTest {
        session.startSession()

        cardDetector.nextDetections = emptyList()

        session.advise()

        val state = session.sessionState.value
        assertTrue("Expected Error but was $state", state is SessionState.Error)
    }

    @Test
    fun `advise speaks the advice`() = runTest {
        session.startSession()

        // Player: A♠ + 7H (largest bboxes), Dealer: 7C (smallest bbox)
        // soft 18 vs 7 → STAND
        cardDetector.nextDetections = listOf(
            card(CardValue.ACE, Suit.SPADES),
            card(CardValue.SEVEN, Suit.HEARTS),
            card(CardValue.SEVEN, Suit.CLUBS)
        )

        session.advise()

        assertEquals(1, adviceSpeaker.spokenAdvice.size)
        assertEquals(Action.STAND, adviceSpeaker.spokenAdvice[0].action)
    }

    @Test
    fun `advise updates running count`() = runTest {
        session.startSession()

        // Player: 2S(+1) + 3H(+1) (largest bboxes), Dealer: 5D(+1) (smallest bbox)
        // = running count +3
        cardDetector.nextDetections = listOf(
            card(CardValue.TWO, Suit.SPADES),
            card(CardValue.THREE, Suit.HEARTS),
            card(CardValue.FIVE, Suit.DIAMONDS)
        )

        session.advise()

        assertEquals(3, cardCounter.countState.value.runningCount)
        val state = session.sessionState.value as SessionState.AdviceReady
        assertEquals(3, state.advice.runningCount)
    }

    @Test
    fun `advise can be called from AdviceReady state`() = runTest {
        session.startSession()

        // Player: 10S + 6H, Dealer: 7C
        cardDetector.nextDetections = listOf(
            card(CardValue.TEN, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS),
            card(CardValue.SEVEN, Suit.CLUBS)
        )
        session.advise()
        assertTrue(session.sessionState.value is SessionState.AdviceReady)
        assertEquals(3, cardCounter.countState.value.totalCardsSeen)

        // Advise again from AdviceReady — same 3 cards, no new cards
        session.advise()
        assertTrue(session.sessionState.value is SessionState.AdviceReady)
        // Counter deduplicates: still 3 total cards seen
        assertEquals(3, cardCounter.countState.value.totalCardsSeen)
    }

    @Test
    fun `advise is no-op when in Idle state`() = runTest {
        // Don't start session
        session.advise()
        assertEquals(SessionState.Idle, session.sessionState.value)
    }

    // --- Multi-round scenario ---

    @Test
    fun `multi-round scenario preserves running count`() = runTest {
        session.startSession()

        // Round 1: Player: 2S(+1) + 3H(+1), Dealer: 9C(0)
        cardDetector.nextDetections = listOf(
            card(CardValue.TWO, Suit.SPADES),
            card(CardValue.THREE, Suit.HEARTS),
            card(CardValue.NINE, Suit.CLUBS)
        )
        session.advise()
        assertEquals(2, cardCounter.countState.value.runningCount)

        // Next hand — running count persists
        session.nextHand()
        assertEquals(SessionState.Streaming, session.sessionState.value)
        assertEquals(RoundState(), session.roundState.value)
        assertEquals(2, cardCounter.countState.value.runningCount)
        assertEquals(0, cardCounter.countState.value.cardsSeenThisRound.size)

        // Round 2: Player: KS(-1) + AC(-1), Dealer: 7D(0)
        cardDetector.nextDetections = listOf(
            card(CardValue.KING, Suit.SPADES),
            card(CardValue.ACE, Suit.CLUBS),
            card(CardValue.SEVEN, Suit.DIAMONDS)
        )
        session.advise()
        // Running count: 2 + 0 + (-1) + (-1) = 0
        assertEquals(0, cardCounter.countState.value.runningCount)
        assertEquals(6, cardCounter.countState.value.totalCardsSeen)
    }

    @Test
    fun `resetCount zeroes count and transitions to Streaming`() = runTest {
        session.startSession()

        // Player: 2S + 3H, Dealer: 9C
        cardDetector.nextDetections = listOf(
            card(CardValue.TWO, Suit.SPADES),
            card(CardValue.THREE, Suit.HEARTS),
            card(CardValue.NINE, Suit.CLUBS)
        )
        session.advise()
        assertEquals(2, cardCounter.countState.value.runningCount)

        session.resetCount()
        assertEquals(0, cardCounter.countState.value.runningCount)
        assertEquals(0, cardCounter.countState.value.totalCardsSeen)
        assertEquals(SessionState.Streaming, session.sessionState.value)
        assertEquals(RoundState(), session.roundState.value)
    }

    @Test
    fun `hand evaluation is correct in advice`() = runTest {
        session.startSession()

        // Player: A♠ + 6H (largest bboxes), Dealer: 6C (smallest bbox)
        // soft 17 vs 6 → DOUBLE
        cardDetector.nextDetections = listOf(
            card(CardValue.ACE, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS),
            card(CardValue.SIX, Suit.CLUBS)
        )
        session.advise()

        val advice = (session.sessionState.value as SessionState.AdviceReady).advice
        assertNotNull(advice.handEvaluation)
        assertEquals(17, advice.handEvaluation!!.softTotal)
        assertTrue(advice.handEvaluation!!.isSoft)
        assertEquals(Action.DOUBLE, advice.action)
    }

    @Test
    fun `blackjack results in STAND`() = runTest {
        session.startSession()

        // Player: A♠ + K♥ (largest bboxes), Dealer: 5D (smallest bbox)
        // blackjack
        cardDetector.nextDetections = listOf(
            card(CardValue.ACE, Suit.SPADES),
            card(CardValue.KING, Suit.HEARTS),
            card(CardValue.FIVE, Suit.DIAMONDS)
        )
        session.advise()

        val advice = (session.sessionState.value as SessionState.AdviceReady).advice
        assertTrue(advice.handEvaluation!!.isBlackjack)
        assertEquals(Action.STAND, advice.action)
    }

    // --- Helpers ---

    private fun card(value: CardValue, suit: Suit) = Card(value, suit)

    // --- Test fakes ---

    private class FakeFrameProvider : FrameProvider {
        private val _streamState = MutableStateFlow(StreamState.IDLE)
        override val streamState: StateFlow<StreamState> = _streamState

        private val _frameFlow = MutableSharedFlow<FrameData>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        override val frameFlow: SharedFlow<FrameData> = _frameFlow

        override suspend fun startStream() {
            _streamState.value = StreamState.STREAMING
        }

        override suspend fun captureFrame(): FrameData? {
            if (_streamState.value != StreamState.STREAMING) return null
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            return FrameData(bitmap = bitmap, timestampMs = 0L, width = 1, height = 1)
        }

        override suspend fun stopStream() {
            _streamState.value = StreamState.IDLE
        }
    }

    private class FakeCardDetector : CardDetector {
        var nextDetections: List<Card> = emptyList()

        override fun initialize() {}

        override suspend fun detect(frame: Bitmap, confidenceThreshold: Float): DetectionResult {
            // Assign decreasing bbox sizes so the first card listed gets the
            // largest box (player) and the last gets the smallest (dealer).
            val cards = nextDetections.mapIndexed { index, card ->
                val size = (nextDetections.size - index) * 100f
                DetectedCard(card, 0.99f, RectF(0f, 0f, size, size))
            }
            return DetectionResult(cards = cards)
        }

        override fun release() {}
    }

    private class FakeAdviceSpeaker : AdviceSpeaker {
        val spokenAdvice = mutableListOf<Advice>()
        override var includeCountInSpeech: Boolean = false

        override fun initialize() {}
        override fun speak(advice: Advice) {
            spokenAdvice.add(advice)
        }
        override fun release() {}
    }
}
