package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.HandEvaluation

object HandEvaluator {

    fun evaluate(cards: List<Card>): HandEvaluation {
        if (cards.isEmpty()) {
            return HandEvaluation(
                cards = cards,
                hardTotal = 0,
                softTotal = null,
                isSoft = false,
                isPair = false,
                isBlackjack = false
            )
        }

        var hardTotal = 0
        var aceCount = 0

        for (card in cards) {
            if (card.value == CardValue.ACE) {
                aceCount++
                hardTotal += 1 // Count ace as 1 for hard total
            } else {
                hardTotal += card.value.numericValue
            }
        }

        // Soft total: if we have at least one ace and counting one ace as 11
        // doesn't bust (hard + 10 <= 21)
        val softTotal = if (aceCount > 0 && hardTotal + 10 <= 21) {
            hardTotal + 10
        } else {
            null
        }

        val isSoft = softTotal != null

        // A pair is exactly 2 cards with the same value
        val isPair = cards.size == 2 && cards[0].value == cards[1].value

        // Blackjack is exactly 2 cards totaling 21 (ace + ten-value)
        val isBlackjack = cards.size == 2 && (softTotal == 21 || hardTotal == 21)

        val pairValue = if (isPair) cards[0].value else null

        return HandEvaluation(
            cards = cards,
            hardTotal = hardTotal,
            softTotal = softTotal,
            isSoft = isSoft,
            isPair = isPair,
            isBlackjack = isBlackjack,
            pairValue = pairValue
        )
    }
}
