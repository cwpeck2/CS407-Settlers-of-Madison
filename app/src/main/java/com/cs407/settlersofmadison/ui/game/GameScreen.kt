package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.game.state.GamePhase
import com.cs407.settlersofmadison.game.state.GameStateManager
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow


/**
 * Entry point used from Navigation: wires GameStateManager + playerId into VM.
 */
@Composable
fun GameScreen(
    gameManager: GameStateManager,
    playerId: String,
    onExit: () -> Unit
) {
    val factory = remember(gameManager, playerId) {
        GameViewModelFactory(gameManager, playerId)
    }
    val vm: GameViewModel = viewModel(factory = factory)

    GameScreenContent(vm = vm, onExit = onExit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScreenContent(
    vm: GameViewModel,
    onExit: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycleCompat()
    val eventMessage by vm.eventText.collectAsStateWithLifecycleCompat()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPauseDialog by remember { mutableStateOf(false) }

    val localPlayerId = vm.playerId
    val isMyTurn = (state.turn == localPlayerId)

    LaunchedEffect(eventMessage) {
        eventMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeEvent()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settlers of Madison") },
                navigationIcon = {
                    TextButton(onClick = onExit) {
                        Text("Exit")
                    }
                },
                actions = {
                    IconButton(onClick = { showPauseDialog = true }) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Header: whose turn / last roll ---
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentPlayer = state.players[state.turn]
                Text(
                    "Turn: ${currentPlayer?.name ?: state.turn}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Last roll: ${state.lastRoll ?: "--"}")
            }

            // --- Board ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                HexBoard(
                    state = state,
                    currentPlayerId = state.turn,
                    onVertexTap = { vKey ->
                        if (isMyTurn && state.phase == GamePhase.PLAY) {
                            vm.placeSettlement(vKey)
                        }
                    }
                )
            }

            // --- Actions ---
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { vm.rollDice() },
                    enabled = isMyTurn && state.phase == GamePhase.PLAY
                ) { Text("Roll Dice") }

                Button(
                    onClick = { vm.endTurn() },
                    enabled = isMyTurn && state.phase == GamePhase.PLAY
                ) { Text("End Turn") }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { vm.debugDumpBoard() }) {
                    Text("Dump Board")
                }
            }

            // --- Simple resource summary for the local player ---
            val localPlayer = state.players[localPlayerId]
            PlayerResourceSummary(
                title = "Your Resources",
                resources = localPlayer?.resources ?: emptyMap()
            )
        }
    }

    // Pause dialog from your teammate's code (already merged).
    PauseDialog(
        showDialog = showPauseDialog,
        playerName = if (localPlayerId == "host") "Host" else "Guest",
        onDismiss = { showPauseDialog = false },
        onQuit = {
            showPauseDialog = false
            onExit()
        }
    )
}

@Composable
private fun PlayerResourceSummary(
    title: String,
    resources: Map<Resource, Int>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)

        val allCards = Resource.values().map { res ->
            ResourceCount(
                type = res,
                amount = resources[res] ?: 0
            )
        }

        if (allCards.all { it.amount == 0 }) {
            Text("No resources yet", style = MaterialTheme.typography.labelSmall)
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                allCards.forEach { rc ->
                    ResourceCard(res = rc)
                }
            }
        }
    }
}

/* ---------- lifecycle-compatible collectors ---------- */
@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat(): State<T> {
    return collectAsState()
}
