package com.example.gettingrichapp.ui.session

import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Card
import com.example.gettingrichapp.session.SessionState

data class SessionUiState(
    val sessionState: SessionState = SessionState.Idle,
    val playerCards: List<Card> = emptyList(),
    val dealerUpcard: Card? = null,
    val recommendedAction: Action? = null,
    val runningCount: Int = 0,
    val trueCount: Double = 0.0,
    val isAdvising: Boolean = false,
    val showResetConfirmation: Boolean = false
)
