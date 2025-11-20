package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * pause/settings dialog.
 * Just shows player name and basic buttons.
 */
@Composable
fun PauseDialog(
    showDialog: Boolean,
    playerName: String,
    onDismiss: () -> Unit,
    onQuit: () -> Unit
) {
    if (showDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Text(
                        text = "Game Paused",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Divider()

                    // Show player name
                    Text(
                        text = "Player: $playerName",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Divider()

                    // Resume button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Resume Game")
                    }

                    // Quit button
                    var showQuitConfirm by remember { mutableStateOf(false) }

                    OutlinedButton(
                        onClick = { showQuitConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quit Game")
                    }

                    // Simple quit confirmation
                    if (showQuitConfirm) {
                        AlertDialog(
                            onDismissRequest = { showQuitConfirm = false },
                            title = { Text("Quit Game?") },
                            text = { Text("Are you sure you want to quit?") },
                            confirmButton = {
                                Button(onClick = {
                                    showQuitConfirm = false
                                    onQuit()
                                }) {
                                    Text("Yes, Quit")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showQuitConfirm = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}