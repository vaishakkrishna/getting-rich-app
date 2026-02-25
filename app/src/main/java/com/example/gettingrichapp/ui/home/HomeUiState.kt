package com.example.gettingrichapp.ui.home

import com.example.gettingrichapp.glasses.ConnectionState

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val numDecks: Int = 6,
    val canStartSession: Boolean = false,
    val hasCameraPermission: Boolean = false
)
