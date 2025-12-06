package com.cs407.settlersofmadison.ui.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.P2PHolder
import com.cs407.settlersofmadison.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

// Overall phase – now includes SETUP and ROBBER.
enum class GamePhase { SETUP, PLAY, ROBBER }
enum class BuildType { ROAD, SETTLEMENT, CITY }

data class TradeOffer(
    val fromId: String,
    val toId: String,
    val offer: Map<Resource, Int>,
    val request: Map<Resource, Int>
)

data class PlayerState(
    val id: String,
    val name: String,
    val resources: Map<Resource, Int> = emptyMap(),
    val settlements: Set<VertexKey> = emptySet(),
    val roads: Set<EdgeKey> = emptySet(),
    val points: Int = 0
)

data class RoomState(
    val tiles: List<Tile>,
    val players: Map<String, PlayerState>,
    val turn: String,
    val phase: GamePhase = GamePhase.PLAY,
    val lastRoll: Int? = null,
    val hasRolledThisTurn: Boolean = false,
    val robberCoord: HexCoord? = null,
    val robberMoverId: String? = null,
    val startingPlayerId: String? = null,
    val pendingTrade: TradeOffer? = null,
    val setupIndex: Int = 0,
    val setupPlacedSettlement: Boolean = false,
    val setupPlacedRoad: Boolean = false,
    val setupCurrentSettlementVertex: VertexKey? = null,
)

// CHANGED: Accept seed in constructor
class GameViewModel(private val seed: Long) : ViewModel() {

    private val p2p = P2PHolder.service

    // CHANGED: Use seeded random for board generation
    private val boardTiles: List<Tile> = standardCatanBoardRandom(seed)

    private val boardGraph: BoardGraph = buildBoardGraph(boardTiles)

    // CHANGED: Use seeded random for starting player
    private val startingPlayerId: String =
        if (Random(seed).nextBoolean()) "host" else "guest"

    private val _state = MutableStateFlow(
        RoomState(
            tiles = boardGraph.tiles,
            players = mapOf(
                "host" to PlayerState("host", "Host"),
                "guest" to PlayerState("guest", "Guest")
            ),
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

    val eventText = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            p2p.incoming.collect { line ->
                if (!line.startsWith("GAME:")) return@collect
                val payload = line.removePrefix("GAME:")
                handleIncomingGameMessage(payload)
            }
        }
    }
    init {
        Log.d("GameVM", "Creating GameViewModel with seed=$seed, role board=${startingPlayerId}")
    }
    private fun mutate(block: (RoomState) -> RoomState) {
        _state.value = block(_state.value)
    }

    private val buildCosts: Map<BuildType, Map<Resource, Int>> = mapOf(
        BuildType.ROAD to mapOf(Resource.CONCRETE to 1, Resource.BUCKY to 1),
        BuildType.SETTLEMENT to mapOf(Resource.CONCRETE to 1, Resource.STUDENT to 1, Resource.CHAIR to 1, Resource.CHEESE_CURD to 1)
    )
    private val tradableResources: List<Resource> = listOf(
        Resource.CONCRETE,
        Resource.STUDENT,
        Resource.BUCKY,
        Resource.CHAIR,
        Resource.CHEESE_CURD
    )

    private val resourceOrder: Array<Resource> = tradableResources.toTypedArray()

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

    private fun isAdjacentToSettlement(rs: RoomState, v: VertexKey): Boolean {
        val neighbors = adjacentVerticesOf(v)
        return rs.players.values.any { player ->
            player.settlements.any { it in neighbors }
        }
    }

    private fun setupPlayerForIndex(starting: String, index: Int): String {
        val other = if (starting == "host") "guest" else "host"
        return when (index) {
            0 -> starting
            1 -> other
            2 -> other
            3 -> starting
            else -> starting
        }
    }

