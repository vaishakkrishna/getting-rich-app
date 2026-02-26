package com.example.gettingrichapp.counting

import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HiLoCardCounterTest {

    private lateinit var counter: HiLoCardCounter

    private fun card(v: CardValue, s: Suit = Suit.SPADES) = Card(v, s)

    @Before
    fun setUp() {
        counter = HiLoCardCounter()
    }

    // --- Basic counting ---

    @Test
    fun `low cards increment running count`() {
        // 2,3,4,5,6 are +1 each
        val cards = listOf(
            card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS),
            card(CardValue.FOUR, Suit.DIAMONDS), card(CardValue.FIVE, Suit.CLUBS),
            card(CardValue.SIX)
        )
        counter.processDetectedCards(cards)
        assertEquals(5, counter.countState.value.runningCount)
        assertEquals(5, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `high cards decrement running count`() {
        // 10,J,Q,K,A are -1 each
        val cards = listOf(
            card(CardValue.TEN), card(CardValue.JACK, Suit.HEARTS),
            card(CardValue.QUEEN, Suit.DIAMONDS), card(CardValue.KING, Suit.CLUBS),
            card(CardValue.ACE)
        )
        counter.processDetectedCards(cards)
        assertEquals(-5, counter.countState.value.runningCount)
        assertEquals(5, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `neutral cards do not change running count`() {
        // 7,8,9 are 0
        val cards = listOf(
            card(CardValue.SEVEN), card(CardValue.EIGHT, Suit.HEARTS),
            card(CardValue.NINE, Suit.DIAMONDS)
        )
        counter.processDetectedCards(cards)
        assertEquals(0, counter.countState.value.runningCount)
        assertEquals(3, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `mixed cards sequence`() {
        // 2(+1), 7(0), K(-1), 5(+1), A(-1) = 0
        val cards = listOf(
            card(CardValue.TWO), card(CardValue.SEVEN, Suit.HEARTS),
            card(CardValue.KING, Suit.DIAMONDS), card(CardValue.FIVE, Suit.CLUBS),
            card(CardValue.ACE)
        )
        counter.processDetectedCards(cards)
        assertEquals(0, counter.countState.value.runningCount)
    }

    // --- Per-round deduplication ---

    @Test
    fun `duplicate card in same round is ignored`() {
        val twoSpades = card(CardValue.TWO, Suit.SPADES)
        counter.processDetectedCards(listOf(twoSpades))
        assertEquals(1, counter.countState.value.runningCount)
        assertEquals(1, counter.countState.value.totalCardsSeen)

        // Same card again — should be ignored
        val newCards = counter.processDetectedCards(listOf(twoSpades))
        assertTrue(newCards.isEmpty())
        assertEquals(1, counter.countState.value.runningCount)
        assertEquals(1, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `same value different suit is not a duplicate`() {
        counter.processDetectedCards(listOf(card(CardValue.TWO, Suit.SPADES)))
        assertEquals(1, counter.countState.value.runningCount)

        val newCards = counter.processDetectedCards(listOf(card(CardValue.TWO, Suit.HEARTS)))
        assertEquals(1, newCards.size)
        assertEquals(2, counter.countState.value.runningCount)
        assertEquals(2, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `processDetectedCards returns only new cards`() {
        val first = card(CardValue.THREE, Suit.SPADES)
        val second = card(CardValue.FIVE, Suit.HEARTS)

        counter.processDetectedCards(listOf(first))

        // Process both — only second should be new
        val newCards = counter.processDetectedCards(listOf(first, second))
        assertEquals(1, newCards.size)
        assertEquals(second, newCards[0])
    }

    // --- Multi-round ---

    @Test
    fun `running count persists across rounds`() {
        counter.processDetectedCards(listOf(card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS)))
        assertEquals(2, counter.countState.value.runningCount)

        counter.nextHand()

        counter.processDetectedCards(listOf(card(CardValue.FOUR), card(CardValue.FIVE, Suit.HEARTS)))
        assertEquals(4, counter.countState.value.runningCount)
        assertEquals(4, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `nextHand clears round cards but keeps running count`() {
        counter.processDetectedCards(listOf(card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS)))
        assertEquals(2, counter.countState.value.cardsSeenThisRound.size)

        counter.nextHand()

        assertEquals(0, counter.countState.value.cardsSeenThisRound.size)
        assertEquals(2, counter.countState.value.runningCount)
        assertEquals(2, counter.countState.value.totalCardsSeen)
    }

    @Test
    fun `same card can be counted again in new round`() {
        val twoSpades = card(CardValue.TWO, Suit.SPADES)

        counter.processDetectedCards(listOf(twoSpades))
        assertEquals(1, counter.countState.value.runningCount)

        counter.nextHand()

        // Same card in new round — should count again (new physical card)
        val newCards = counter.processDetectedCards(listOf(twoSpades))
        assertEquals(1, newCards.size)
        assertEquals(2, counter.countState.value.runningCount)
    }

    // --- Reset ---

    @Test
    fun `resetCount resets everything except numDecks`() {
        counter.setNumDecks(8)
        counter.processDetectedCards(listOf(card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS)))

        counter.resetCount()

        val state = counter.countState.value
        assertEquals(0, state.runningCount)
        assertEquals(0, state.totalCardsSeen)
        assertEquals(0, state.cardsSeenThisRound.size)
        assertEquals(8, state.numDecks) // preserved
    }

    @Test
    fun `after reset, same cards can be counted again`() {
        val twoSpades = card(CardValue.TWO, Suit.SPADES)
        counter.processDetectedCards(listOf(twoSpades))
        assertEquals(1, counter.countState.value.runningCount)

        counter.resetCount()
        assertEquals(0, counter.countState.value.runningCount)

        counter.processDetectedCards(listOf(twoSpades))
        assertEquals(1, counter.countState.value.runningCount)
    }

    // --- setNumDecks ---

    @Test
    fun `setNumDecks updates state`() {
        counter.setNumDecks(2)
        assertEquals(2, counter.countState.value.numDecks)
    }

    // --- True count ---

    @Test
    fun `true count calculation with 6 decks`() {
        // Running count = 6, 0 cards seen, 6 decks = 312 cards
        // True count = 6 / 6.0 = 1.0
        counter.processDetectedCards(listOf(
            card(CardValue.TWO, Suit.SPADES),
            card(CardValue.THREE, Suit.HEARTS),
            card(CardValue.FOUR, Suit.DIAMONDS),
            card(CardValue.FIVE, Suit.CLUBS),
            card(CardValue.SIX, Suit.SPADES),
            card(CardValue.SIX, Suit.HEARTS)
        ))
        assertEquals(6, counter.countState.value.runningCount)
        // 312 - 6 = 306 remaining, 306/52 = ~5.88 decks
        // True count = 6 / 5.88 ≈ 1.02
        val tc = counter.countState.value.trueCount
        assertTrue("True count should be ~1.02 but was $tc", tc in 0.9..1.1)
    }

    @Test
    fun `true count with 1 deck`() {
        counter.setNumDecks(1)
        // +3 running count, 10 cards seen → 42 remaining → 42/52 ≈ 0.808 decks
        // True count = 3 / 0.808 ≈ 3.71
        repeat(10) { i ->
            counter.processDetectedCards(listOf(
                card(CardValue.entries[i % CardValue.entries.size], Suit.entries[i % Suit.entries.size])
            ))
        }
        // Manually set a known state for precise assertion
        counter.resetCount()
        counter.setNumDecks(1)
        counter.processDetectedCards(listOf(
            card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS), card(CardValue.FOUR, Suit.DIAMONDS)
        ))
        // Running count = 3, 3 cards seen, 1 deck = 52 cards
        // Remaining = 49, remaining decks = 49/52 ≈ 0.942
        // True count = 3 / 0.942 ≈ 3.18
        val tc = counter.countState.value.trueCount
        assertTrue("True count should be ~3.18 but was $tc", tc in 3.0..3.3)
    }

    @Test
    fun `true count is zero when no cards seen`() {
        assertEquals(0.0, counter.countState.value.trueCount, 0.001)
    }

    // --- cardsSeenThisRound tracking ---

    @Test
    fun `cardsSeenThisRound tracks cards in current round`() {
        val cards = listOf(card(CardValue.TWO), card(CardValue.THREE, Suit.HEARTS))
        counter.processDetectedCards(cards)
        assertEquals(cards, counter.countState.value.cardsSeenThisRound)
    }

    @Test
    fun `cardsSeenThisRound accumulates across multiple processDetectedCards calls`() {
        val first = card(CardValue.TWO)
        val second = card(CardValue.THREE, Suit.HEARTS)
        counter.processDetectedCards(listOf(first))
        counter.processDetectedCards(listOf(second))
        assertEquals(listOf(first, second), counter.countState.value.cardsSeenThisRound)
    }
}
