package com.example.gettingrichapp.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import com.example.gettingrichapp.detection.DetectedCard
import com.example.gettingrichapp.model.Action
import com.example.gettingrichapp.model.Advice
import com.example.gettingrichapp.model.CardValue
import com.example.gettingrichapp.model.HandEvaluation
import com.example.gettingrichapp.model.Suit
import com.example.gettingrichapp.session.SessionState
import com.example.gettingrichapp.ui.theme.GettingRichAppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    uiState: SessionUiState,
    previewFrame: Bitmap? = null,
    detections: List<DetectedCard> = emptyList(),
    frameWidth: Int = 0,
    frameHeight: Int = 0,
    onAdvise: () -> Unit = {},
    onNextHand: () -> Unit = {},
    onResetCount: () -> Unit = {},
    onResetCountConfirmed: () -> Unit = {},
    onResetCountDismissed: () -> Unit = {},
    onStopSession: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Session") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Count status bar
            CountStatusBar(
                runningCount = uiState.runningCount,
                trueCount = uiState.trueCount
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live video preview with detection overlays
            if (uiState.isPreviewActive) {
                VideoPreview(
                    frame = previewFrame,
                    detections = detections,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card display area
            CardDisplayArea(
                playerCards = uiState.playerCards,
                dealerUpcard = uiState.dealerUpcard
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Advice display
            AdviceDisplay(
                sessionState = uiState.sessionState,
                recommendedAction = uiState.recommendedAction,
                isAdvising = uiState.isAdvising
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            ActionButtons(
                isAdvising = uiState.isAdvising,
                onAdvise = onAdvise,
                onNextHand = onNextHand,
                onResetCount = onResetCount,
                onStopSession = onStopSession
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Reset count confirmation dialog
        if (uiState.showResetConfirmation) {
            ResetCountDialog(
                onConfirm = onResetCountConfirmed,
                onDismiss = onResetCountDismissed
            )
        }
    }
}

@Composable
private fun CountStatusBar(runningCount: Int, trueCount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CountItem(label = "Running Count", value = runningCount.toString())
            CountItem(
                label = "True Count",
                value = String.format(Locale.US, "%.1f", trueCount)
            )
        }
    }
}

@Composable
private fun CountItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDisplayArea(
    playerCards: List<com.example.gettingrichapp.model.Card>,
    dealerUpcard: com.example.gettingrichapp.model.Card?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Dealer upcard
        Text(
            text = "Dealer",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (dealerUpcard != null) {
            CardChip(card = dealerUpcard)
        } else {
            Text(
                text = "No card detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Player cards
        Text(
            text = "Player",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (playerCards.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                playerCards.forEach { card ->
                    CardChip(card = card)
                }
            }
        } else {
            Text(
                text = "No cards detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardChip(card: com.example.gettingrichapp.model.Card) {
    val suitSymbol = when (card.suit) {
        Suit.HEARTS -> "\u2665"
        Suit.DIAMONDS -> "\u2666"
        Suit.CLUBS -> "\u2663"
        Suit.SPADES -> "\u2660"
    }
    val suitColor = when (card.suit) {
        Suit.HEARTS, Suit.DIAMONDS -> Color.Red
        Suit.CLUBS, Suit.SPADES -> Color.Black
    }
    val valueLabel = when (card.value) {
        CardValue.ACE -> "A"
        CardValue.KING -> "K"
        CardValue.QUEEN -> "Q"
        CardValue.JACK -> "J"
        CardValue.TEN -> "10"
        else -> card.value.numericValue.toString()
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = "$valueLabel$suitSymbol",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = suitColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AdviceDisplay(
    sessionState: SessionState,
    recommendedAction: Action?,
    isAdvising: Boolean
) {
    val (text, color) = when {
        isAdvising -> "Analyzing..." to MaterialTheme.colorScheme.onSurfaceVariant
        sessionState is SessionState.Error -> "Error: ${sessionState.message}" to MaterialTheme.colorScheme.error
        recommendedAction != null -> actionDisplayInfo(recommendedAction)
        sessionState is SessionState.Streaming -> "Ready — tap Advise" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Waiting..." to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center,
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun actionDisplayInfo(action: Action): Pair<String, Color> {
    return when (action) {
        Action.HIT -> "HIT" to Color(0xFF4CAF50)        // green
        Action.STAND -> "STAND" to Color(0xFFF44336)     // red
        Action.DOUBLE -> "DOUBLE" to Color(0xFFFF9800)   // orange
        Action.SPLIT -> "SPLIT" to Color(0xFF2196F3)     // blue
        Action.SURRENDER -> "SURRENDER" to Color(0xFF9C27B0) // purple
    }
}

@Composable
private fun ActionButtons(
    isAdvising: Boolean,
    onAdvise: () -> Unit,
    onNextHand: () -> Unit,
    onResetCount: () -> Unit,
    onStopSession: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary action: Advise
        Button(
            onClick = onAdvise,
            enabled = !isAdvising,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = if (isAdvising) "Analyzing..." else "Advise",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Secondary actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onNextHand,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Next Hand")
            }
            OutlinedButton(
                onClick = onResetCount,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("Reset Count")
            }
        }

        // Stop session
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onStopSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Stop Session")
        }
    }
}

@Composable
private fun ResetCountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Count") },
        text = { Text("Are you sure you want to reset the running count to zero?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun SessionScreenIdlePreview() {
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.Idle,
                runningCount = 0,
                trueCount = 0.0
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenStreamingPreview() {
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.Streaming,
                runningCount = 3,
                trueCount = 1.2
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenAdvisingPreview() {
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.Analyzing,
                isAdvising = true,
                playerCards = listOf(
                    com.example.gettingrichapp.model.Card(CardValue.TEN, Suit.SPADES),
                    com.example.gettingrichapp.model.Card(CardValue.SIX, Suit.HEARTS)
                ),
                dealerUpcard = com.example.gettingrichapp.model.Card(CardValue.NINE, Suit.CLUBS),
                runningCount = 2,
                trueCount = 0.8
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenAdviceReadyPreview() {
    val playerCards = listOf(
        com.example.gettingrichapp.model.Card(CardValue.TEN, Suit.SPADES),
        com.example.gettingrichapp.model.Card(CardValue.SIX, Suit.HEARTS)
    )
    val dealerUpcard = com.example.gettingrichapp.model.Card(CardValue.NINE, Suit.CLUBS)
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.AdviceReady(
                    Advice(
                        action = Action.HIT,
                        playerCards = playerCards,
                        dealerUpcard = dealerUpcard,
                        handEvaluation = HandEvaluation(
                            cards = playerCards,
                            hardTotal = 16,
                            softTotal = null,
                            isSoft = false,
                            isPair = false,
                            isBlackjack = false
                        ),
                        runningCount = 2,
                        trueCount = 0.8
                    )
                ),
                playerCards = playerCards,
                dealerUpcard = dealerUpcard,
                recommendedAction = Action.HIT,
                runningCount = 2,
                trueCount = 0.8
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenErrorPreview() {
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.Error("Camera stream lost"),
                runningCount = 5,
                trueCount = 2.1
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionScreenResetDialogPreview() {
    GettingRichAppTheme {
        SessionScreen(
            uiState = SessionUiState(
                sessionState = SessionState.Streaming,
                runningCount = 7,
                trueCount = 2.8,
                showResetConfirmation = true
            )
        )
    }
}
