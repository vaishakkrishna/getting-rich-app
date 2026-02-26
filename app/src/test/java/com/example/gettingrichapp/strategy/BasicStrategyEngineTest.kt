package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicStrategyEngineTest {

    private val engine = BasicStrategyEngine()

    private fun card(v: CardValue, s: Suit = Suit.SPADES) = Card(v, s)

    private fun eval(vararg values: CardValue) =
        HandEvaluator.evaluate(values.mapIndexed { i, v ->
            card(v, Suit.entries[i % Suit.entries.size])
        })

    // --- Hard total strategy ---

    @Test
    fun `hard 16 vs dealer 10 is SURRENDER`() {
        val hand = eval(CardValue.TEN, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.TEN), 2)
        assertEquals(Action.SURRENDER, action)
    }

    @Test
    fun `hard 16 vs dealer 9 is SURRENDER`() {
        val hand = eval(CardValue.TEN, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.NINE), 2)
        assertEquals(Action.SURRENDER, action)
    }

    @Test
    fun `hard 16 vs dealer 7 is HIT`() {
        val hand = eval(CardValue.TEN, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.SEVEN), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `hard 16 vs dealer 5 is STAND`() {
        val hand = eval(CardValue.TEN, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.FIVE), 2)
        assertEquals(Action.STAND, action)
    }

    @Test
    fun `hard 12 vs dealer 2 is HIT`() {
        val hand = eval(CardValue.TEN, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.TWO), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `hard 12 vs dealer 4 is STAND`() {
        val hand = eval(CardValue.TEN, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.FOUR), 2)
        assertEquals(Action.STAND, action)
    }

    @Test
    fun `hard 11 vs dealer 6 is DOUBLE`() {
        val hand = eval(CardValue.SIX, CardValue.FIVE)
        val action = engine.recommend(hand, card(CardValue.SIX), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `hard 11 vs dealer ace is DOUBLE`() {
        val hand = eval(CardValue.SIX, CardValue.FIVE)
        val action = engine.recommend(hand, card(CardValue.ACE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `hard 10 vs dealer 9 is DOUBLE`() {
        val hand = eval(CardValue.SIX, CardValue.FOUR)
        val action = engine.recommend(hand, card(CardValue.NINE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `hard 10 vs dealer 10 is HIT`() {
        val hand = eval(CardValue.SIX, CardValue.FOUR)
        val action = engine.recommend(hand, card(CardValue.TEN), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `hard 9 vs dealer 3 is DOUBLE`() {
        val hand = eval(CardValue.FIVE, CardValue.FOUR)
        val action = engine.recommend(hand, card(CardValue.THREE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `hard 9 vs dealer 2 is HIT`() {
        val hand = eval(CardValue.FIVE, CardValue.FOUR)
        val action = engine.recommend(hand, card(CardValue.TWO), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `hard 17 always STAND`() {
        for (dealer in CardValue.entries) {
            val hand = eval(CardValue.TEN, CardValue.SEVEN)
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("hard 17 vs $dealer", Action.STAND, action)
        }
    }

    @Test
    fun `hard 8 or below always HIT`() {
        val hand = eval(CardValue.THREE, CardValue.FOUR)
        for (dealer in CardValue.entries) {
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("hard 7 vs $dealer", Action.HIT, action)
        }
    }

    @Test
    fun `hard 15 vs dealer 10 is SURRENDER`() {
        val hand = eval(CardValue.TEN, CardValue.FIVE)
        val action = engine.recommend(hand, card(CardValue.TEN), 2)
        assertEquals(Action.SURRENDER, action)
    }

    // --- Soft total strategy ---

    @Test
    fun `soft 18 vs dealer 2 is DOUBLE`() {
        val hand = eval(CardValue.ACE, CardValue.SEVEN)
        val action = engine.recommend(hand, card(CardValue.TWO), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `soft 18 vs dealer 7 is STAND`() {
        val hand = eval(CardValue.ACE, CardValue.SEVEN)
        val action = engine.recommend(hand, card(CardValue.SEVEN), 2)
        assertEquals(Action.STAND, action)
    }

    @Test
    fun `soft 18 vs dealer 9 is HIT`() {
        val hand = eval(CardValue.ACE, CardValue.SEVEN)
        val action = engine.recommend(hand, card(CardValue.NINE), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `soft 17 vs dealer 3 is DOUBLE`() {
        val hand = eval(CardValue.ACE, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.THREE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `soft 17 vs dealer 2 is HIT`() {
        val hand = eval(CardValue.ACE, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.TWO), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `soft 13 vs dealer 5 is DOUBLE`() {
        val hand = eval(CardValue.ACE, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.FIVE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `soft 13 vs dealer 4 is HIT`() {
        val hand = eval(CardValue.ACE, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.FOUR), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `soft 19 vs dealer 6 is DOUBLE`() {
        val hand = eval(CardValue.ACE, CardValue.EIGHT)
        val action = engine.recommend(hand, card(CardValue.SIX), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `soft 19 vs dealer 5 is STAND`() {
        val hand = eval(CardValue.ACE, CardValue.EIGHT)
        val action = engine.recommend(hand, card(CardValue.FIVE), 2)
        assertEquals(Action.STAND, action)
    }

    @Test
    fun `soft 20 always STAND`() {
        for (dealer in CardValue.entries) {
            val hand = eval(CardValue.ACE, CardValue.NINE)
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("soft 20 vs $dealer", Action.STAND, action)
        }
    }

    // --- Pair splitting ---

    @Test
    fun `pair of aces always SPLIT`() {
        for (dealer in CardValue.entries) {
            val hand = eval(CardValue.ACE, CardValue.ACE)
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("AA vs $dealer", Action.SPLIT, action)
        }
    }

    @Test
    fun `pair of eights always SPLIT`() {
        for (dealer in CardValue.entries) {
            val hand = eval(CardValue.EIGHT, CardValue.EIGHT)
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("88 vs $dealer", Action.SPLIT, action)
        }
    }

    @Test
    fun `pair of tens never SPLIT`() {
        for (dealer in CardValue.entries) {
            val hand = eval(CardValue.TEN, CardValue.TEN)
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("TT vs $dealer", Action.STAND, action)
        }
    }

    @Test
    fun `pair of fives never SPLIT uses hard table`() {
        // 5-5 = hard 10, should use hard table
        val hand = eval(CardValue.FIVE, CardValue.FIVE)
        // vs dealer 9: hard 10 vs 9 = DOUBLE
        val action = engine.recommend(hand, card(CardValue.NINE), 2)
        assertEquals(Action.DOUBLE, action)
    }

    @Test
    fun `pair of nines vs dealer 7 is STAND`() {
        val hand = eval(CardValue.NINE, CardValue.NINE)
        val action = engine.recommend(hand, card(CardValue.SEVEN), 2)
        assertEquals(Action.STAND, action)
    }

    @Test
    fun `pair of nines vs dealer 6 is SPLIT`() {
        val hand = eval(CardValue.NINE, CardValue.NINE)
        val action = engine.recommend(hand, card(CardValue.SIX), 2)
        assertEquals(Action.SPLIT, action)
    }

    @Test
    fun `pair of twos vs dealer 3 is SPLIT`() {
        val hand = eval(CardValue.TWO, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.THREE), 2)
        assertEquals(Action.SPLIT, action)
    }

    @Test
    fun `pair of twos vs dealer 8 is HIT`() {
        val hand = eval(CardValue.TWO, CardValue.TWO)
        val action = engine.recommend(hand, card(CardValue.EIGHT), 2)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `pair of sixes vs dealer 2 is SPLIT`() {
        val hand = eval(CardValue.SIX, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.TWO), 2)
        assertEquals(Action.SPLIT, action)
    }

    @Test
    fun `pair of sixes vs dealer 7 is HIT`() {
        val hand = eval(CardValue.SIX, CardValue.SIX)
        val action = engine.recommend(hand, card(CardValue.SEVEN), 2)
        assertEquals(Action.HIT, action)
    }

    // --- Fallback: DOUBLE → HIT when handSize > 2 ---

    @Test
    fun `double falls back to HIT with more than 2 cards`() {
        // Hard 11 vs dealer 6: normally DOUBLE, but with 3 cards → HIT
        val hand = eval(CardValue.FOUR, CardValue.THREE, CardValue.FOUR) // hard 11
        val action = engine.recommend(hand, card(CardValue.SIX), 3)
        assertEquals(Action.HIT, action)
    }

    @Test
    fun `soft double falls back to HIT with more than 2 cards`() {
        // Soft 17 (A+2+4) vs dealer 3: normally DOUBLE, but with 3 cards → HIT
        val hand = eval(CardValue.ACE, CardValue.TWO, CardValue.FOUR) // soft 17
        val action = engine.recommend(hand, card(CardValue.THREE), 3)
        assertEquals(Action.HIT, action)
    }

    // --- Fallback: SURRENDER → HIT when handSize > 2 ---

    @Test
    fun `surrender falls back to HIT with more than 2 cards`() {
        // Hard 16 vs dealer 10: normally SURRENDER, but with 3 cards → HIT
        val hand = eval(CardValue.SEVEN, CardValue.FOUR, CardValue.FIVE) // hard 16
        val action = engine.recommend(hand, card(CardValue.TEN), 3)
        assertEquals(Action.HIT, action)
    }

    // --- Blackjack ---

    @Test
    fun `blackjack always STAND`() {
        val hand = eval(CardValue.ACE, CardValue.TEN)
        for (dealer in CardValue.entries) {
            val action = engine.recommend(hand, card(dealer), 2)
            assertEquals("BJ vs $dealer", Action.STAND, action)
        }
    }
}
