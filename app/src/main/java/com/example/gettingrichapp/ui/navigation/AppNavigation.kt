package com.example.gettingrichapp.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gettingrichapp.ServiceLocator
import com.example.gettingrichapp.glasses.ConnectionState
import com.example.gettingrichapp.ui.home.HomeScreen
import com.example.gettingrichapp.ui.home.HomeViewModel
import com.example.gettingrichapp.ui.session.SessionScreen
import com.example.gettingrichapp.ui.session.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            val uiState by viewModel.uiState.collectAsState()

            HomeScreen(
                uiState = uiState,
                onNumDecksChanged = viewModel::onNumDecksChanged,
                onConnectGlasses = {
                    val activity = context as? Activity ?: return@HomeScreen
                    val glasses = ServiceLocator.glassesConnection
                    if (uiState.connectionState is ConnectionState.Connected) {
                        glasses.startUnregistration(activity)
                    } else {
                        glasses.startRegistration(activity)
                    }
                },
                onStartSession = {
                    val activity = context as? Activity ?: return@HomeScreen
                    coroutineScope.launch {
                        val glasses = ServiceLocator.glassesConnection
                        val hasPermission = glasses.requestCameraPermission(activity)
                        if (hasPermission) {
                            navController.navigate(Screen.Session.route)
                        }
                    }
                }
            )
        }
        composable(Screen.Session.route) {
            val viewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory)
            val uiState by viewModel.uiState.collectAsState()
            val previewFrame by viewModel.previewFrame.collectAsState()
            val detectionOverlays by viewModel.detectionOverlays.collectAsState()
            val frameSize by viewModel.frameSize.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.startSession()
            }

            SessionScreen(
                uiState = uiState,
                previewFrame = previewFrame,
                detections = detectionOverlays,
                frameWidth = frameSize.first,
                frameHeight = frameSize.second,
                onAdvise = viewModel::onAdvise,
                onNextHand = viewModel::onNextHand,
                onResetCount = viewModel::onResetCount,
                onResetCountConfirmed = viewModel::onResetCountConfirmed,
                onResetCountDismissed = viewModel::onResetCountDismissed,
                onStopSession = {
                    viewModel.onStopSession()
                    navController.popBackStack()
                }
            )
        }
    }
}
