package com.example.gettingrichapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gettingrichapp.ServiceLocator
import com.example.gettingrichapp.counting.CardCounter
import com.example.gettingrichapp.glasses.ConnectionState
import com.example.gettingrichapp.glasses.GlassesConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    private val glassesConnection: GlassesConnection,
    private val cardCounter: CardCounter
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        glassesConnection.connectionState,
        cardCounter.countState
    ) { connectionState, countState ->
        HomeUiState(
            connectionState = connectionState,
            numDecks = countState.numDecks,
            canStartSession = connectionState is ConnectionState.Connected,
            hasCameraPermission = true // checked at activity level
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onNumDecksChanged(numDecks: Int) {
        cardCounter.setNumDecks(numDecks)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    glassesConnection = ServiceLocator.glassesConnection,
                    cardCounter = ServiceLocator.cardCounter
                )
            }
        }
    }
}
