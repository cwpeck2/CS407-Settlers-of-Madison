package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.game.state.ResourceCard
import com.cs407.settlersofmadison.game.state.ResourceCount
import com.cs407.settlersofmadison.game.state.ResourceType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    localPlayerId: String,
    onExit: () -> Unit,
    vm: GameViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val eventMessage by vm.eventText.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPauseDialog by remember { mutableStateOf(false) }

    // Simple local user profile
    val userProfile = remember(localPlayerId) {
        UserProfile(
            playerName = if (localPlayerId == "host") "Host" else "Guest",
            playerId = localPlayerId
        )
    }

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
                title = { Text("Game") },
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
                val robberMode = state.phase == GamePhase.ROBBER && state.turn == localPlayerId

                HexBoard(
                    roomState = state,
                    currentPlayerId = state.turn,
                    robberMode = robberMode,
                    robberCoord = state.robberCoord,
                    onVertexTap = { vKey ->
                        if (isMyTurn && state.phase == GamePhase.PLAY) {
                            vm.onLocalVertexTap(localPlayerId, vKey)
                        }
                    },
                    onTileTap = if (robberMode) { coord ->
                        vm.onLocalPlaceRobber(localPlayerId, coord)
                    } else null
                )
            }

            // --- Actions ---
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { vm.onLocalRollDice(localPlayerId) },
                    enabled = isMyTurn && state.phase == GamePhase.PLAY
                ) { Text("Roll Dice") }

                Button(
                    onClick = { vm.onLocalEndTurn(localPlayerId) },
                    enabled = isMyTurn && state.phase == GamePhase.PLAY
                ) { Text("End Turn") }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { vm.debugDumpEverything() }) {
                    Text("Dump Board")
                }
            }

            // --- Local player's resource deck ---
            val localPlayer = state.players[localPlayerId]
            PlayerResourceDeck(
                title = "Your Resources",
                player = localPlayer
            )
        }
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

/**
 * Show only the given player's cards (no peeking at opponent).
 */
@Composable
private fun PlayerResourceDeck(
    title: String,
    player: PlayerState?
) {
    if (player == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)

        val r = player.resources

        val counts = listOf(
            ResourceCount(ResourceType.WOOD,  r[Resource.WOOD]  ?: 0),
            ResourceCount(ResourceType.BRICK, r[Resource.BRICK] ?: 0),
            ResourceCount(ResourceType.SHEEP, r[Resource.SHEEP] ?: 0),
            ResourceCount(ResourceType.WHEAT, r[Resource.WHEAT] ?: 0),
            ResourceCount(ResourceType.ORE,   r[Resource.ORE]   ?: 0)
        ).filter { it.amount > 0 }

        if (counts.isEmpty()) {
            Text("No resources yet", style = MaterialTheme.typography.labelSmall)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                counts.forEach { card ->
                    ResourceCard(res = card)
                }
            }
        }
    }
}
private fun hexToPixel(coord: HexCoord, hexSize: Float): Offset {
    // Pointy-top axial layout (q, r), standard RedBlob style
    val x = hexSize * (sqrt(3f) * coord.q + sqrt(3f) / 2f * coord.r)
    val y = hexSize * (3f / 2f * coord.r)
    return Offset(x, y)
}

private fun hexCornerOffset(hexSize: Float, cornerIndex: Int): Offset {
    // Make sure this orientation matches your existing tile drawing
    val angleDeg = 60f * cornerIndex - 30f
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val cx = hexSize * cos(angleRad)
    val cy = hexSize * sin(angleRad)
    return Offset(cx, cy)
}
