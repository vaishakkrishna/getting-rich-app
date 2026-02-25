package com.example.gettingrichapp.session

import kotlinx.coroutines.flow.StateFlow

interface GameSession {
    val sessionState: StateFlow<SessionState>
    val roundState: StateFlow<RoundState>
    suspend fun startSession()
    suspend fun advise()
    fun nextHand()
    fun resetCount()
    suspend fun stopSession()
}
