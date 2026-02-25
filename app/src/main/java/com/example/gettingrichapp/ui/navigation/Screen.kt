package com.example.gettingrichapp.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Session : Screen("session")
}
