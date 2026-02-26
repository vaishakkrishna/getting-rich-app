package com.example.gettingrichapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gettingrichapp.glasses.ConnectionState
import com.example.gettingrichapp.ui.theme.GettingRichAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNumDecksChanged: (Int) -> Unit = {},
    onConnectGlasses: () -> Unit = {},
    onStartSession: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Card Game Advisor") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Connection status indicator
            ConnectionStatusIndicator(connectionState = uiState.connectionState)

            // Connect / Disconnect button
            if (uiState.connectionState is ConnectionState.Disconnected ||
                uiState.connectionState is ConnectionState.Error
            ) {
                OutlinedButton(
                    onClick = onConnectGlasses,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Glasses")
                }
            } else if (uiState.connectionState is ConnectionState.Connected) {
                OutlinedButton(
                    onClick = onConnectGlasses,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect Glasses")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Deck selector
            DeckSelector(
                numDecks = uiState.numDecks,
                onNumDecksChanged = onNumDecksChanged
            )

            Spacer(modifier = Modifier.weight(1f))

            // Start Session button
            Button(
                onClick = onStartSession,
                enabled = uiState.canStartSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Start Session",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusIndicator(connectionState: ConnectionState) {
    val (color, label) = when (connectionState) {
        is ConnectionState.Disconnected -> Color.Gray to "Disconnected"
        is ConnectionState.Searching -> Color.Yellow to "Searching..."
        is ConnectionState.Connected -> Color.Green to "Connected"
        is ConnectionState.Error -> Color.Red to "Error: ${connectionState.message}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckSelector(
    numDecks: Int,
    onNumDecksChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val deckOptions = (1..8).toList()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Number of Decks",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = numDecks.toString(),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                deckOptions.forEach { count ->
                    DropdownMenuItem(
                        text = { Text(count.toString()) },
                        onClick = {
                            onNumDecksChanged(count)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun HomeScreenDisconnectedPreview() {
    GettingRichAppTheme {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Disconnected,
                numDecks = 6,
                canStartSession = false,
                hasCameraPermission = false
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenSearchingPreview() {
    GettingRichAppTheme {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Searching,
                numDecks = 6,
                canStartSession = false,
                hasCameraPermission = false
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenConnectedPreview() {
    GettingRichAppTheme {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Connected,
                numDecks = 6,
                canStartSession = true,
                hasCameraPermission = true
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorPreview() {
    GettingRichAppTheme {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Error("Bluetooth unavailable"),
                numDecks = 6,
                canStartSession = false,
                hasCameraPermission = false
            )
        )
    }
}
