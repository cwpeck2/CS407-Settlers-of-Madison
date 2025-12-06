package com.cs407.settlersofmadison.ui.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.P2PHolder
import com.cs407.settlersofmadison.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

// Overall phase – now includes SETUP and ROBBER.
enum class GamePhase { SETUP, PLAY, ROBBER }
enum class BuildType { ROAD, SETTLEMENT, CITY }

/**
 * Simple 1-for-1 player-to-player trade offer.
 *
 * fromId gives [give] to toId
 * toId   gives [get]  to fromId
 */
data class TradeOffer(
    val fromId: String,
    val toId: String,
    val offer: Map<Resource, Int>,   // what fromId gives
    val request: Map<Resource, Int>  // what fromId receives
)
/**
 * Per–player state.
 *
 * id     – "host" or "guest"
 * name   – display name
 * resources – card counts by Resource type
 * settlements – canonical vertex keys where they’ve placed houses
 * roads      – canonical edge keys where they’ve placed roads
 * points     – VI points (1 per settlement for now)
 */
data class PlayerState(
    val id: String,
    val name: String,
    val resources: Map<Resource, Int> = emptyMap(),
    val settlements: Set<VertexKey> = emptySet(),
    val roads: Set<EdgeKey> = emptySet(),
    val points: Int = 0
)


/**
 * Global room/game state.
 */
data class RoomState(
    val tiles: List<Tile>,
    val players: Map<String, PlayerState>,
    val turn: String,

    // Phase of the game
    val phase: GamePhase = GamePhase.PLAY,

    // Dice state
    val lastRoll: Int? = null,
    val hasRolledThisTurn: Boolean = false,

    // Robber lives on a single tile; null = not placed yet.
    val robberCoord: HexCoord? = null,
    // While in ROBBER phase, only this player can place/move robber.
    val robberMoverId: String? = null,

    // --- Setup phase tracking (ABBA initial placements) ---
    // Randomly chosen starting player ("host" or "guest")
    val startingPlayerId: String? = null,
    val pendingTrade: TradeOffer? = null,
    /**
     * 0: starting player – first settlement+road
     * 1: other player    – first settlement+road
     * 2: other player    – second settlement+road
     * 3: starting player – second settlement+road
     * >=4: setup done
     */
    val setupIndex: Int = 0,

    // Has current setup player placed settlement/road in this step?
    val setupPlacedSettlement: Boolean = false,
    val setupPlacedRoad: Boolean = false,

    // The settlement vertex placed in *this* setup step (road must attach here).
    val setupCurrentSettlementVertex: VertexKey? = null,

)

class GameViewModel : ViewModel() {

    private val p2p = P2PHolder.service

    // --- Board definition ----------------------------------------------------

    // Standard UW-Catan-like radius-2 board with randomized resources/numbers.
    private val boardTiles: List<Tile> = standardCatanBoardRandom()

    // Single immutable graph describing tiles + shared vertices/edges.
    private val boardGraph: BoardGraph = buildBoardGraph(boardTiles)

    // Randomly choose who starts (host or guest).
    private val startingPlayerId: String =
        if (kotlin.random.Random.nextBoolean()) "host" else "guest"

    // Reactive game state used by the UI.
    private val _state = MutableStateFlow(
        RoomState(
            tiles = boardGraph.tiles,
            players = mapOf(
                "host" to PlayerState("host", "Host"),
                "guest" to PlayerState("guest", "Guest")
            ),
            // Setup starts with the randomly chosen player.
            turn = startingPlayerId,
            phase = GamePhase.SETUP,
            startingPlayerId = startingPlayerId,
            setupIndex = 0,
            setupPlacedSettlement = false,
            setupPlacedRoad = false,
            setupCurrentSettlementVertex = null,
            hasRolledThisTurn = false,
            pendingTrade = null
        )
    )
    val state: StateFlow<RoomState> = _state

    // One–shot event text (snackbar / toast).
    val eventText = MutableStateFlow<String?>(null)

