package com.example.gettingrichapp.counting

import com.example.gettingrichapp.model.Card
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HiLoCardCounter : CardCounter {

    private val _countState = MutableStateFlow(CountState())
    override val countState: StateFlow<CountState> = _countState

    /** Cards already counted in the current round (dedup by suit+value). */
    private val seenThisRound = mutableSetOf<Card>()

    override fun setNumDecks(numDecks: Int) {
        _countState.value = _countState.value.copy(numDecks = numDecks)
    }

    /**
     * Process newly detected cards. Cards already seen this round (same suit+value)
     * are ignored. Returns only the newly counted cards.
     */
    override fun processDetectedCards(detectedCards: List<Card>): List<Card> {
        val newCards = detectedCards.filter { it !in seenThisRound }
        if (newCards.isEmpty()) return emptyList()

        seenThisRound.addAll(newCards)

        val countDelta = newCards.sumOf { it.value.hiLoValue }
        val current = _countState.value
        _countState.value = current.copy(
            runningCount = current.runningCount + countDelta,
            totalCardsSeen = current.totalCardsSeen + newCards.size,
            cardsSeenThisRound = current.cardsSeenThisRound + newCards
        )

        return newCards
    }

    /** Clear round-specific state but preserve the running count and total cards seen. */
    override fun nextHand() {
        seenThisRound.clear()
        _countState.value = _countState.value.copy(cardsSeenThisRound = emptyList())
    }

    /** Reset everything to initial state. */
    override fun resetCount() {
        seenThisRound.clear()
        _countState.value = CountState(numDecks = _countState.value.numDecks)
    }
}
