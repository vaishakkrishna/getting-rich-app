package com.example.gettingrichapp.counting

import com.example.gettingrichapp.model.Card
import kotlinx.coroutines.flow.StateFlow

data class CountState(
    val runningCount: Int = 0,
    val totalCardsSeen: Int = 0,
    val numDecks: Int = 6,
    val cardsSeenThisRound: List<Card> = emptyList()
) {
    val trueCount: Double
        get() {
            val remainingCards = numDecks * 52 - totalCardsSeen
            if (remainingCards <= 0) return 0.0
            val estimatedRemainingDecks = remainingCards / 52.0
            return runningCount / estimatedRemainingDecks
        }
}

interface CardCounter {
    val countState: StateFlow<CountState>
    fun setNumDecks(numDecks: Int)
    fun processDetectedCards(detectedCards: List<Card>): List<Card>
    fun nextHand()
    fun resetCount()
}