    init {
        // Listen for raw P2P game messages from the other device.
        viewModelScope.launch {
            p2p.incoming.collect { line ->
                if (!line.startsWith("GAME:")) return@collect
                val payload = line.removePrefix("GAME:")
                handleIncomingGameMessage(payload)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun mutate(block: (RoomState) -> RoomState) {
        _state.value = block(_state.value)
    }

    /**
     * Static build cost table for each type of build action.
     */
    private val buildCosts: Map<BuildType, Map<Resource, Int>> = mapOf(
        BuildType.ROAD to mapOf(
            Resource.CONCRETE to 1,     // originally WOOD
            Resource.BUCKY to 1         // originally BRICK – arbitrary fun mapping
        ),
        BuildType.SETTLEMENT to mapOf(
            Resource.CONCRETE to 1,
            Resource.STUDENT to 1,
            Resource.CHAIR to 1,
            Resource.CHEESE_CURD to 1
        )
        // City cost will be added alongside city implementation.
    )
    private val resourceOrder: Array<Resource> = Resource.values()

    private fun encodeResourceMap(map: Map<Resource, Int>): String =
        resourceOrder.joinToString(",") { (map[it] ?: 0).toString() }

    private fun decodeResourceMap(data: String): Map<Resource, Int> {
        val parts = data.split(",")
        val result = mutableMapOf<Resource, Int>()
        for (i in resourceOrder.indices) {
            if (i >= parts.size) break
            val n = parts[i].toIntOrNull() ?: 0
            if (n > 0) result[resourceOrder[i]] = n
        }
        return result
    }
    private fun canAfford(player: PlayerState, type: BuildType): Boolean {
        val cost = buildCosts[type] ?: return true
        for ((res, needed) in cost) {
            val have = player.resources[res] ?: 0
            if (have < needed) return false
        }
        return true
    }

    private fun payFor(player: PlayerState, type: BuildType): PlayerState {
        val cost = buildCosts[type] ?: return player
        val newRes = player.resources.toMutableMap()
        for ((res, needed) in cost) {
            val current = newRes[res] ?: 0
            newRes[res] = current - needed
        }
        return player.copy(resources = newRes)
    }

    /**
     * Vertices adjacent to v (distance 1 in the vertex graph).
     */
    private fun adjacentVerticesOf(v: VertexKey): Set<VertexKey> {
        val res = mutableSetOf<VertexKey>()
        for (edge in boardGraph.edges.values) {
            val (a, b) = edge.vertices
            if (a == v) res.add(b)
            if (b == v) res.add(a)
        }
        return res
    }

    private fun edgesIncidentToVertex(v: VertexKey): List<EdgeKey> {
        return boardGraph.edges.values
            .filter { edge ->
                val (a, b) = edge.vertices
                a == v || b == v
            }
            .map { it.key }
    }

    /** True if this vertex is distance-1 from any settlement (any player). */
    private fun isAdjacentToSettlement(rs: RoomState, v: VertexKey): Boolean {
        val neighbors = adjacentVerticesOf(v)
        return rs.players.values.any { player ->
            player.settlements.any { it in neighbors }
        }
    }

    // --- Setup helper: who should be placing right now? ----------------------

    private fun setupPlayerForIndex(starting: String, index: Int): String {
        val other = if (starting == "host") "guest" else "host"
        return when (index) {
            0 -> starting  // first settlement+road of starting player
            1 -> other     // first settlement+road of other player
            2 -> other     // second settlement+road of other player
            3 -> starting  // second settlement+road of starting player
            else -> starting
        }
    }

    private fun advanceSetup(rs: RoomState): RoomState {
        if (rs.phase != GamePhase.SETUP) return rs
        val starting = rs.startingPlayerId ?: rs.turn
        val nextIndex = rs.setupIndex + 1

        // All 4 placements done -> start normal PLAY, first player is starting player.
        if (nextIndex >= 4) {
            return rs.copy(
                phase = GamePhase.PLAY,
                turn = starting,
                setupIndex = nextIndex,
                setupPlacedSettlement = false,
                setupPlacedRoad = false,
                setupCurrentSettlementVertex = null,
                hasRolledThisTurn = false
            )
        }

        val nextPlayer = setupPlayerForIndex(starting, nextIndex)
        return rs.copy(
            turn = nextPlayer,
            setupIndex = nextIndex,
            setupPlacedSettlement = false,
            setupPlacedRoad = false,
            setupCurrentSettlementVertex = null
        )
    }

    // -------------------------------------------------------------------------
    // Core actions (local logic – no network)
    // -------------------------------------------------------------------------

    /**
     * Try to place a settlement for playerId at vertex v.
     */
    private fun tryPlaceSettlement(playerId: String, v: VertexKey) {
        mutate { rs ->
            // Block all building while a trade is pending.
            if (rs.pendingTrade != null) return@mutate rs

            // Vertex must exist
            if (!boardGraph.vertices.containsKey(v)) return@mutate rs

            // Already occupied?
            if (rs.players.values.any { v in it.settlements }) {
                eventText.value = "Vertex already occupied."
                return@mutate rs
            }

            // Distance rule – no adjacent settlement.
            if (isAdjacentToSettlement(rs, v)) {
                eventText.value = "Too close to another settlement."
                return@mutate rs
            }

            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            // --- SETUP PHASE LOGIC ---
            if (rs.phase == GamePhase.SETUP) {
                val starting = rs.startingPlayerId ?: playerId
                val expected = setupPlayerForIndex(starting, rs.setupIndex)

                // Only the expected player can act.
                if (expected != playerId || rs.turn != playerId) return@mutate rs

                // Can only place one settlement in this setup step.
                if (rs.setupPlacedSettlement) return@mutate rs

                // Max 2 total settlements per player in setup.
                if (me.settlements.size >= 2) return@mutate rs

                val newSet = me.settlements.toMutableSet().apply { add(v) }
                val updatedPlayer = me.copy(
                    settlements = newSet,
                    points = newSet.size // 1 point per settlement for now
                )
                players[playerId] = updatedPlayer

                eventText.value = "${updatedPlayer.name} placed a settlement."

                val newState = rs.copy(
                    players = players,
                    setupPlacedSettlement = true,
                    setupCurrentSettlementVertex = v
                )
                debugDumpEverything()
                return@mutate newState
            }

            // --- PLAY PHASE LOGIC ---
            if (rs.phase != GamePhase.PLAY || rs.turn != playerId) return@mutate rs

            // Settlement must connect to one of *your* roads.
            val incidentEdges = edgesIncidentToVertex(v)
            val hasConnectingRoad = incidentEdges.any { it in me.roads }
            if (!hasConnectingRoad) {
                // Quietly ignore – user only sees allowed vertices.
                return@mutate rs
            }

            if (!canAfford(me, BuildType.SETTLEMENT)) {
                eventText.value = "Not enough resources to build a dorm."
                return@mutate rs
            }

            val paidPlayer = payFor(me, BuildType.SETTLEMENT)
            val newSet = paidPlayer.settlements.toMutableSet().apply { add(v) }
            val updatedPlayer = paidPlayer.copy(
                settlements = newSet,
                points = newSet.size
            )
            players[playerId] = updatedPlayer

            val vertex = boardGraph.vertices[v]
            val touchingTiles = vertex?.tileCoords
                ?.mapNotNull { coord -> boardGraph.tilesByCoord[coord] }
                ?.sortedWith(compareBy<Tile> { it.coord.q }.thenBy { it.coord.r })
                ?: emptyList()

            val touchingDesc =
                if (touchingTiles.isEmpty()) "no tiles"
                else touchingTiles.joinToString { "${it.number}-${it.resource.name}" }

            eventText.value =
                "${updatedPlayer.name} built a dorm touching: $touchingDesc"

            val newState = rs.copy(players = players)
            debugDumpEverything()
            newState
        }
    }

    private fun tryPlaceRoad(playerId: String, e: EdgeKey) {
        mutate { rs ->
            // Block building while a trade is pending.
            if (rs.pendingTrade != null) return@mutate rs

            val edge = boardGraph.edges[e] ?: return@mutate rs

            // Already occupied by any player?
            if (rs.players.values.any { e in it.roads }) {
                // Silently ignore; UI doesn't highlight taken edges anyway.
                return@mutate rs
            }

            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            // --- SETUP PHASE LOGIC ---
            if (rs.phase == GamePhase.SETUP) {
                val starting = rs.startingPlayerId ?: playerId
                val expected = setupPlayerForIndex(starting, rs.setupIndex)

                if (expected != playerId || rs.turn != playerId) return@mutate rs
                if (!rs.setupPlacedSettlement) return@mutate rs // must place settlement first
                if (rs.setupPlacedRoad) return@mutate rs       // only one road per setup step

                val v = rs.setupCurrentSettlementVertex ?: return@mutate rs

                // Road must be incident to the settlement we just placed this step.
                val incidentEdges = edgesIncidentToVertex(v)
                if (e !in incidentEdges) return@mutate rs

                val newRoads = me.roads.toMutableSet().apply { add(e) }
                val updatedPlayer = me.copy(roads = newRoads)
                players[playerId] = updatedPlayer

                eventText.value = "${updatedPlayer.name} placed an initial road."

                val afterRoad = rs.copy(
                    players = players,
                    setupPlacedRoad = true
                )
                debugDumpEverything()
                return@mutate advanceSetup(afterRoad)
            }

            // --- PLAY PHASE LOGIC ---
            if (rs.phase != GamePhase.PLAY || rs.turn != playerId) return@mutate rs

            val hasNetwork = me.settlements.isNotEmpty() || me.roads.isNotEmpty()

            if (hasNetwork) {
                // Precompute our network vertices.
                val networkVertices = mutableSetOf<VertexKey>().apply {
                    addAll(me.settlements)
                    for (rKey in me.roads) {
                        val rEdge = boardGraph.edges[rKey] ?: continue
                        add(rEdge.vertices.first)
                        add(rEdge.vertices.second)
                    }
                }

                val (v1, v2) = edge.vertices
                if (v1 !in networkVertices && v2 !in networkVertices) {
                    // Not connected to our network – ignore.
                    return@mutate rs
                }
            }

            // Resource check for road.
            if (!canAfford(me, BuildType.ROAD)) {
                eventText.value = "Not enough resources to build a path."
                return@mutate rs
            }

            val paidPlayer = payFor(me, BuildType.ROAD)
            val newRoads = paidPlayer.roads.toMutableSet().apply { add(e) }
            players[playerId] = paidPlayer.copy(roads = newRoads)

            eventText.value = "${paidPlayer.name} built a path."

            rs.copy(players = players)
        }
    }

    /**
     * Compute legal road edges for highlighting.
     */
    fun legalRoadEdgesFor(
        playerId: String,
        rs: RoomState = _state.value
    ): Set<EdgeKey> {
        val me = rs.players[playerId] ?: return emptySet()

        // Trades block building.
        if (rs.pendingTrade != null) return emptySet()

        // --- SETUP: only the current builder, only edges off the current settlement ---
        if (rs.phase == GamePhase.SETUP) {
            val starting = rs.startingPlayerId ?: playerId
            val expected = setupPlayerForIndex(starting, rs.setupIndex)

            if (expected != playerId || rs.turn != playerId) return emptySet()
            if (!rs.setupPlacedSettlement || rs.setupPlacedRoad) return emptySet()

            val v = rs.setupCurrentSettlementVertex ?: return emptySet()
            val takenEdges: Set<EdgeKey> =
                rs.players.values.flatMapTo(mutableSetOf()) { it.roads }

            return edgesIncidentToVertex(v).filter { it !in takenEdges }.toSet()
        }

        // --- PLAY ---
        if (rs.phase != GamePhase.PLAY) return emptySet()

        if (!canAfford(me, BuildType.ROAD)) return emptySet()

        val hasNetwork = me.settlements.isNotEmpty() || me.roads.isNotEmpty()

        val networkVertices: Set<VertexKey> =
            if (!hasNetwork) emptySet()
            else buildSet {
                addAll(me.settlements)
                for (rKey in me.roads) {
                    val rEdge = boardGraph.edges[rKey] ?: continue
                    add(rEdge.vertices.first)
                    add(rEdge.vertices.second)
                }
            }

        val takenEdges: Set<EdgeKey> =
            rs.players.values.flatMapTo(mutableSetOf()) { it.roads }

        val result = mutableSetOf<EdgeKey>()

        for ((eKey, edge) in boardGraph.edges) {
            if (eKey in takenEdges) continue

            if (hasNetwork) {
                val (v1, v2) = edge.vertices
                if (v1 !in networkVertices && v2 !in networkVertices) continue
            }
            result.add(eKey)
        }

        return result
    }

    /**
     * Legal settlement vertices during PLAY (for "build dorm" highlights).
     */
    fun legalSettlementVerticesFor(
        playerId: String,
        rs: RoomState = _state.value
    ): Set<VertexKey> {
        if (rs.phase != GamePhase.PLAY) return emptySet()
        if (rs.pendingTrade != null) return emptySet()

        val me = rs.players[playerId] ?: return emptySet()
        if (!canAfford(me, BuildType.SETTLEMENT)) return emptySet()

        val occupied = rs.players.values
            .flatMapTo(mutableSetOf()) { it.settlements }

        val result = mutableSetOf<VertexKey>()

        for ((vKey, _) in boardGraph.vertices) {
            if (vKey in occupied) continue
            if (isAdjacentToSettlement(rs, vKey)) continue

            val incidentEdges = edgesIncidentToVertex(vKey)
            val hasConnectingRoad = incidentEdges.any { it in me.roads }
            if (!hasConnectingRoad) continue

            result.add(vKey)
        }

        return result
    }

    /**
     * Apply a dice roll to all players.
     */
    private fun applyRollResult(roll: Int) {
        mutate { rs ->
            if (rs.phase != GamePhase.PLAY) return@mutate rs

            val players = rs.players.toMutableMap()
            val gainsByPlayerName = mutableMapOf<String, MutableList<Resource>>()
            val robbedCoord = rs.robberCoord

            for ((id, player) in players) {
                var gained = 0
                val newRes = player.resources.toMutableMap()

                for (v in player.settlements) {
                    val vertex = boardGraph.vertices[v] ?: continue
                    for (coord in vertex.tileCoords) {
                        val tile = boardGraph.tilesByCoord[coord] ?: continue

                        // Robber blocks this tile
                        if (robbedCoord != null && tile.coord == robbedCoord) continue

                        if (tile.number == roll) {
                            newRes[tile.resource] =
                                (newRes[tile.resource] ?: 0) + 1
                            gained++

                            gainsByPlayerName
                                .getOrPut(player.name) { mutableListOf() }
                                .add(tile.resource)
                        }
                    }
                }

                if (gained > 0) {
                    players[id] = player.copy(resources = newRes)
                }
            }

            val msg =
                if (gainsByPlayerName.isEmpty()) {
                    "Rolled $roll. No resources."
                } else {
                    "Rolled $roll → " + gainsByPlayerName.entries.joinToString(" | ") { (name, list) ->
                        val summary = list
                            .groupingBy { it }
                            .eachCount()
                            .entries
                            .joinToString { (res, count) ->
                                val prettyName = res.name
                                    .lowercase()
                                    .replaceFirstChar { it.uppercaseChar() }
                                "$count $prettyName"
                            }
                        "$name: $summary"
                    }
                }

            eventText.value = msg

            rs.copy(
                players = players,
                lastRoll = roll,
                hasRolledThisTurn = true
            )
        }
    }

    private fun internalEndTurn() {
        mutate { rs ->
            if (rs.phase != GamePhase.PLAY) return@mutate rs
            if (rs.pendingTrade != null) return@mutate rs
            val next = if (rs.turn == "host") "guest" else "host"
            rs.copy(turn = next, hasRolledThisTurn = false)
        }
    }

    /**
     * Enter robber phase after rolling a 7.
     */
    private fun startRobberPhase(playerId: String) {
        mutate { rs ->
            eventText.value =
                "${rs.players[playerId]?.name ?: playerId} rolled 7. Tap a tile to move the Badger Patrol."
            rs.copy(
                phase = GamePhase.ROBBER,
                lastRoll = 7,
                robberMoverId = playerId,
                hasRolledThisTurn = true
            )
        }
    }

    /**
     * Place/move robber on a given tile coord.
     */
    private fun placeRobberInternal(playerId: String, coord: HexCoord) {
        mutate { rs ->
            if (rs.phase != GamePhase.ROBBER ||
                rs.turn != playerId ||
                rs.robberMoverId != playerId
            ) {
                return@mutate rs
            }

            // Must move to a *different* tile.
            if (rs.robberCoord != null && rs.robberCoord == coord) {
                return@mutate rs
            }

            eventText.value =
                "${rs.players[playerId]?.name ?: playerId} moved the Badger Patrol."

            rs.copy(
                robberCoord = coord,
                phase = GamePhase.PLAY,
                robberMoverId = null,
                hasRolledThisTurn = true
            )
        }
    }

    // -------------------------------------------------------------------------
    // Trade helpers
    // -------------------------------------------------------------------------

    fun canStartTrade(
        playerId: String,
        rs: RoomState = _state.value
    ): Boolean {
        if (rs.phase != GamePhase.PLAY) return false
        if (rs.turn != playerId) return false
        if (rs.pendingTrade != null) return false
        val me = rs.players[playerId] ?: return false
        return me.resources.values.sum() > 0
    }
    private fun applyTrade(rs: RoomState, trade: TradeOffer): RoomState {
        val players = rs.players.toMutableMap()
        val from = players[trade.fromId] ?: return rs
        val to = players[trade.toId] ?: return rs

        fun applyDelta(player: PlayerState, delta: Map<Resource, Int>): PlayerState {
            val newRes = player.resources.toMutableMap()
            for ((res, d) in delta) {
                val cur = newRes[res] ?: 0
                newRes[res] = cur + d
            }
            return player.copy(resources = newRes)
        }

        val fromDelta = mutableMapOf<Resource, Int>()
        val toDelta = mutableMapOf<Resource, Int>()

        for (res in resourceOrder) {
            val giveAmt = trade.offer[res] ?: 0
            val reqAmt = trade.request[res] ?: 0
            if (giveAmt != 0 || reqAmt != 0) {
                fromDelta[res] = (fromDelta[res] ?: 0) - giveAmt + reqAmt
                toDelta[res] = (toDelta[res] ?: 0) + giveAmt - reqAmt
            }
        }

        val newFrom = applyDelta(from, fromDelta)
        val newTo = applyDelta(to, toDelta)

        players[trade.fromId] = newFrom
        players[trade.toId] = newTo

        eventText.value = "Trade completed."

        return rs.copy(players = players, pendingTrade = null)
    }

    private fun applyBankTrade(
        rs: RoomState,
        playerId: String,
        give: Resource,
        get: Resource
    ): RoomState {
        val players = rs.players.toMutableMap()
        val me = players[playerId] ?: return rs

        val have = me.resources[give] ?: 0
        if (have < 4) {
            // Leave rs unchanged – caller will show message.
            return rs
        }

        val resMap = me.resources.toMutableMap()
        resMap[give] = have - 4
        resMap[get] = (resMap[get] ?: 0) + 1

        players[playerId] = me.copy(resources = resMap)
        eventText.value = "${me.name} used Flamingo Run (4→1)."

        return rs.copy(players = players)
    }

    // -------------------------------------------------------------------------
    // Network message handling (remote actions)
    // -------------------------------------------------------------------------

    /**
     * Handles payload of the form:
     *  - SETTLEMENT:<playerId>:<q>:<r>:<corner>
     *  - ROAD:<playerId>:<q>:<r>:<edgeIndex>
     *  - ROLL:<playerId>:<roll>
     *  - ENDTURN:<playerId>
     *  - ROBBER:<playerId>:<q>:<r>
     *  - TRADE_OFFER:<from>:<to>:<give>:<get>
     *  - TRADE_ACCEPT:<byPlayer>
     *  - TRADE_REJECT:<byPlayer>
     *  - TRADE_CANCEL:<byPlayer>
     *  - TRADE_BANK:<playerId>:<give>:<get>
     */
    private fun handleIncomingGameMessage(payload: String) {
        val parts = payload.split(":")
        if (parts.isEmpty()) return

        when (parts[0]) {
            "SETTLEMENT" -> {
                if (parts.size != 5) return
                val playerId = parts[1]
                val q = parts[2].toIntOrNull() ?: return
                val r = parts[3].toIntOrNull() ?: return
                val corner = parts[4].toIntOrNull() ?: return
                val v = VertexKey(q, r, corner)
                tryPlaceSettlement(playerId, v)
            }

            "ROAD" -> {
                if (parts.size != 5) return
                val playerId = parts[1]
                val q = parts[2].toIntOrNull() ?: return
                val r = parts[3].toIntOrNull() ?: return
                val edgeIndex = parts[4].toIntOrNull() ?: return
                val e = EdgeKey(q, r, edgeIndex)
                tryPlaceRoad(playerId, e)
            }

            "ROLL" -> {
                if (parts.size != 3) return
                val playerId = parts[1]
                val roll = parts[2].toIntOrNull() ?: return

                if (roll == 7) {
                    startRobberPhase(playerId)
                } else {
                    applyRollResult(roll)
                }
            }

            "ENDTURN" -> {
                internalEndTurn()
            }

            "ROBBER" -> {
                if (parts.size != 4) return
                val playerId = parts[1]
                val q = parts[2].toIntOrNull() ?: return
                val r = parts[3].toIntOrNull() ?: return
                val coord = HexCoord(q, r)
                placeRobberInternal(playerId, coord)
            }

            "TRADE_OFFER" -> {
                if (parts.size != 5) return
                val fromId = parts[1]
                val toId = parts[2]
                val offer = decodeResourceMap(parts[3])
                val request = decodeResourceMap(parts[4])
                mutate { rs ->
                    rs.copy(pendingTrade = TradeOffer(fromId, toId, offer, request))
                }
            }

            "TRADE_ACCEPT" -> {
                val trade = _state.value.pendingTrade ?: return
                mutate { rs -> applyTrade(rs, trade) }
            }

            "TRADE_REJECT" -> {
                mutate { rs ->
                    eventText.value = "Trade rejected."
                    rs.copy(pendingTrade = null)
                }
            }

            "TRADE_CANCEL" -> {
                mutate { rs ->
                    eventText.value = "Trade cancelled."
                    rs.copy(pendingTrade = null)
                }
            }

            "TRADE_FLAMINGO" -> {
                if (parts.size != 4) return
                val playerId = parts[1]
                val giveIdx = parts[2].toIntOrNull() ?: return
                val getIdx = parts[3].toIntOrNull() ?: return
                val give = resourceOrder.getOrNull(giveIdx) ?: return
                val get = resourceOrder.getOrNull(getIdx) ?: return
                applyFlamingoTradeInternal(playerId, give, get)
            }

        }
    }

    // -------------------------------------------------------------------------
    // Public API for the UI (local + send over network)
    // -------------------------------------------------------------------------

    fun onLocalVertexTap(playerId: String, v: VertexKey) {
        val current = _state.value
        if (current.pendingTrade != null) return
        tryPlaceSettlement(playerId, v)
        p2p.send("GAME:SETTLEMENT:$playerId:${v.q}:${v.r}:${v.corner}")
    }

    fun onLocalEdgeTap(playerId: String, e: EdgeKey) {
        val current = _state.value
        if (current.pendingTrade != null) return
        tryPlaceRoad(playerId, e)
        p2p.send("GAME:ROAD:$playerId:${e.q}:${e.r}:${e.edge}")
    }

    fun onLocalRollDice(playerId: String) {
        val current = _state.value
        if (current.turn != playerId ||
            current.phase != GamePhase.PLAY ||
            current.hasRolledThisTurn ||
            current.pendingTrade != null
        ) {
            return
        }

        val roll = (1..6).random() + (1..6).random()

        if (roll == 7) {
            startRobberPhase(playerId)
        } else {
            applyRollResult(roll)
        }

        p2p.send("GAME:ROLL:$playerId:$roll")
    }

    fun onLocalEndTurn(playerId: String) {
        val current = _state.value
        if (current.turn != playerId ||
            current.phase != GamePhase.PLAY ||
            current.pendingTrade != null
        ) return

        internalEndTurn()
        p2p.send("GAME:ENDTURN:$playerId")
    }

    fun onLocalPlaceRobber(playerId: String, coord: HexCoord) {
        placeRobberInternal(playerId, coord)
        p2p.send("GAME:ROBBER:$playerId:${coord.q}:${coord.r}")
    }

    // ---- Trade entry points from UI ----

    fun onLocalProposeTrade(
        playerId: String,
        offer: Map<Resource, Int>,
        request: Map<Resource, Int>
    ) {
        // Filter zeroes early
        val cleanOffer = offer.filterValues { it > 0 }
        val cleanRequest = request.filterValues { it > 0 }

        if (cleanOffer.isEmpty() || cleanRequest.isEmpty()) return

        mutate { rs ->
            if (!canStartTrade(playerId, rs)) return@mutate rs

            val me = rs.players[playerId] ?: return@mutate rs
            // Make sure they can afford what they’re offering
            for ((res, amt) in cleanOffer) {
                if ((me.resources[res] ?: 0) < amt) {
                    eventText.value = "Not enough ${res.name.lowercase()} to offer."
                    return@mutate rs
                }
            }

            val other = if (playerId == "host") "guest" else "host"
            val trade = TradeOffer(playerId, other, cleanOffer, cleanRequest)
            eventText.value = "Trade offer sent."
            rs.copy(pendingTrade = trade)
        }

        val other = if (playerId == "host") "guest" else "host"
        val offerStr = encodeResourceMap(offer)
        val requestStr = encodeResourceMap(request)
        p2p.send("GAME:TRADE_OFFER:$playerId:$other:$offerStr:$requestStr")
    }

    fun onLocalAcceptTrade(playerId: String) {
        val snapshot = _state.value
        val trade = snapshot.pendingTrade ?: return
        if (trade.toId != playerId) return

        mutate { rs -> applyTrade(rs, trade) }
        p2p.send("GAME:TRADE_ACCEPT:$playerId")
    }

    fun onLocalRejectTrade(playerId: String) {
        val snapshot = _state.value
        val trade = snapshot.pendingTrade ?: return
        if (trade.toId != playerId) return

        mutate { rs ->
            eventText.value = "${rs.players[playerId]?.name ?: playerId} declined the trade."
            rs.copy(pendingTrade = null)
        }
        p2p.send("GAME:TRADE_REJECT:$playerId")
    }

    fun onLocalCancelTrade(playerId: String) {
        val snapshot = _state.value
        val trade = snapshot.pendingTrade ?: return
        if (trade.fromId != playerId) return

        mutate { rs ->
            eventText.value = "Trade cancelled."
            rs.copy(pendingTrade = null)
        }
        p2p.send("GAME:TRADE_CANCEL:$playerId")
    }
    private fun applyFlamingoTradeInternal(playerId: String, give: Resource, get: Resource) {
        mutate { rs ->
            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            val have = me.resources[give] ?: 0
            if (have < 4) {
                // silently ignore bad messages
                return@mutate rs
            }

            val newRes = me.resources.toMutableMap()
            newRes[give] = have - 4
            newRes[get] = (newRes[get] ?: 0) + 1

            players[playerId] = me.copy(resources = newRes)
            eventText.value = "Flamingo Run trade complete."
            rs.copy(players = players)
        }
    }
    fun onLocalFlamingoTrade(playerId: String, give: Resource, get: Resource) {
        applyFlamingoTradeInternal(playerId, give, get)
        val giveIdx = resourceOrder.indexOf(give)
        val getIdx = resourceOrder.indexOf(get)
        p2p.send("GAME:TRADE_FLAMINGO:$playerId:$giveIdx:$getIdx")
    }
    fun consumeEvent() {
        eventText.value = null
    }

    // -------------------------------------------------------------------------
    // Setup prompts for the bottom message bar
    // -------------------------------------------------------------------------

    fun setupPromptFor(
        playerId: String,
        rs: RoomState = _state.value
    ): String? {
        if (rs.phase != GamePhase.SETUP) return null
        val starting = rs.startingPlayerId ?: return null
        val currentIndex = rs.setupIndex

        val isFirstRound = currentIndex in 0..1
        val roundLabel = if (isFirstRound) "first" else "second"

        val expectedPlayer = setupPlayerForIndex(starting, currentIndex)
        val myTurn = rs.turn == playerId && expectedPlayer == playerId

        return if (!myTurn) {
            if (rs.setupPlacedSettlement && !rs.setupPlacedRoad) {
                "Waiting for opponent to place $roundLabel path"
            } else {
                "Waiting for opponent to place $roundLabel dorm and path"
            }
        } else {
            if (!rs.setupPlacedSettlement) {
                "Place your $roundLabel dorm"
            } else if (!rs.setupPlacedRoad) {
                "Place your $roundLabel path"
            } else {
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // Debug dump
    // -------------------------------------------------------------------------

    companion object {
        private const val HEX_TAG = "HEXDEBUG"
    }

    fun debugDumpEverything() {
        val graph = boardGraph
        val rs = _state.value
        val vertices = graph.vertices
        val tilesByCoord = graph.tilesByCoord

        Log.d(HEX_TAG, "===== VERTEX -> TILES DUMP =====")
        Log.d(
            HEX_TAG,
            "Total tiles = ${graph.tiles.size}, total unique vertices = ${vertices.size}"
        )

        vertices.toSortedMap(compareBy<VertexKey> { it.q }.thenBy { it.r }.thenBy { it.corner })
            .forEach { (vKey, vertex) ->
                val tileList = vertex.tileCoords
                    .sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })
                    .mapNotNull { coord -> tilesByCoord[coord] }
                    .joinToString { "(${it.coord.q},${it.coord.r})#${it.number}:${it.resource.name}" }

                Log.d(
                    HEX_TAG,
                    "V(q=${vKey.q}, r=${vKey.r}, c=${vKey.corner}) touches ${vertex.tileCoords.size} tiles -> $tileList"
                )
            }

        Log.d(HEX_TAG, "Robber at: ${rs.robberCoord}")
        Log.d(HEX_TAG, "===== END VERTEX DUMP =====")

        rs.players.forEach { (id, player) ->
            Log.d(HEX_TAG, "===== SETTLEMENTS FOR ${player.name} (id=$id) =====")
            player.settlements.forEach { vKey ->
                val vertex = vertices[vKey]
                val tileList = vertex?.tileCoords
                    ?.sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })
                    ?.mapNotNull { coord -> tilesByCoord[coord] }
                    ?.joinToString { "(${it.coord.q},${it.coord.r})#${it.number}:${it.resource.name}" }
                    ?: "no tiles"

                Log.d(
                    HEX_TAG,
                    "Settlement at V(q=${vKey.q}, r=${vKey.r}, c=${vKey.corner}) touches ${
                        vertex?.tileCoords?.size ?: 0
                    } tiles -> $tileList"
                )
            }
            Log.d(HEX_TAG, "===== END SETTLEMENT DUMP =====")
        }
    }

    // -------------------------------------------------------------------------
    // Board generation
    // -------------------------------------------------------------------------

    private fun standardCatanBoardRandom(): List<Tile> {
        val coords = mutableListOf<HexCoord>()
        for (q in -2..2) {
            for (r in -2..2) {
                if (abs(q + r) <= 2) {
                    coords.add(HexCoord(q, r))
                }
            }
        }

        val resourceBag = mutableListOf<Resource>().apply {
            repeat(4) { add(Resource.CONCRETE) }
            repeat(3) { add(Resource.STUDENT) }
            repeat(4) { add(Resource.BUCKY) }
            repeat(4) { add(Resource.CHAIR) }
            repeat(4) { add(Resource.CHEESE_CURD) }
        }

        val numberBag = mutableListOf<Int>().apply {
            addAll(
                listOf(
                    2,
                    3, 3,
                    4, 4,
                    5, 5, 5,
                    6, 6,
                    8, 8,
                    9, 9,
                    10, 10,
                    11, 11,
                    12
                )
            )
        }

        val sortedCoords = coords.sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })
        val resList = resourceBag.shuffled()
        val numList = numberBag.shuffled()

        return sortedCoords.mapIndexed { index, coord ->
            Tile(
                coord = coord,
                resource = resList[index],
                number = numList[index]
            )
        }
    }
}