    private fun advanceSetup(rs: RoomState): RoomState {
        if (rs.phase != GamePhase.SETUP) return rs
        val starting = rs.startingPlayerId ?: rs.turn
        val nextIndex = rs.setupIndex + 1
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

    private fun tryPlaceSettlement(playerId: String, v: VertexKey) {
        mutate { rs ->
            if (rs.pendingTrade != null) return@mutate rs
            if (!boardGraph.vertices.containsKey(v)) return@mutate rs
            if (rs.players.values.any { v in it.settlements }) {
                eventText.value = "Vertex already occupied."
                return@mutate rs
            }
            if (isAdjacentToSettlement(rs, v)) {
                eventText.value = "Too close to another settlement."
                return@mutate rs
            }
            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            if (rs.phase == GamePhase.SETUP) {
                val starting = rs.startingPlayerId ?: playerId
                val expected = setupPlayerForIndex(starting, rs.setupIndex)
                if (expected != playerId || rs.turn != playerId) return@mutate rs
                if (rs.setupPlacedSettlement) return@mutate rs
                if (me.settlements.size >= 2) return@mutate rs

                val newSet = me.settlements.toMutableSet().apply { add(v) }
                val updatedPlayer = me.copy(settlements = newSet, points = newSet.size)
                players[playerId] = updatedPlayer
                eventText.value = "${updatedPlayer.name} placed a settlement."

                val newState = rs.copy(players = players, setupPlacedSettlement = true, setupCurrentSettlementVertex = v)
                debugDumpEverything()
                return@mutate newState
            }

            if (rs.phase != GamePhase.PLAY || rs.turn != playerId) return@mutate rs
            val incidentEdges = edgesIncidentToVertex(v)
            val hasConnectingRoad = incidentEdges.any { it in me.roads }
            if (!hasConnectingRoad) return@mutate rs
            if (!canAfford(me, BuildType.SETTLEMENT)) {
                eventText.value = "Not enough resources to build a dorm."
                return@mutate rs
            }
            val paidPlayer = payFor(me, BuildType.SETTLEMENT)
            val newSet = paidPlayer.settlements.toMutableSet().apply { add(v) }
            val updatedPlayer = paidPlayer.copy(settlements = newSet, points = newSet.size)
            players[playerId] = updatedPlayer

            val vertex = boardGraph.vertices[v]
            val touchingTiles = vertex?.tileCoords
                ?.mapNotNull { coord -> boardGraph.tilesByCoord[coord] }
                ?.sortedWith(compareBy<Tile> { it.coord.q }.thenBy { it.coord.r })
                ?: emptyList()
            val touchingDesc = if (touchingTiles.isEmpty()) "no tiles" else touchingTiles.joinToString { "${it.number}-${it.resource.name}" }
            eventText.value = "${updatedPlayer.name} built a dorm touching: $touchingDesc"
            val newState = rs.copy(players = players)
            debugDumpEverything()
            newState
        }
    }

    private fun tryPlaceRoad(playerId: String, e: EdgeKey) {
        mutate { rs ->
            if (rs.pendingTrade != null) return@mutate rs
            val edge = boardGraph.edges[e] ?: return@mutate rs
            if (rs.players.values.any { e in it.roads }) return@mutate rs
            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            if (rs.phase == GamePhase.SETUP) {
                val starting = rs.startingPlayerId ?: playerId
                val expected = setupPlayerForIndex(starting, rs.setupIndex)
                if (expected != playerId || rs.turn != playerId) return@mutate rs
                if (!rs.setupPlacedSettlement) return@mutate rs
                if (rs.setupPlacedRoad) return@mutate rs
                val v = rs.setupCurrentSettlementVertex ?: return@mutate rs
                val incidentEdges = edgesIncidentToVertex(v)
                if (e !in incidentEdges) return@mutate rs

                val newRoads = me.roads.toMutableSet().apply { add(e) }
                val updatedPlayer = me.copy(roads = newRoads)
                players[playerId] = updatedPlayer
                eventText.value = "${updatedPlayer.name} placed an initial road."
                val afterRoad = rs.copy(players = players, setupPlacedRoad = true)
                debugDumpEverything()
                return@mutate advanceSetup(afterRoad)
            }

            if (rs.phase != GamePhase.PLAY || rs.turn != playerId) return@mutate rs
            val hasNetwork = me.settlements.isNotEmpty() || me.roads.isNotEmpty()
            if (hasNetwork) {
                val networkVertices = mutableSetOf<VertexKey>().apply {
                    addAll(me.settlements)
                    for (rKey in me.roads) {
                        val rEdge = boardGraph.edges[rKey] ?: continue
                        add(rEdge.vertices.first); add(rEdge.vertices.second)
                    }
                }
                val (v1, v2) = edge.vertices
                if (v1 !in networkVertices && v2 !in networkVertices) return@mutate rs
            }
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

    fun legalRoadEdgesFor(playerId: String, rs: RoomState = _state.value): Set<EdgeKey> {
        val me = rs.players[playerId] ?: return emptySet()
        if (rs.pendingTrade != null) return emptySet()
        if (rs.phase == GamePhase.SETUP) {
            val starting = rs.startingPlayerId ?: playerId
            val expected = setupPlayerForIndex(starting, rs.setupIndex)
            if (expected != playerId || rs.turn != playerId) return emptySet()
            if (!rs.setupPlacedSettlement || rs.setupPlacedRoad) return emptySet()
            val v = rs.setupCurrentSettlementVertex ?: return emptySet()
            val takenEdges = rs.players.values.flatMapTo(mutableSetOf()) { it.roads }
            return edgesIncidentToVertex(v).filter { it !in takenEdges }.toSet()
        }
        if (rs.phase != GamePhase.PLAY) return emptySet()
        if (!canAfford(me, BuildType.ROAD)) return emptySet()
        val hasNetwork = me.settlements.isNotEmpty() || me.roads.isNotEmpty()
        val networkVertices: Set<VertexKey> = if (!hasNetwork) emptySet() else buildSet {
            addAll(me.settlements)
            for (rKey in me.roads) {
                val rEdge = boardGraph.edges[rKey] ?: continue
                add(rEdge.vertices.first); add(rEdge.vertices.second)
            }
        }
        val takenEdges = rs.players.values.flatMapTo(mutableSetOf()) { it.roads }
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

    fun legalSettlementVerticesFor(playerId: String, rs: RoomState = _state.value): Set<VertexKey> {
        if (rs.phase != GamePhase.PLAY) return emptySet()
        if (rs.pendingTrade != null) return emptySet()
        val me = rs.players[playerId] ?: return emptySet()
        if (!canAfford(me, BuildType.SETTLEMENT)) return emptySet()
        val occupied = rs.players.values.flatMapTo(mutableSetOf()) { it.settlements }
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
                        if (robbedCoord != null && tile.coord == robbedCoord) continue

                        // Only numbered, non-water tiles pay out
                        if (tile.number == roll && tile.resource != Resource.LAKE) {
                            newRes[tile.resource] = (newRes[tile.resource] ?: 0) + 1
                            gained++
                            gainsByPlayerName
                                .getOrPut(player.name) { mutableListOf() }
                                .add(tile.resource)
                        }
                    }
                }
                if (gained > 0) players[id] = player.copy(resources = newRes)
            }
            val msg = if (gainsByPlayerName.isEmpty()) "Rolled $roll. No resources." else
                "Rolled $roll → " + gainsByPlayerName.entries.joinToString(" | ") { (name, list) ->
                    val summary = list.groupingBy { it }.eachCount().entries.joinToString { (res, count) ->
                        val prettyName = res.name.lowercase().replaceFirstChar { it.uppercaseChar() }
                        "$count $prettyName"
                    }
                    "$name: $summary"
                }
            eventText.value = msg
            rs.copy(players = players, lastRoll = roll, hasRolledThisTurn = true)
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

    private fun startRobberPhase(playerId: String) {
        mutate { rs ->
            eventText.value = "${rs.players[playerId]?.name ?: playerId} rolled 7. Tap a tile to move the Badger Patrol."
            rs.copy(phase = GamePhase.ROBBER, lastRoll = 7, robberMoverId = playerId, hasRolledThisTurn = true)
        }
    }

    private fun placeRobberInternal(playerId: String, coord: HexCoord) {
        mutate { rs ->
            if (rs.phase != GamePhase.ROBBER || rs.turn != playerId || rs.robberMoverId != playerId) return@mutate rs
            if (rs.robberCoord != null && rs.robberCoord == coord) return@mutate rs
            eventText.value = "${rs.players[playerId]?.name ?: playerId} moved the Badger Patrol."
            rs.copy(robberCoord = coord, phase = GamePhase.PLAY, robberMoverId = null, hasRolledThisTurn = true)
        }
    }

    fun canStartTrade(playerId: String, rs: RoomState = _state.value): Boolean {
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

    private fun applyFlamingoTradeInternal(playerId: String, give: Resource, get: Resource) {
        mutate { rs ->
            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs
            val have = me.resources[give] ?: 0
            if (have < 4) return@mutate rs
            val newRes = me.resources.toMutableMap()
            newRes[give] = have - 4
            newRes[get] = (newRes[get] ?: 0) + 1
            players[playerId] = me.copy(resources = newRes)
            eventText.value = "Flamingo Run trade complete."
            rs.copy(players = players)
        }
    }

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
                tryPlaceSettlement(playerId, VertexKey(q, r, corner))
            }
            "ROAD" -> {
                if (parts.size != 5) return
                val playerId = parts[1]
                val q = parts[2].toIntOrNull() ?: return
                val r = parts[3].toIntOrNull() ?: return
                val edgeIndex = parts[4].toIntOrNull() ?: return
                tryPlaceRoad(playerId, EdgeKey(q, r, edgeIndex))
            }
            "ROLL" -> {
                if (parts.size != 3) return
                val playerId = parts[1]
                val roll = parts[2].toIntOrNull() ?: return
                if (roll == 7) startRobberPhase(playerId) else applyRollResult(roll)
            }
            "ENDTURN" -> internalEndTurn()
            "ROBBER" -> {
                if (parts.size != 4) return
                val playerId = parts[1]
                val q = parts[2].toIntOrNull() ?: return
                val r = parts[3].toIntOrNull() ?: return
                placeRobberInternal(playerId, HexCoord(q, r))
            }
            "TRADE_OFFER" -> {
                if (parts.size != 5) return
                val fromId = parts[1]
                val toId = parts[2]
                val offer = decodeResourceMap(parts[3])
                val request = decodeResourceMap(parts[4])
                mutate { rs -> rs.copy(pendingTrade = TradeOffer(fromId, toId, offer, request)) }
            }
            "TRADE_ACCEPT" -> {
                val trade = _state.value.pendingTrade ?: return
                mutate { rs -> applyTrade(rs, trade) }
            }
            "TRADE_REJECT" -> mutate { rs -> eventText.value = "Trade rejected."; rs.copy(pendingTrade = null) }
            "TRADE_CANCEL" -> mutate { rs -> eventText.value = "Trade cancelled."; rs.copy(pendingTrade = null) }
            "TRADE_FLAMINGO" -> {
                if (parts.size != 4) return
                val playerId = parts[1]
                val giveIdx = parts[2].toIntOrNull() ?: return
                val getIdx = parts[3].toIntOrNull() ?: return
                applyFlamingoTradeInternal(playerId, resourceOrder.getOrNull(giveIdx) ?: return, resourceOrder.getOrNull(getIdx) ?: return)
            }
        }
    }

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
        if (current.turn != playerId || current.phase != GamePhase.PLAY || current.hasRolledThisTurn || current.pendingTrade != null) return
        val roll = (1..6).random() + (1..6).random()
        if (roll == 7) startRobberPhase(playerId) else applyRollResult(roll)
        p2p.send("GAME:ROLL:$playerId:$roll")
    }
    fun onLocalEndTurn(playerId: String) {
        val current = _state.value
        if (current.turn != playerId || current.phase != GamePhase.PLAY || current.pendingTrade != null) return
        internalEndTurn()
        p2p.send("GAME:ENDTURN:$playerId")
    }
    fun onLocalPlaceRobber(playerId: String, coord: HexCoord) {
        placeRobberInternal(playerId, coord)
        p2p.send("GAME:ROBBER:$playerId:${coord.q}:${coord.r}")
    }
    fun onLocalProposeTrade(playerId: String, offer: Map<Resource, Int>, request: Map<Resource, Int>) {
        val cleanOffer = offer.filterValues { it > 0 }
        val cleanRequest = request.filterValues { it > 0 }
        if (cleanOffer.isEmpty() || cleanRequest.isEmpty()) return
        mutate { rs ->
            if (!canStartTrade(playerId, rs)) return@mutate rs
            val me = rs.players[playerId] ?: return@mutate rs
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
        mutate { rs -> eventText.value = "${rs.players[playerId]?.name ?: playerId} declined the trade."; rs.copy(pendingTrade = null) }
        p2p.send("GAME:TRADE_REJECT:$playerId")
    }
    fun onLocalCancelTrade(playerId: String) {
        val snapshot = _state.value
        val trade = snapshot.pendingTrade ?: return
        if (trade.fromId != playerId) return
        mutate { rs -> eventText.value = "Trade cancelled."; rs.copy(pendingTrade = null) }
        p2p.send("GAME:TRADE_CANCEL:$playerId")
    }
    fun onLocalFlamingoTrade(playerId: String, give: Resource, get: Resource) {
        applyFlamingoTradeInternal(playerId, give, get)
        val giveIdx = resourceOrder.indexOf(give)
        val getIdx = resourceOrder.indexOf(get)
        p2p.send("GAME:TRADE_FLAMINGO:$playerId:$giveIdx:$getIdx")
    }
    fun consumeEvent() { eventText.value = null }
    fun setupPromptFor(playerId: String, rs: RoomState = _state.value): String? {
        if (rs.phase != GamePhase.SETUP) return null
        val starting = rs.startingPlayerId ?: return null
        val currentIndex = rs.setupIndex
        val isFirstRound = currentIndex in 0..1
        val roundLabel = if (isFirstRound) "first" else "second"
        val expectedPlayer = setupPlayerForIndex(starting, currentIndex)
        val myTurn = rs.turn == playerId && expectedPlayer == playerId
        return if (!myTurn) {
            if (rs.setupPlacedSettlement && !rs.setupPlacedRoad) "Waiting for opponent to place $roundLabel path"
            else "Waiting for opponent to place $roundLabel dorm and path"
        } else {
            if (!rs.setupPlacedSettlement) "Place your $roundLabel dorm"
            else if (!rs.setupPlacedRoad) "Place your $roundLabel path"
            else null
        }
    }
    fun debugDumpEverything() { /* ... unchanged ... */ }
    private val MAX_TERRAIN_CLUSTER = 3
    // CHANGED: Use the seed for deterministic board generation
    private fun standardCatanBoardRandom(seed: Long): List<Tile> {
        val rng = Random(seed)

        // 1) Radius-3 hex coordinates (37 tiles)
        val coords = hexCoords(radius = 3)
            .sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })

        val neighbors = buildNeighborMap(coords)

        // 2) Place terrain (resources + 1 water)
        val resourceLayout = generateResourceLayout(coords, neighbors, rng)

        // 3) Place numbers with adjacency constraints
        val numberLayout = generateNumberLayout(coords, neighbors, resourceLayout, rng)

        // 4) Build final tiles (water gets number 0, which never triggers)
        return coords.map { coord ->
            val res = resourceLayout[coord]!!
            val num = numberLayout[coord] ?: 0
            Tile(
                coord = coord,
                resource = res,
                number = num
            )
        }
    }

    // Build axial hex coordinates for a hex of given radius
    private fun hexCoords(radius: Int): List<HexCoord> {
        val result = mutableListOf<HexCoord>()
        for (q in -radius..radius) {
            for (r in -radius..radius) {
                val s = -q - r
                if (maxOf(kotlin.math.abs(q), kotlin.math.abs(r), kotlin.math.abs(s)) <= radius) {
                    result.add(HexCoord(q, r))
                }
            }
        }
        return result
    }

    // Neighbor map using axial directions
    private fun buildNeighborMap(coords: List<HexCoord>): Map<HexCoord, List<HexCoord>> {
        val coordSet = coords.toSet()
        val deltas = listOf(
            HexCoord(1, 0),
            HexCoord(-1, 0),
            HexCoord(0, 1),
            HexCoord(0, -1),
            HexCoord(1, -1),
            HexCoord(-1, 1)
        )
        val neighbors = mutableMapOf<HexCoord, MutableList<HexCoord>>()
        for (c in coords) {
            val list = mutableListOf<HexCoord>()
            for (d in deltas) {
                val n = HexCoord(c.q + d.q, c.r + d.r)
                if (n in coordSet) list.add(n)
            }
            neighbors[c] = list
        }
        return neighbors
    }

    // Resource layout: 1 WATER + scaled counts of your 5 resources,
    // with a max-cluster size constraint.
    private fun generateResourceLayout(
        coords: List<HexCoord>,
        neighbors: Map<HexCoord, List<HexCoord>>,
        rng: Random
    ): Map<HexCoord, Resource> {
        val tileCount = coords.size
        if (tileCount != 37) {
            Log.w("BoardGen", "Unexpected tileCount=$tileCount, terrain distribution tuned for 37.")
        }

        val resourceBag = mutableListOf<Resource>().apply {
            add(Resource.LAKE)

            // 36 remaining tiles:
            repeat(7) { add(Resource.CONCRETE) }
            repeat(6) { add(Resource.STUDENT) }
            repeat(7) { add(Resource.BUCKY) }
            repeat(8) { add(Resource.CHAIR) }
            repeat(8) { add(Resource.CHEESE_CURD) }
        }

        if (resourceBag.size != tileCount) {
            Log.w(
                "BoardGen",
                "Resource bag size (${resourceBag.size}) != coords size ($tileCount)."
            )
        }

        // Try *a lot* of random permutations with constraints
        repeat(50_000) {
            val shuffled = resourceBag.shuffled(rng)
            val layout = coords.zip(shuffled).toMap()
            if (isResourceLayoutValid(layout, neighbors)) {
                return layout
            }
        }

        // Fallback: still random, just without the cluster constraint
        Log.w("BoardGen", "Falling back to unconstrained resource layout (random).")
        val fallback = coords.zip(resourceBag.shuffled(rng)).toMap()
        return fallback
    }

    // No large groups of same terrain (except water, which we don’t really care about).
    private fun isResourceLayoutValid(
        layout: Map<HexCoord, Resource>,
        neighbors: Map<HexCoord, List<HexCoord>>
    ): Boolean {
        val terrainTypes = listOf(
            Resource.CONCRETE,
            Resource.STUDENT,
            Resource.BUCKY,
            Resource.CHAIR,
            Resource.CHEESE_CURD
            // WATER is allowed to do whatever; and we only have 1 anyway
        )

        for (terrain in terrainTypes) {
            val visited = mutableSetOf<HexCoord>()
            for ((coord, res) in layout) {
                if (res != terrain || coord in visited) continue

                // BFS to measure cluster size
                var count = 0
                val queue: ArrayDeque<HexCoord> = ArrayDeque()
                queue.add(coord)
                visited.add(coord)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    count++
                    if (count > MAX_TERRAIN_CLUSTER) return false

                    for (n in neighbors[current].orEmpty()) {
                        if (n !in visited && layout[n] == terrain) {
                            visited.add(n)
                            queue.add(n)
                        }
                    }
                }
            }
        }
        return true
    }

    // Number layout:
    // - No same-number neighbors
    // - No 6 touching 8
    // - No 2 touching 12
    // WATER tiles get number 0 (ignored in checks and payouts).
    private fun generateNumberLayout(
        coords: List<HexCoord>,
        neighbors: Map<HexCoord, List<HexCoord>>,
        resourceLayout: Map<HexCoord, Resource>,
        rng: Random
    ): Map<HexCoord, Int?> {
        val nonWaterCoords = coords.filter { resourceLayout[it] != Resource.LAKE }
        val waterCoords = coords.filter { resourceLayout[it] == Resource.LAKE }

        val numberedCount = nonWaterCoords.size
        if (numberedCount != 36) {
            Log.w("BoardGen", "Unexpected numberedCount=$numberedCount, number bag tuned for 36.")
        }

        val numberBag = mutableListOf<Int>().apply {
            repeat(2) { add(2) }
            repeat(3) { add(3) }
            repeat(4) { add(4) }
            repeat(5) { add(5) }
            repeat(4) { add(6) }
            repeat(4) { add(8) }
            repeat(4) { add(9) }
            repeat(4) { add(10) }
            repeat(4) { add(11) }
            repeat(2) { add(12) }
        }

        if (numberBag.size != numberedCount) {
            Log.w(
                "BoardGen",
                "Number bag size (${numberBag.size}) != non-water coords size ($numberedCount)."
            )
        }

        // Try many random assignments with adjacency rules
        repeat(50_000) {
            val shuffled = numberBag.shuffled(rng)
            val layout = mutableMapOf<HexCoord, Int?>()

            nonWaterCoords.zip(shuffled).forEach { (coord, num) ->
                layout[coord] = num
            }
            waterCoords.forEach { coord ->
                layout[coord] = 0
            }

            if (isNumberLayoutValid(layout, neighbors)) {
                return layout
            }
        }

        // Fallback: random numbers but without adjacency constraints
        Log.w("BoardGen", "Falling back to unconstrained number layout (random).")
        val fallback = mutableMapOf<HexCoord, Int?>()
        val shuffled = numberBag.shuffled(rng)
        nonWaterCoords.zip(shuffled).forEach { (coord, num) -> fallback[coord] = num }
        waterCoords.forEach { coord -> fallback[coord] = 0 }
        return fallback
    }

    private fun isNumberLayoutValid(
        layout: Map<HexCoord, Int?>,
        neighbors: Map<HexCoord, List<HexCoord>>
    ): Boolean {
        for ((coord, a) in layout) {
            val nA = a ?: 0
            if (nA == 0) continue // water / no-number tile

            for (neighbor in neighbors[coord].orEmpty()) {
                val b = layout[neighbor] ?: 0
                if (b == 0) continue

                // Same number can't touch
                if (nA == b) return false

                // 6 & 8 can't touch
                if ((nA == 6 && b == 8) || (nA == 8 && b == 6)) return false

                // 2 & 12 can't touch
                if ((nA == 2 && b == 12) || (nA == 12 && b == 2)) return false
            }
        }
        return true
    }
}

// CHANGED: Factory to inject the seed
class GameViewModelFactory(private val seed: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(seed) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}