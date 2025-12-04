package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.VertexKey
import com.cs407.settlersofmadison.game.state.GamePhase
import com.cs407.settlersofmadison.game.state.GameStateManager
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.StateFlow

/**
 * Convenience entry point: create the GameViewModel with a factory,
 * then render the main GameScreen content.
 *
 * @param gameManager shared GameStateManager
 * @param playerId "host" or "guest"
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
    val eventText by vm.eventText.collectAsStateWithLifecycleCompat()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show one-shot events as snackbars
    LaunchedEffect(eventText) {
        eventText?.let { msg ->
            snackbarHostState.showSnackbar(msg)
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Game summary
            Text(
                text = "Turn: ${state.turn.uppercase()} • Phase: ${state.phase}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Last roll: ${state.lastRoll ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            // Players + resources
            state.players.values.forEach { p ->
                PlayerSummaryRow(
                    name = p.name,
                    points = p.points,
                    resources = p.resources
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Debug/network test
            Text(
                text = "Debug counter: ${state.debugCounter}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.incrementDebugCounter() }) {
                Text("+1 (sync test)")
            }

            Spacer(Modifier.height(24.dp))

            // Simple action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { vm.rollDice() }) {
                    Text("Roll Dice")
                }
                Button(onClick = { vm.endTurn() }) {
                    Text("End Turn")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Temporary test button for settlement placement
            if (state.phase == GamePhase.SETUP) {
                Button(onClick = {
                    // For now, always try to place at (0,0) corner 0.
                    val v = VertexKey(q = 0, r = 0, corner = 0)
                    vm.placeSettlement(v)
                }) {
                    Text("Place test settlement at (0,0,0)")
                }
            }

            // TODO: Replace with real board rendering using state.tiles,
            // state.players[*].settlements, etc.
        }
    }
}

/**
 * Compact display of one player's points and resource counts.
 */
@Composable
private fun PlayerSummaryRow(
    name: String,
    points: Int,
    resources: Map<Resource, Int>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$name — $points pts",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        val line = Resource.values()
            .joinToString("  ") { res ->
                val count = resources[res] ?: 0
                "${res.name.lowercase().replaceFirstChar { it.uppercase() }}: $count"
            }
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Small helpers so this file doesn't depend directly on lifecycle-runtime   */
/*  versions you might not be using yet. If you already have                  */
/*  collectAsStateWithLifecycle in your project, you can delete this          */
/*  and just import it instead.                                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat(): State<T> {
    // For now, we just delegate to collectAsState().
    // If you use lifecycle-runtime-compose, replace this with the real
    // collectAsStateWithLifecycle.
    val current = this
    return current.collectAsState()
}