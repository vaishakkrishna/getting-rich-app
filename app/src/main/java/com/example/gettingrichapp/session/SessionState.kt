package com.example.gettingrichapp.session

import com.example.gettingrichapp.model.Advice
import com.example.gettingrichapp.model.Card

sealed class SessionState {
    data object Idle : SessionState()
    data object Streaming : SessionState()
    data object Analyzing : SessionState()
    data class AdviceReady(val advice: Advice) : SessionState()
    data class Error(val message: String) : SessionState()
}

data class RoundState(
    val playerCards: List<Card> = emptyList(),
    val dealerUpcard: Card? = null,
    val currentAdvice: Advice? = null
)
