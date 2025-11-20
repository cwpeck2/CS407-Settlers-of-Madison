package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(onExit: () -> Unit) {
    // State for pause dialog
    var showPauseDialog by remember { mutableStateOf(false) }

    // Example user profile
    val userProfile = remember {
        UserProfile(
            playerName = "Player 1",
            playerId = "player_1",
            gamesPlayed = 0,
            gamesWon = 0
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Game (WIP)") },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text("Exit") }
                },
                // Added pause button
                actions = {
                    IconButton(onClick = { showPauseDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Pause Game"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("Game board coming soon!")
        }

        // Pause dialog
        PauseDialog(
            showDialog = showPauseDialog,
            playerName = userProfile.playerName,
            onDismiss = { showPauseDialog = false },
            onQuit = {
                showPauseDialog = false
                onExit()
            }
        )
    }
}