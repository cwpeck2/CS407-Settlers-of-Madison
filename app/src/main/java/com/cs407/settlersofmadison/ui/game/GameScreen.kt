package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.game.state.ResourceCard
import com.cs407.settlersofmadison.game.state.ResourceCount
import com.cs407.settlersofmadison.game.state.ResourceType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

// Mode inside the trade dialog: player↔player vs Flamingo Run (4:1 bank trade)
private enum class TradeMode { PLAYER, FLAMINGO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    localPlayerId: String,
    seed: Long, // CHANGED: Accept seed
    onExit: () -> Unit
) {
    // CHANGED: Use factory to pass seed to VM
    val vm: GameViewModel = viewModel(factory = GameViewModelFactory(seed))

    val state by vm.state.collectAsState()
    val eventMessage by vm.eventText.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPauseDialog by remember { mutableStateOf(false) }
    var placingRoad by remember { mutableStateOf(false) }
    var placingSettlement by remember { mutableStateOf(false) }
    var showTradeDialog by remember { mutableStateOf(false) }
    var boardScale by remember { mutableStateOf(1f) }
    var boardOffset by remember { mutableStateOf(Offset.Zero) }

    val boardTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (boardScale * zoomChange).coerceIn(0.5f, 2.5f)  // tweak min/max as you like
        boardScale = newScale
        boardOffset += panChange
    }
    // When turn changes, cancel any pending build / trade modes.
    LaunchedEffect(state.turn) {
        placingRoad = false
        placingSettlement = false
        showTradeDialog = false
    }

    val pendingTrade = state.pendingTrade
    val hasBlockingTrade = pendingTrade != null

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Background image
            Image(
                painter = painterResource(id = R.drawable.game_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )

            // Foreground layer: board + UI + floating buttons
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
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
                            // Handle gestures first
                            .transformable(boardTransformState)
                            // Then visually scale & translate the whole board
                            .graphicsLayer {
                                scaleX = boardScale
                                scaleY = boardScale
                                translationX = boardOffset.x
                                translationY = boardOffset.y
                            }
                    ) {
                        val robberMode =
                            state.phase == GamePhase.ROBBER && state.turn == localPlayerId

                        val rawRoadHighlights =
                            if (
                                isMyTurn &&
                                !robberMode &&
                                (state.phase == GamePhase.PLAY || state.phase == GamePhase.SETUP) &&
                                !hasBlockingTrade
                            ) {
                                vm.legalRoadEdgesFor(localPlayerId, state)
                            } else {
                                emptySet()
                            }

                        val rawSettlementHighlights =
                            if (
                                isMyTurn &&
                                !robberMode &&
                                state.phase == GamePhase.PLAY &&
                                !hasBlockingTrade
                            ) {
                                vm.legalSettlementVerticesFor(localPlayerId, state)
                            } else {
                                emptySet()
                            }

                        val activeEdgeHighlights =
                            if (state.phase == GamePhase.SETUP) {
                                rawRoadHighlights
                            } else if (placingRoad) {
                                rawRoadHighlights
                            } else {
                                emptySet()
                            }

                        val activeVertexHighlights =
                            if (state.phase == GamePhase.PLAY && placingSettlement) {
                                rawSettlementHighlights
                            } else {
                                emptySet()
                            }

                        HexBoard(
                            roomState = state,
                            currentPlayerId = state.turn,
                            robberMode = robberMode,
                            robberCoord = state.robberCoord,
                            onVertexTap = { vKey ->
                                if (hasBlockingTrade) return@HexBoard
                                if (!robberMode) {
                                    when (state.phase) {
                                        GamePhase.SETUP -> {
                                            if (isMyTurn) {
                                                vm.onLocalVertexTap(localPlayerId, vKey)
                                            }
                                        }

                                        GamePhase.PLAY -> {
                                            if (
                                                isMyTurn &&
                                                placingSettlement &&
                                                vKey in activeVertexHighlights
                                            ) {
                                                vm.onLocalVertexTap(localPlayerId, vKey)
                                                placingSettlement = false
                                            }
                                        }

                                        GamePhase.ROBBER -> Unit
                                    }
                                }
                            },
                            onTileTap = if (robberMode) { coord ->
                                vm.onLocalPlaceRobber(localPlayerId, coord)
                            } else null,
                            onEdgeTap = { eKey ->
                                if (hasBlockingTrade) return@HexBoard
                                if (
                                    isMyTurn &&
                                    !robberMode &&
                                    eKey in activeEdgeHighlights &&
                                    (
                                            (state.phase == GamePhase.PLAY && placingRoad) ||
                                                    state.phase == GamePhase.SETUP
                                            )
                                ) {
                                    vm.onLocalEdgeTap(localPlayerId, eKey)
                                    if (state.phase == GamePhase.PLAY) {
                                        placingRoad = false
                                    }
                                }
                            },
                            highlightEdges = activeEdgeHighlights,
                            highlightVertices = activeVertexHighlights,
                            portResources = vm.portResources
                        )
                    }

                    // --- Build bar only in normal PLAY phase and no blocking trade ---
                    val localPlayer = state.players[localPlayerId]
                    if (state.phase == GamePhase.PLAY && !hasBlockingTrade) {
                        val canBuildRoadNow = localPlayer != null &&
                                vm.legalRoadEdgesFor(localPlayerId, state).isNotEmpty()

                        val canBuildSettlementNow = localPlayer != null &&
                                vm.legalSettlementVerticesFor(localPlayerId, state).isNotEmpty()

                        BuildBar(
                            canBuildRoad = canBuildRoadNow,
                            placingRoad = placingRoad,
                            onToggleRoadPlacement = {
                                if (canBuildRoadNow && isMyTurn && state.phase == GamePhase.PLAY) {
                                    val newState = !placingRoad
                                    placingRoad = newState
                                    if (newState) {
                                        placingSettlement = false
                                    }
                                }
                            },
                            canBuildSettlement = canBuildSettlementNow,
                            placingSettlement = placingSettlement,
                            onToggleSettlementPlacement = {
                                if (canBuildSettlementNow && isMyTurn && state.phase == GamePhase.PLAY) {
                                    val newState = !placingSettlement
                                    placingSettlement = newState
                                    if (newState) {
                                        placingRoad = false
                                    }
                                }
                            }
                        )
                    }

                    // --- Setup prompt bar (bottom) ---
                    val setupPrompt = vm.setupPromptFor(localPlayerId)
                    if (setupPrompt != null) {
                        Text(
                            setupPrompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // --- Pending trade banner (interruptive) ---
                    if (pendingTrade != null) {
                        TradeBanner(
                            pendingTrade = pendingTrade,
                            localPlayerId = localPlayerId,
                            players = state.players,
                            onAccept = { vm.onLocalAcceptTrade(localPlayerId) },
                            onReject = { vm.onLocalRejectTrade(localPlayerId) },
                            onCancel = { vm.onLocalCancelTrade(localPlayerId) }
                        )
                    }

                    // --- Local player's resource deck ("hand" of cards) ---
                    PlayerResourceDeck(
                        title = "Your Resources",
                        player = localPlayer
                    )
                }

                // --- Floating icon buttons: Trade + Roll/End Turn ---
                TurnActionButtons(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    isMyTurn = isMyTurn,
                    phase = state.phase,
                    hasRolledThisTurn = state.hasRolledThisTurn,
                    canStartTrade = vm.canStartTrade(localPlayerId, state),
                    hasBlockingTrade = hasBlockingTrade,
                    onRollDice = { vm.onLocalRollDice(localPlayerId) },
                    onEndTurn = { vm.onLocalEndTurn(localPlayerId) },
                    onTrade = { showTradeDialog = true }
                )
            }
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

    // Trade dialog
    val localPlayerForDialog = state.players[localPlayerId]
    if (showTradeDialog && localPlayerForDialog != null) {
        TradeDialog(
            localPlayer = localPlayerForDialog,
            bankRateFor = { res -> vm.bankRateFor(localPlayerId, res) },
            onSendOffer = { offer, request ->
                vm.onLocalProposeTrade(localPlayerId, offer, request)
            },
            onFlamingoTrade = { give, get ->
                vm.onLocalFlamingoTrade(localPlayerId, give, get)
            },
            onDismiss = { showTradeDialog = false },
            backgroundResId = R.drawable.game_background
        )
    }
}

