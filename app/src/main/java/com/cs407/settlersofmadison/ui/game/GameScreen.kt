package com.cs407.settlersofmadison.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// Mode inside the trade dialog: player↔player vs Flamingo Run (4:1 bank trade)
private enum class TradeMode { PLAYER, FLAMINGO }

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
    var placingRoad by remember { mutableStateOf(false) }
    var placingSettlement by remember { mutableStateOf(false) }
    var showTradeDialog by remember { mutableStateOf(false) }

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

                    // In SETUP → always show road highlights (no button).
                    // In PLAY → only show when the corresponding build mode is active.
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
                        highlightVertices = activeVertexHighlights
                    )
                }

                // --- Actions (only meaningful in PLAY) ---
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { vm.onLocalRollDice(localPlayerId) },
                        enabled = isMyTurn &&
                                state.phase == GamePhase.PLAY &&
                                !state.hasRolledThisTurn &&
                                !hasBlockingTrade
                    ) { Text("Roll Dice") }

                    Button(
                        onClick = { vm.onLocalEndTurn(localPlayerId) },
                        enabled = isMyTurn &&
                                state.phase == GamePhase.PLAY &&
                                !hasBlockingTrade
                    ) { Text("End Turn") }

                    Button(
                        onClick = { showTradeDialog = true },
                        enabled = vm.canStartTrade(localPlayerId, state)
                    ) { Text("Trade") }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { vm.debugDumpEverything() }) {
                        Text("Dump Board")
                    }
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
    val localPlayer = state.players[localPlayerId]
    if (showTradeDialog && localPlayer != null) {
        TradeDialog(
            localPlayer = localPlayer,
            onSendOffer = { offer, request ->
                vm.onLocalProposeTrade(localPlayerId, offer, request)
            },
            onFlamingoTrade = { give, get ->
                vm.onLocalFlamingoTrade(localPlayerId, give, get)
            },
            onDismiss = { showTradeDialog = false }
        )
    }
}

/* ------------------------------------------------------------------------- */
/*  Resource "hand" UI                                                       */
/* ------------------------------------------------------------------------- */

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

/* ------------------------------------------------------------------------- */
/*  Trade UI pieces                                                          */
/* ------------------------------------------------------------------------- */

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
    onSendOffer: (offer: Map<Resource, Int>, request: Map<Resource, Int>) -> Unit,
    onFlamingoTrade: (give: Resource, get: Resource) -> Unit,
    onDismiss: () -> Unit
) {
    val resCounts = localPlayer.resources
    val allResources = Resource.values().toList()

    var mode by remember { mutableStateOf(TradeMode.PLAYER) }

    // Shared two rows – interpretation depends on mode.
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
                // For Flamingo: total "get" must be at most 1 across all resources.
                val othersTotal = getAmounts.filterKeys { it != res }.values.sum()
                val allowedForThis = (1 - othersTotal).coerceAtLeast(0)
                val clamped = v.coerceIn(0, allowedForThis)

                getAmounts.toMutableMap().also {
                    if (clamped == 0) it.remove(res) else it[res] = clamped
                }
            }
        }
    }

    // Player↔Player confirmation condition
    val canSendPlayerTrade =
        giveAmounts.values.any { it > 0 } && getAmounts.values.any { it > 0 }

    // Flamingo Run confirmation condition:
    // - Give: at least 4 total, all from ONE resource type we have ≥4 of.
    // - Get: exactly 1 total, from ONE resource type.
    val flamingoGiveNonZero = giveAmounts.filterValues { it > 0 }
    val flamingoGiveTotal = flamingoGiveNonZero.values.sum()
    val flamingoGetNonZero = getAmounts.filterValues { it > 0 }
    val flamingoGetTotal = flamingoGetNonZero.values.sum()

    val canFlamingoTrade =
        flamingoGiveTotal >= 4 &&
                flamingoGiveNonZero.size == 1 &&
                (resCounts[flamingoGiveNonZero.keys.first()] ?: 0) >= 4 &&
                flamingoGetNonZero.size == 1 &&
                flamingoGetTotal == 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    TradeMode.FLAMINGO -> "Flamingo Run (4 : 1)"
                }
                Text(titleText, style = MaterialTheme.typography.titleSmall)

                Text(
                    if (mode == TradeMode.PLAYER) "You give" else "You give (4 of one type)",
                    style = MaterialTheme.typography.labelLarge
                )
                ResourceAmountRow(
                    resources = allResources,
                    amounts = giveAmounts,
                    maxFor = { resCounts[it] ?: 0 },
                    onChange = ::setGive
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (mode == TradeMode.PLAYER) "You get" else "You get (1 of any)",
                    style = MaterialTheme.typography.labelLarge
                )
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
        },
        confirmButton = {
            val enabled = when (mode) {
                TradeMode.PLAYER -> canSendPlayerTrade
                TradeMode.FLAMINGO -> canFlamingoTrade
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
                Text(
                    if (mode == TradeMode.PLAYER)
                        "Send Offer"
                    else
                        "Flamingo Run (4 → 1)"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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

/* ------------------------------------------------------------------------- */
/*  Build bar                                                                */
/* ------------------------------------------------------------------------- */

@Composable
private fun BuildBar(
    canBuildRoad: Boolean,
    placingRoad: Boolean,
    onToggleRoadPlacement: () -> Unit,
    canBuildSettlement: Boolean,
    placingSettlement: Boolean,
    onToggleSettlementPlacement: () -> Unit
) {
    if (!canBuildRoad && !canBuildSettlement) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canBuildRoad) {
            AssistChip(
                onClick = onToggleRoadPlacement,
                label = {
                    Text(
                        if (placingRoad) "Tap a highlighted edge to place road"
                        else "Build Road"
                    )
                }
            )
        }

        if (canBuildSettlement) {
            AssistChip(
                onClick = onToggleSettlementPlacement,
                label = {
                    Text(
                        if (placingSettlement) "Tap a highlighted corner to place settlement"
                        else "Build Settlement"
                    )
                }
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  Small helpers                                                            */
/* ------------------------------------------------------------------------- */

private fun Resource.displayName(): String =
    when (this) {
        Resource.CONCRETE    -> "Concrete"
        Resource.STUDENT     -> "Student"
        Resource.BUCKY       -> "Bucky"
        Resource.CHAIR       -> "Union Chair"
        Resource.CHEESE_CURD -> "Cheese Curds"
    }

private fun Resource.toResourceType(): ResourceType =
    when (this) {
        Resource.CONCRETE    -> ResourceType.CONCRETE
        Resource.STUDENT     -> ResourceType.STUDENT
        Resource.BUCKY       -> ResourceType.BUCKY
        Resource.CHAIR       -> ResourceType.CHAIR
        Resource.CHEESE_CURD -> ResourceType.CHEESE_CURD
    }

/** Turn {Concrete=2, Student=1} into "2 Concrete, 1 Student" for the banner. */
private fun Map<Resource, Int>.describe(): String =
    entries
        .filter { it.value > 0 }
        .joinToString { "${it.value} ${it.key.displayName()}" }
        .ifEmpty { "nothing" }
