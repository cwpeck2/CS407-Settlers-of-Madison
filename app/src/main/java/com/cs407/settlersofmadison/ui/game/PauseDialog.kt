package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Pause/settings dialog.
 * Just shows player name and basic buttons.
 */
@Composable
fun PauseDialog(
    showDialog: Boolean,
    playerName: String,
    onDismiss: () -> Unit,
    onQuit: () -> Unit
) {
    if (!showDialog) return

    val (showQuitConfirm, setShowQuitConfirm) = remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Paused", style = MaterialTheme.typography.headlineSmall)
            Text("Player: $playerName", style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onDismiss) {
                    Text("Resume")
                }
                Button(onClick = { setShowQuitConfirm(true) }) {
                    Text("Quit Game")
                }
            }

            if (showQuitConfirm) {
                AlertDialog(
                    onDismissRequest = { setShowQuitConfirm(false) },
                    title = { Text("Quit game?") },
                    text = { Text("Are you sure you want to quit?") },
                    confirmButton = {
                        TextButton(onClick = {
                            setShowQuitConfirm(false)
                            onQuit()
                        }) {
                            Text("Yes, Quit")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { setShowQuitConfirm(false) }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
