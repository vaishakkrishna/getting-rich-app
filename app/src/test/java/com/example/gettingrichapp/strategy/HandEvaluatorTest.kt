package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandEvaluatorTest {

    private fun card(v: CardValue, s: Suit = Suit.SPADES) = Card(v, s)

    // --- Hard totals ---

    @Test
    fun `hard total of two non-ace cards`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.TEN), card(CardValue.SEVEN)))
        assertEquals(17, hand.hardTotal)
        assertNull(hand.softTotal)
        assertFalse(hand.isSoft)
    }

    @Test
    fun `hard total of three cards`() {
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.FIVE), card(CardValue.THREE), card(CardValue.NINE))
        )
        assertEquals(17, hand.hardTotal)
        assertNull(hand.softTotal)
    }

    @Test
    fun `hard total with low cards`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.TWO), card(CardValue.THREE)))
        assertEquals(5, hand.hardTotal)
    }

    @Test
    fun `hard 20 with king and queen is not a pair`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.KING), card(CardValue.QUEEN)))
        assertEquals(20, hand.hardTotal)
        assertNull(hand.softTotal)
        assertFalse(hand.isSoft)
        assertFalse(hand.isPair)
    }

    // --- Soft totals (ace handling) ---

    @Test
    fun `soft total with one ace`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.ACE), card(CardValue.SIX)))
        assertEquals(7, hand.hardTotal)
        assertEquals(17, hand.softTotal)
        assertTrue(hand.isSoft)
    }

    @Test
    fun `soft total with one ace and multiple cards`() {
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.ACE), card(CardValue.TWO), card(CardValue.FOUR))
        )
        assertEquals(7, hand.hardTotal)
        assertEquals(17, hand.softTotal)
        assertTrue(hand.isSoft)
    }

    @Test
    fun `ace counted as 1 when soft would bust`() {
        // A + 7 + 8 = hard 16 (ace=1), soft would be 26 (bust)
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.ACE), card(CardValue.SEVEN), card(CardValue.EIGHT))
        )
        assertEquals(16, hand.hardTotal)
        assertNull(hand.softTotal)
        assertFalse(hand.isSoft)
    }

    @Test
    fun `two aces`() {
        // 2 aces: hard = 2, soft = 12 (one ace as 11)
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.ACE), card(CardValue.ACE, Suit.HEARTS)))
        assertEquals(2, hand.hardTotal)
        assertEquals(12, hand.softTotal)
        assertTrue(hand.isSoft)
        assertTrue(hand.isPair)
        assertEquals(CardValue.ACE, hand.pairValue)
    }

    @Test
    fun `three aces`() {
        // 3 aces: hard = 3, soft = 13
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.ACE), card(CardValue.ACE, Suit.HEARTS), card(CardValue.ACE, Suit.DIAMONDS))
        )
        assertEquals(3, hand.hardTotal)
        assertEquals(13, hand.softTotal)
        assertTrue(hand.isSoft)
    }

    // --- Pairs ---

    @Test
    fun `pair of eights`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.EIGHT), card(CardValue.EIGHT, Suit.HEARTS)))
        assertTrue(hand.isPair)
        assertEquals(CardValue.EIGHT, hand.pairValue)
        assertEquals(16, hand.hardTotal)
    }

    @Test
    fun `not a pair with three cards`() {
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.EIGHT), card(CardValue.EIGHT, Suit.HEARTS), card(CardValue.TWO))
        )
        assertFalse(hand.isPair)
        assertNull(hand.pairValue)
    }

    @Test
    fun `not a pair with different values`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.EIGHT), card(CardValue.NINE)))
        assertFalse(hand.isPair)
        assertNull(hand.pairValue)
    }

    @Test
    fun `ten and jack is not a pair`() {
        // TEN and JACK are different CardValue entries even though numericValue is the same
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.TEN), card(CardValue.JACK)))
        assertFalse(hand.isPair)
    }

    @Test
    fun `pair of tens`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.TEN), card(CardValue.TEN, Suit.HEARTS)))
        assertTrue(hand.isPair)
        assertEquals(CardValue.TEN, hand.pairValue)
    }

    // --- Blackjack ---

    @Test
    fun `blackjack with ace and ten`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.ACE), card(CardValue.TEN)))
        assertTrue(hand.isBlackjack)
        assertEquals(11, hand.hardTotal)
        assertEquals(21, hand.softTotal)
    }

    @Test
    fun `blackjack with ace and king`() {
        val hand = HandEvaluator.evaluate(listOf(card(CardValue.KING), card(CardValue.ACE)))
        assertTrue(hand.isBlackjack)
    }

    @Test
    fun `21 with three cards is not blackjack`() {
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.SEVEN), card(CardValue.SEVEN, Suit.HEARTS), card(CardValue.SEVEN, Suit.DIAMONDS))
        )
        assertEquals(21, hand.hardTotal)
        assertFalse(hand.isBlackjack)
    }

    @Test
    fun `soft 21 with three cards is not blackjack`() {
        val hand = HandEvaluator.evaluate(
            listOf(card(CardValue.ACE), card(CardValue.FIVE), card(CardValue.FIVE, Suit.HEARTS))
        )
        assertEquals(21, hand.softTotal)
        assertFalse(hand.isBlackjack) // 3 cards
    }

    // --- Empty hand ---

    @Test
    fun `empty hand`() {
        val hand = HandEvaluator.evaluate(emptyList())
        assertEquals(0, hand.hardTotal)
        assertNull(hand.softTotal)
        assertFalse(hand.isSoft)
        assertFalse(hand.isPair)
        assertFalse(hand.isBlackjack)
    }
}
