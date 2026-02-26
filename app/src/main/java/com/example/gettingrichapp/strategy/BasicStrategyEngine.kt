package com.example.gettingrichapp.strategy

import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.model.HandEvaluation

class BasicStrategyEngine : StrategyEngine {

    override fun recommend(playerHand: HandEvaluation, dealerUpcard: Card, handSize: Int): Action {
        if (playerHand.isBlackjack) return Action.STAND

        val dIdx = StrategyTables.dealerIndex(dealerUpcard.value)

        // 1. Check pair table if it's a pair (only on initial 2 cards)
        if (playerHand.isPair && handSize == 2 && playerHand.pairValue != null) {
            val pIdx = StrategyTables.pairIndex(playerHand.pairValue)
            val pairAction = StrategyTables.PAIR_TABLE[pIdx][dIdx]
            if (pairAction == Action.SPLIT) {
                return Action.SPLIT
            }
            // If not SPLIT, fall through to soft/hard table
        }

        // 2. Check soft table if hand is soft
        if (playerHand.isSoft && playerHand.softTotal != null) {
            val softTotal = playerHand.softTotal
            if (softTotal in 13..21) {
                val sIdx = softTotal - 13
                val action = StrategyTables.SOFT_TABLE[sIdx][dIdx]
                return applyFallbacks(action, handSize)
            }
        }

        // 3. Hard table
        val hardTotal = playerHand.hardTotal
        val action = when {
            hardTotal <= 4 -> Action.HIT
            hardTotal >= 21 -> Action.STAND
            else -> {
                val hIdx = hardTotal - 5
                StrategyTables.HARD_TABLE[hIdx][dIdx]
            }
        }
        return applyFallbacks(action, handSize)
    }

    /**
     * If DOUBLE but hand has more than 2 cards, fall back to HIT.
     * If SURRENDER but hand has more than 2 cards, fall back to HIT.
     */
    private fun applyFallbacks(action: Action, handSize: Int): Action {
        if (handSize > 2) {
            return when (action) {
                Action.DOUBLE -> Action.HIT
                Action.SURRENDER -> Action.HIT
                else -> action
            }
        }
        return action
    }
}