// ... [Helper functions like PlayerResourceDeck, TradeDialog, etc. remain unchanged]
@Composable
private fun PlayerResourceDeck(
    title: String,
    player: PlayerState?
) {
    val cardHeight = 180.dp
    val cardWidth = 120.dp

    // We'll dynamically adjust spacing so the fan stays roughly within the screen width.
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val availableWidth = screenWidth - 32.dp // allow for some padding

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge)

        if (player == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("No resources yet", style = MaterialTheme.typography.labelSmall)
            }
            return
        }

        val r = player.resources

        // Build a flat list: one entry per actual card owned.
        val flatCards = mutableListOf<Resource>()

        fun addCopies(res: Resource) {
            val count = r[res] ?: 0
            repeat(count) {
                flatCards.add(res)
            }
        }

        addCopies(Resource.CHEESE_CURD)
        addCopies(Resource.CONCRETE)
        addCopies(Resource.CHAIR)
        addCopies(Resource.STUDENT)
        addCopies(Resource.BUCKY)

        if (flatCards.isEmpty()) {
            // Still reserve hand space even with 0 cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
                contentAlignment = Alignment.Center
            ) {
                Text("No resources yet", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            val count = flatCards.size
            val totalCardWidthPx = cardWidth.value * count
            val maxWidthPx = availableWidth.value

            val spacing: Dp =
                if (count <= 1) {
                    8.dp
                } else {
                    val rawSpacingPx = (maxWidthPx - totalCardWidthPx) / (count - 1)
                    val rawSpacing = rawSpacingPx.dp
                    // Clamp so we don't compress too insanely or spread too wide.
                    rawSpacing.coerceIn(-60.dp, 8.dp)
                }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(flatCards) { res ->
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardHeight)
                    ) {
                        // Each physical copy is drawn as its own card
                        ResourceCard(
                            res = ResourceCount(
                                type = res.toResourceType(),
                                amount = 1
                            )
                        )
                    }
                }
            }
        }
    }
}
// [Other helper functions omitted for brevity as they did not change] ...
// TradeBanner, TradeDialog, BuildBar, Resource Helpers are identical to previous version.
@Composable
private fun TradeBanner(
    pendingTrade: TradeOffer,
    localPlayerId: String,
    players: Map<String, PlayerState>,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit
) {
    val from = players[pendingTrade.fromId]
    val to = players[pendingTrade.toId]
    val iAmSender = pendingTrade.fromId == localPlayerId
    val iAmReceiver = pendingTrade.toId == localPlayerId

    // If I'm the receiver, I need to be able to pay `request`
    val receiver = players[localPlayerId]
    val canAccept: Boolean = if (iAmReceiver && receiver != null) {
        pendingTrade.request.all { (res, count) ->
            (receiver.resources[res] ?: 0) >= count
        }
    } else {
        false
    }

    val text = if (iAmReceiver) {
        "${from?.name ?: pendingTrade.fromId} offers " +
                pendingTrade.offer.describe() +
                " for " +
                pendingTrade.request.describe() +
                "."
    } else {
        "Waiting for ${to?.name ?: pendingTrade.toId} – " +
                pendingTrade.offer.describe() +
                " ↔ " +
                pendingTrade.request.describe()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )

            if (iAmReceiver) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onReject) { Text("Decline") }
                    Button(
                        onClick = onAccept,
                        enabled = canAccept
                    ) { Text("Accept") }
                }
            } else if (iAmSender) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun TradeDialog(
    localPlayer: PlayerState,
    bankRateFor: (Resource) -> Int,
    onSendOffer: (offer: Map<Resource, Int>, request: Map<Resource, Int>) -> Unit,
    onFlamingoTrade: (give: Resource, get: Resource) -> Unit,
    onDismiss: () -> Unit,
    backgroundResId: Int? = null // you can keep this if you still want an inner bg image
) {
    val resCounts = localPlayer.resources
    val allResources = Resource.values().filter { it != Resource.LAKE }

    // Do they have ANY 3:1 port (for UI text only)?
    val hasAnyPortBonus = allResources.any { bankRateFor(it) == 3 }

    var mode by remember { mutableStateOf(TradeMode.PLAYER) }

    var giveAmounts by remember { mutableStateOf<Map<Resource, Int>>(emptyMap()) }
    var getAmounts by remember { mutableStateOf<Map<Resource, Int>>(emptyMap()) }

    fun setGive(res: Resource, value: Int) {
        val max = resCounts[res] ?: 0
        val clamped = value.coerceIn(0, max)
        giveAmounts = giveAmounts.toMutableMap().also {
            if (clamped == 0) it.remove(res) else it[res] = clamped
        }
    }

    fun setGet(res: Resource, value: Int) {
        val v = value.coerceAtLeast(0)

        getAmounts = when (mode) {
            TradeMode.PLAYER -> {
                getAmounts.toMutableMap().also {
                    if (v == 0) it.remove(res) else it[res] = v
                }
            }

            TradeMode.FLAMINGO -> {
                val othersTotal = getAmounts.filterKeys { it != res }.values.sum()
                val allowedForThis = (1 - othersTotal).coerceAtLeast(0)
                val clamped = v.coerceIn(0, allowedForThis)

                getAmounts.toMutableMap().also {
                    if (clamped == 0) it.remove(res) else it[res] = clamped
                }
            }
        }
    }

    val canSendPlayerTrade =
        giveAmounts.values.any { it > 0 } && getAmounts.values.any { it > 0 }

    val flamingoGiveNonZero = giveAmounts.filterValues { it > 0 }
    val flamingoGetNonZero = getAmounts.filterValues { it > 0 }

    val canFlamingoTrade: Boolean = run {
        if (flamingoGiveNonZero.size != 1 || flamingoGetNonZero.size != 1) {
            false
        } else {
            val (giveRes, giveCount) = flamingoGiveNonZero.entries.first()
            val (_, getCount) = flamingoGetNonZero.entries.first()

            val required = bankRateFor(giveRes)      // 3 or 4 depending on ports
            val have = resCounts[giveRes] ?: 0

            giveCount == required && getCount == 1 && have >= required
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // ← IMPORTANT: use a normal surface, not transparent
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // If you still want a faint art background *inside* the dialog,
                // keep this. If you want it totally clean, just delete this `if`.
                if (backgroundResId != null) {
                    Image(
                        painter = painterResource(id = backgroundResId),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.15f // made lighter so it doesn’t feel “transparent”
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Trade",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mode toggle row (Player vs Flamingo Run)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = mode == TradeMode.PLAYER,
                            onClick = { mode = TradeMode.PLAYER },
                            label = { Text("Player") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Player trade"
                                )
                            }
                        )
                        FilterChip(
                            selected = mode == TradeMode.FLAMINGO,
                            onClick = { mode = TradeMode.FLAMINGO },
                            label = { Text("Flamingo Run") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocalMall,
                                    contentDescription = "Flamingo Run bank"
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    val titleText = when (mode) {
                        TradeMode.PLAYER -> "Offer to opponent"
                        TradeMode.FLAMINGO ->
                            if (hasAnyPortBonus)
                                "Flamingo Run (3:1 / 4:1 – port bonus on some resources)"
                            else
                                "Flamingo Run (4:1)"
                    }
                    Text(
                        titleText,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // --- YOU GIVE ---
                    val giveLabel = when (mode) {
                        TradeMode.PLAYER -> "You give"
                        TradeMode.FLAMINGO -> {
                            if (flamingoGiveNonZero.size == 1) {
                                val giveRes = flamingoGiveNonZero.keys.first()
                                val rate = bankRateFor(giveRes)
                                "You give ($rate of one type)"
                            } else {
                                "You give (3 or 4 of one type)"
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            giveLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    ResourceAmountRow(
                        resources = allResources,
                        amounts = giveAmounts,
                        maxFor = { resCounts[it] ?: 0 },
                        onChange = ::setGive
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- YOU GET ---
                    val getLabel = when (mode) {
                        TradeMode.PLAYER -> "You get"
                        TradeMode.FLAMINGO -> "You get (1 of any)"
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            getLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    ResourceAmountRow(
                        resources = allResources,
                        amounts = getAmounts,
                        maxFor = {
                            when (mode) {
                                TradeMode.PLAYER -> Int.MAX_VALUE
                                TradeMode.FLAMINGO -> 1
                            }
                        },
                        onChange = ::setGet
                    )
                }
            }
        },
        confirmButton = {
            val enabled = when (mode) {
                TradeMode.PLAYER -> canSendPlayerTrade
                TradeMode.FLAMINGO -> canFlamingoTrade
            }

            val buttonText = when (mode) {
                TradeMode.PLAYER -> "Send Offer"
                TradeMode.FLAMINGO -> {
                    if (flamingoGiveNonZero.size == 1) {
                        val giveRes = flamingoGiveNonZero.keys.first()
                        val rate = bankRateFor(giveRes)
                        "Flamingo Run ($rate → 1)"
                    } else {
                        "Flamingo Run"
                    }
                }
            }

            Button(
                onClick = {
                    when (mode) {
                        TradeMode.PLAYER -> {
                            val cleanOffer = giveAmounts.filterValues { it > 0 }
                            val cleanRequest = getAmounts.filterValues { it > 0 }
                            onSendOffer(cleanOffer, cleanRequest)
                        }

                        TradeMode.FLAMINGO -> {
                            val giveRes = flamingoGiveNonZero.keys.first()
                            val getRes = flamingoGetNonZero.keys.first()
                            onFlamingoTrade(giveRes, getRes)
                        }
                    }
                    onDismiss()
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
@Composable
private fun TurnActionButtons(
    modifier: Modifier = Modifier,
    isMyTurn: Boolean,
    phase: GamePhase,
    hasRolledThisTurn: Boolean,
    canStartTrade: Boolean,
    hasBlockingTrade: Boolean,
    onRollDice: () -> Unit,
    onEndTurn: () -> Unit,
    onTrade: () -> Unit
) {
    val canRoll =
        isMyTurn && phase == GamePhase.PLAY && !hasRolledThisTurn && !hasBlockingTrade
    val canEnd =
        isMyTurn && phase == GamePhase.PLAY && hasRolledThisTurn && !hasBlockingTrade

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top: Trade
        ImageSquareButton(
            enabled = canStartTrade && !hasBlockingTrade,
            onClick = onTrade,
            painterResId = R.drawable.ic_trade,      // your PNG
            contentDescription = "Trade",
            size = 56.dp,
            cornerRadius = 12.dp
        )

        // Bottom: Roll or End Turn
        ImageSquareButton(
            enabled = canRoll || canEnd,
            onClick = {
                when {
                    canRoll -> onRollDice()
                    canEnd  -> onEndTurn()
                }
            },
            painterResId = if (!hasRolledThisTurn)
                R.drawable.ic_roll      // your dice PNG
            else
                R.drawable.ic_end_turn,      // your end-turn PNG
            contentDescription = if (!hasRolledThisTurn) "Roll Dice" else "End Turn",
            size = 64.dp,
            cornerRadius = 16.dp
        )
    }
}
@Composable
private fun ImageSquareButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    painterResId: Int,
    contentDescription: String,
    size: Dp = 64.dp,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 2.dp
) {
    val shape = RoundedCornerShape(cornerRadius)

    val borderColor =
        if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            // 🔹 ONLY an outline now – no background fill.
            .border(borderWidth, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = painterResId),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize(0.8f)         // adjust if you want bigger/smaller
                .alpha(if (enabled) 1f else 0.4f),  // dim when disabled
            contentScale = ContentScale.Fit        // show whole PNG, no crop
        )
    }
}
@Composable
private fun ResourceAmountRow(
    resources: List<Resource>,
    amounts: Map<Resource, Int>,
    maxFor: (Resource) -> Int,
    onChange: (Resource, Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(resources) { res ->
            val amount = amounts[res] ?: 0
            val max = maxFor(res)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Small card art
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(100.dp)
                ) {
                    ResourceCard(
                        res = ResourceCount(
                            type = res.toResourceType(),
                            amount = 1
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onChange(res, amount - 1) },
                        enabled = amount > 0,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("-") }

                    Text(
                        amount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            if (amount < max) onChange(res, amount + 1)
                        },
                        enabled = amount < max,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("+") }
                }
            }
        }
    }
}

@Composable
private fun BuildBar(
    canBuildRoad: Boolean,
    placingRoad: Boolean,
    onToggleRoadPlacement: () -> Unit,
    canBuildSettlement: Boolean,
    placingSettlement: Boolean,
    onToggleSettlementPlacement: () -> Unit,
    // optional extras so your existing call still compiles
    canBuildCity: Boolean = false,
    placingCity: Boolean = false,
    onToggleCityPlacement: () -> Unit = {},
    canBuyDevCard: Boolean = false,
    onBuyDevCard: () -> Unit = {}
) {
    if (!canBuildRoad && !canBuildSettlement && !canBuildCity && !canBuyDevCard) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Road
        if (canBuildRoad) {
            GameActionIconButton(
                iconRes = R.drawable.ic_road,          // <-- your road PNG
                contentDescription =
                    if (placingRoad) "Tap a highlighted edge to place a road"
                    else "Build road",
                enabled = true,
                modifier = Modifier.size(40.dp),
                onClick = onToggleRoadPlacement
            )
        }

        // Settlement
        if (canBuildSettlement) {
            GameActionIconButton(
                iconRes = R.drawable.ic_settle,   // <-- your settlement PNG
                contentDescription =
                    if (placingSettlement) "Tap a highlighted corner to place a settlement"
                    else "Build settlement",
                enabled = true,
                modifier = Modifier.size(40.dp),
                onClick = onToggleSettlementPlacement
            )
        }

        // City (if/when you hook it up)
        if (canBuildCity) {
            GameActionIconButton(
                iconRes = R.drawable.ic_city,         // <-- your city PNG
                contentDescription =
                    if (placingCity) "Tap a highlighted corner to upgrade to a city"
                    else "Build city",
                enabled = true,
                modifier = Modifier.size(40.dp),
                onClick = onToggleCityPlacement
            )
        }

        // Dev card (if/when you hook it up)
        if (canBuyDevCard) {
            GameActionIconButton(
                iconRes = R.drawable.ic_dev,     // <-- your dev-card PNG
                contentDescription = "Buy development card",
                enabled = true,
                modifier = Modifier.size(40.dp),
                onClick = onBuyDevCard
            )
        }
    }
}
@Composable
private fun GameActionIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Color.Red.copy(alpha = 0.7f)
            else Color.White.copy(alpha = 0.3f)
        ),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            contentColor = Color.White
        )
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentScale = ContentScale.Fit  // shows full PNG, not zoomed/cropped
        )
    }
}
private fun Resource.displayName(): String =
    when (this) {
        Resource.CONCRETE    -> "Concrete"
        Resource.STUDENT     -> "Student"
        Resource.BUCKY       -> "Bucky"
        Resource.CHAIR       -> "Union Chair"
        Resource.CHEESE_CURD -> "Cheese Curds"
        Resource.LAKE        -> "Lake"
    }

private fun Resource.toResourceType(): ResourceType =
    when (this) {
        Resource.CONCRETE    -> ResourceType.CONCRETE
        Resource.STUDENT     -> ResourceType.STUDENT
        Resource.BUCKY       -> ResourceType.BUCKY
        Resource.CHAIR       -> ResourceType.CHAIR
        Resource.CHEESE_CURD -> ResourceType.CHEESE_CURD
        Resource.LAKE        -> error("WATER should not be converted to ResourceType")
    }

private fun Map<Resource, Int>.describe(): String =
    entries
        .filter { it.value > 0 }
        .joinToString { "${it.value} ${it.key.displayName()}" }
        .ifEmpty { "nothing" }
