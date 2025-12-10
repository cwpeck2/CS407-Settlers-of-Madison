package com.cs407.settlersofmadison.ui.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.P2PHolder
import com.cs407.settlersofmadison.domain.model.*
import com.cs407.settlersofmadison.ui.lobby.ProfileSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
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
    val cities: Set<VertexKey> = emptySet(),       // NEW
    val roads: Set<EdgeKey> = emptySet(),
    val bonusPoints: Int = 0,                      // for Badger Spirit etc later
    val points: Int = 0,
    val color: Long? = null,           // ARGB long (preferred color)
    val avatarUri: String? = null
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
    // playerId -> how many cards they still must discard after a 7
    val robberDiscardsNeeded: Map<String, Int> = emptyMap(),
    val startingPlayerId: String? = null,
    val pendingTrade: TradeOffer? = null,
    val setupIndex: Int = 0,
    val setupPlacedSettlement: Boolean = false,
    val setupPlacedRoad: Boolean = false,
    val setupCurrentSettlementVertex: VertexKey? = null,
    val portVertices: Set<VertexKey> = emptySet(),
    val bascomTradeTokens: Map<String, Int> = emptyMap(),

    // Capitol lvl1: reroll tokens (max 1 for now; lvl2 will allow 2 once cities exist)
    val capitolRerollTokens: Map<String, Int> = emptyMap(),

    // Engineering lvl1: dev-card boost active for this turn (no dev cards yet, just flag)
    val engineeringBoostThisTurn: Set<String> = emptySet(),
    val winnerId: String? = null
)

data class RerollOffer(
    val playerId: String,
    val firstRoll: Int
)

// CHANGED: Accept seed in constructor
class GameViewModel(private val seed: Long) : ViewModel() {
    val rerollOffer = MutableStateFlow<RerollOffer?>(null)
    val rerollOfferState: StateFlow<RerollOffer?> = rerollOffer

    private val HEX_DIRECTIONS = listOf(
        HexCoord(1, 0),
        HexCoord(1, -1),
        HexCoord(0, -1),
        HexCoord(-1, 0),
        HexCoord(-1, 1),
        HexCoord(0, 1)
    )

    private val p2p = P2PHolder.service

    // CHANGED: Use seeded random for board generation
    private val boardTiles: List<Tile> = generateBoard(seed)

    private val boardGraph: BoardGraph = buildBoardGraph(boardTiles)

    // Ports computed from the lake shoreline
    private val portVerticesOnBoard: Set<VertexKey> = computeLakePorts(boardGraph)
    private val portResourcesInternal: Map<VertexKey, Resource> =
        assignPortResources(portVerticesOnBoard)
    private val portResourceMap: Map<VertexKey, Resource> = run {
        val ports = portVerticesOnBoard.toList()
        if (ports.isEmpty()) {
            emptyMap()
        } else {
            // One of each resource, shuffled
            val resources = listOf(
                Resource.CONCRETE,
                Resource.STUDENT,
                Resource.BUCKY,
                Resource.CHAIR,
                Resource.CHEESE_CURD
            ).shuffled(Random(seed))

            val count = minOf(ports.size, resources.size)
            (0 until count).associate { i ->
                ports[i] to resources[i]
            }
        }
    }

    private val WIN_POINTS = 5

    // Recompute VP for a single player
    private fun PlayerState.withUpdatedPoints(): PlayerState {
        val total = settlements.size + cities.size * 2 + bonusPoints
        return copy(points = total)
    }

    // Recompute points for everyone and check for a winner
    private fun RoomState.withUpdatedPlayers(
        newPlayersRaw: Map<String, PlayerState>
    ): RoomState {
        val newPlayers = newPlayersRaw.mapValues { (_, p) -> p.withUpdatedPoints() }
        val winnerEntry = newPlayers.entries.firstOrNull { it.value.points >= WIN_POINTS }
        return if (winnerEntry != null) {
            copy(players = newPlayers, winnerId = winnerEntry.key)
        } else {
            copy(players = newPlayers)
        }
    }

    private fun bankRateFor(player: PlayerState, res: Resource): Int {
        // Player gets 3:1 ONLY if they have a settlement on a port
        // whose resource matches `res`.
        val hasSpecificPort = player.settlements.any { vKey ->
            portResourcesInternal[vKey] == res
        }
        return if (hasSpecificPort) 3 else 4    // resource-specific, not global
    }

    fun bascomTokensFor(playerId: String): Int =
        _state.value.bascomTradeTokens[playerId] ?: 0

    fun flamingoRateFor(playerId: String, res: Resource): Int {
        val rs = _state.value
        val player = rs.players[playerId] ?: return 4
        val tokens = rs.bascomTradeTokens[playerId] ?: 0
        val baseRate = bankRateFor(player, res)
        return if (tokens > 0) 2 else baseRate
    }

    // Does this player currently have a Bascom "super trade" available?
    fun hasBascomPower(playerId: String): Boolean {
        val rs = _state.value
        return (rs.bascomTradeTokens[playerId] ?: 0) > 0
    }

    // Convenience wrapper for UI
    fun bankRateFor(playerId: String, res: Resource): Int {
        val rs = _state.value
        val player = rs.players[playerId] ?: return 4
        return bankRateFor(player, res)
    }

    private fun assignPortResources(portVertices: Set<VertexKey>): Map<VertexKey, Resource> {
        if (portVertices.isEmpty()) return emptyMap()

        // The five tradable resources (no lake)
        val resources = listOf(
            Resource.CONCRETE,
            Resource.STUDENT,
            Resource.BUCKY,
            Resource.CHAIR,
            Resource.CHEESE_CURD
        )

        val rng = Random(seed + 1)   // deterministic but separate from boardTiles RNG
        val portsShuffled = portVertices.toList().shuffled(rng)

        val result = mutableMapOf<VertexKey, Resource>()
        for (i in portsShuffled.indices) {
            val res = resources[i % resources.size]  // if more than 5 ports, wrap
            result[portsShuffled[i]] = res
        }
        return result
    }

    // Exposed to UI for drawing
    val portResources: Map<VertexKey, Resource>
        get() = portResourcesInternal

    // Starting player still random off the seed
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
            pendingTrade = null,
            portVertices = portVerticesOnBoard
        )
    )
    val state: StateFlow<RoomState> = _state

    fun applyLocalProfile(playerId: String, profile: ProfileSettings) {
        // Update local state
        mutate { rs ->
            val players = rs.players.toMutableMap()
            val p = players[playerId] ?: return@mutate rs

            val nickname = profile.nickname.takeIf { it.isNotBlank() } ?: p.name
            val colorLong = profile.preferredColor
            val avatar = profile.avatarUri

            players[playerId] = p.copy(
                name = nickname,
                color = colorLong,
                avatarUri = avatar
            )
            rs.copy(players = players)
        }

        // Broadcast name + color to peer (avatarUri is local-only and wouldn’t work cross-device)
        val safeNickname = (profile.nickname ?: "").replace(":", " ")
        val colorStr = (profile.preferredColor ?: -1L).toString()
        p2p.send("GAME:PROFILE:$playerId:$safeNickname:$colorStr")
    }

    // Status / notification text (now shown near "Your Resources" instead of snackbar)
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
        Log.d("GameVM", "Creating GameViewModel with seed=$seed, role board=$startingPlayerId")
    }

    private fun ringOf(coord: HexCoord): Int {
        val q = coord.q
        val r = coord.r
        val s = -q - r
        return maxOf(abs(q), abs(r), abs(s))
    }

    private fun playerHasPort(player: PlayerState, rs: RoomState): Boolean {
        if (rs.portVertices.isEmpty()) return false
        return (player.settlements + player.cities).any { it in rs.portVertices }
    }

    private fun mutate(block: (RoomState) -> RoomState) {
        _state.value = block(_state.value)
    }

    private val buildCosts: Map<BuildType, Map<Resource, Int>> = mapOf(
        BuildType.ROAD to mapOf(
            Resource.CONCRETE to 1,
            Resource.BUCKY to 1
        ),
        BuildType.SETTLEMENT to mapOf(
            Resource.CONCRETE to 1,
            Resource.STUDENT to 1,
            Resource.CHAIR to 1,
            Resource.CHEESE_CURD to 1
        ),
        BuildType.CITY to mapOf(                           // NEW
            Resource.STUDENT to 2,
            Resource.CHAIR to 3
        )
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
            (player.settlements + player.cities).any { it in neighbors }
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

                val newState = rs.copy(
                    players = players,
                    setupPlacedSettlement = true,
                    setupCurrentSettlementVertex = v
                )
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
            val touchingDesc =
                if (touchingTiles.isEmpty()) "no tiles"
                else touchingTiles.joinToString { "${it.number}-${it.resource.name}" }

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
        val occupied = rs.players.values.flatMapTo(mutableSetOf()) { it.settlements + it.cities }
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
            val capitolTokens = rs.capitolRerollTokens.toMutableMap()

            val capitolTiles = boardGraph.tiles.filter {
                it.landmark == Landmark.CAPITOL && it.number == roll
            }

            if (capitolTiles.isNotEmpty()) {
                // For each player, check if they have a settlement touching any Capitol tile
                rs.players.forEach { (pid, pState) ->
                    val controlsCapitol = pState.settlements.any { vKey ->
                        val vertex = boardGraph.vertices[vKey] ?: return@any false
                        vertex.tileCoords.any { coord ->
                            capitolTiles.any { it.coord == coord }
                        }
                    }

                    if (controlsCapitol) {
                        val current = capitolTokens[pid] ?: 0
                        val maxTokens = 1   // Lvl 1 now; later we bump to 2 if city
                        if (current < maxTokens) {
                            capitolTokens[pid] = current + 1
                        }
                    }
                }
            }
            val gainsByPlayerName = mutableMapOf<String, MutableList<Resource>>()
            val robbedCoord = rs.robberCoord

            // Copies of landmark state we’ll update
            val bascomTokens = rs.bascomTradeTokens.toMutableMap()
            val engineeringBoost = rs.engineeringBoostThisTurn.toMutableSet()

            fun addGain(playerName: String, res: Resource, bag: MutableMap<Resource, Int>) {
                bag[res] = (bag[res] ?: 0) + 1
                gainsByPlayerName
                    .getOrPut(playerName) { mutableListOf() }
                    .add(res)
            }

            for ((id, player) in players) {
                var gained = 0
                val newRes = player.resources.toMutableMap()

                for (v in player.settlements) {
                    val vertex = boardGraph.vertices[v] ?: continue

                    for (coord in vertex.tileCoords) {
                        val tile = boardGraph.tilesByCoord[coord] ?: continue

                        // Robber blocks entire tile
                        if (robbedCoord != null && tile.coord == robbedCoord) continue

                        // Only numbered, non-lake tiles produce
                        if (tile.number == roll && tile.resource != Resource.LAKE) {
                            // Base resource
                            addGain(player.name, tile.resource, newRes)
                            gained++

                            // Landmark behavior (Level 1 only for now)
                            when (tile.landmark) {
                                Landmark.BASCOM_HILL -> {
                                    // L1: gain super-trade token (one 2:1 Flamingo)
                                    bascomTokens[id] = (bascomTokens[id] ?: 0) + 1
                                }

                                Landmark.CAPITOL -> {
                                    // L1: gain one reroll token (max 1 for now; L2 will allow 2)
                                    val current = capitolTokens[id] ?: 0
                                    val maxForLevel = 1 // TODO: if city on Capitol, make this 2
                                    if (current < maxForLevel) {
                                        capitolTokens[id] = current + 1
                                    }
                                }

                                Landmark.MEMORIAL_UNION -> {
                                    // L1: swap one of your resources with a random resource (1:1)
                                    val flat = mutableListOf<Resource>()
                                    newRes.forEach { (res, count) ->
                                        repeat(count) { flat.add(res) }
                                    }
                                    if (flat.isNotEmpty()) {
                                        val giveRes = flat.random()
                                        val currentCount = newRes[giveRes] ?: 0
                                        if (currentCount > 0) {
                                            newRes[giveRes] = currentCount - 1
                                            if (newRes[giveRes] == 0) newRes.remove(giveRes)

                                            val getRes = tradableResources.random()
                                            addGain(player.name, getRes, newRes)
                                        }
                                    }
                                }

                                Landmark.ENGINEERING_HALL -> {
                                    // L1: dev cards played this turn should give +1 random resource.
                                    // We just remember the flag for this turn.
                                    engineeringBoost.add(id)
                                }

                                null -> Unit
                            }
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
                    "Rolled $roll → " +
                            gainsByPlayerName.entries.joinToString(" | ") { (name, list) ->
                                val summary = list.groupingBy { it }.eachCount()
                                    .entries.joinToString { (res, count) ->
                                        val pretty = res.name.lowercase()
                                            .replaceFirstChar { it.uppercaseChar() }
                                        "$count $pretty"
                                    }
                                "$name: $summary"
                            }
                }

            eventText.value = msg

            rs.copy(
                players = players,
                lastRoll = roll,
                hasRolledThisTurn = true,
                bascomTradeTokens = bascomTokens,
                capitolRerollTokens = capitolTokens,
                engineeringBoostThisTurn = engineeringBoost
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
     * Called whenever a 7 is rolled (normal or after reroll).
     * Sets the game into ROBBER phase and computes discard requirements.
     */
    private fun startRobberPhase(playerId: String) {
        mutate { rs ->
            // Any player with >7 resources must discard half (rounded UP)
            val discardsNeeded = mutableMapOf<String, Int>()
            for ((pid, pState) in rs.players) {
                val total = pState.resources.values.sum()
                if (total > 7) {
                    val toDiscard = (total + 1) / 2  // rounded up
                    if (toDiscard > 0) {
                        discardsNeeded[pid] = toDiscard
                    }
                }
            }

            val moverName = rs.players[playerId]?.name ?: playerId

            eventText.value =
                if (discardsNeeded.isNotEmpty()) {
                    "Badger Patrol! Players with >7 resources must discard before $moverName moves it."
                } else {
                    "$moverName rolled 7. Tap a tile to move the Badger Patrol."
                }

            rs.copy(
                phase = GamePhase.ROBBER,
                lastRoll = 7,
                robberMoverId = playerId,
                hasRolledThisTurn = true,
                robberDiscardsNeeded = discardsNeeded
            )
        }
    }

    /**
     * Apply a robber discard for [playerId].
     * This is used both locally and when we receive it over P2P.
     */
    private fun applyRobberDiscard(
        rs: RoomState,
        playerId: String,
        discard: Map<Resource, Int>
    ): RoomState {
        val need = rs.robberDiscardsNeeded[playerId] ?: return rs
        val total = discard.values.sum()
        if (total <= 0) return rs

        // Strict: must discard exactly the required amount
        if (total != need) {
            Log.w("GameVM", "Robber discard mismatch: expected $need, got $total for $playerId")
            return rs
        }

        val player = rs.players[playerId] ?: return rs

        // Validate the player actually has enough of each resource
        for ((res, count) in discard) {
            val have = player.resources[res] ?: 0
            if (count < 0 || count > have) {
                Log.w("GameVM", "Invalid discard: $playerId tries to discard $count of $res, has $have")
                return rs
            }
        }

        val newRes = player.resources.toMutableMap()
        for ((res, count) in discard) {
            if (count == 0) continue
            val have = newRes[res] ?: 0
            val newCount = have - count
            if (newCount > 0) newRes[res] = newCount else newRes.remove(res)
        }

        val updatedPlayer = player.copy(resources = newRes)
        val newPlayers = rs.players.toMutableMap()
        newPlayers[playerId] = updatedPlayer

        val newDiscardMap = rs.robberDiscardsNeeded.toMutableMap()
        newDiscardMap.remove(playerId)

        eventText.value =
            "${updatedPlayer.name} discarded $total resources due to the Badger Patrol."

        return rs.copy(
            players = newPlayers,
            robberDiscardsNeeded = newDiscardMap
        )
    }

    private fun placeRobberInternal(playerId: String, coord: HexCoord) {
        mutate { rs ->
            if (rs.phase != GamePhase.ROBBER || rs.turn != playerId || rs.robberMoverId != playerId) return@mutate rs
            if (rs.robberCoord != null && rs.robberCoord == coord) return@mutate rs
            eventText.value = "${rs.players[playerId]?.name ?: playerId} moved the Badger Patrol."
            rs.copy(
                robberCoord = coord,
                phase = GamePhase.PLAY,
                robberMoverId = null,
                hasRolledThisTurn = true
            )
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

            val baseRate = bankRateFor(me, give)   // 3 or 4 from ports
            val bascomTokens = rs.bascomTradeTokens.toMutableMap()
            val tokenCount = bascomTokens[playerId] ?: 0

            // If you have a Bascom token, this Flamingo can be 2:1
            val effectiveRate = if (tokenCount > 0) 2 else baseRate

            val have = me.resources[give] ?: 0
            if (have < effectiveRate) return@mutate rs

            val newRes = me.resources.toMutableMap()
            newRes[give] = have - effectiveRate
            newRes[get] = (newRes[get] ?: 0) + 1

            // If we used a Bascom token (2:1), consume one
            if (tokenCount > 0 && effectiveRate == 2) {
                val remaining = tokenCount - 1
                if (remaining > 0) bascomTokens[playerId] = remaining
                else bascomTokens.remove(playerId)
            }

            players[playerId] = me.copy(resources = newRes)

            eventText.value =
                if (effectiveRate == 2)
                    "Bascom Hill super-trade! Flamingo Run 2:1 ${give.name.lowercase()}."
                else
                    "Flamingo Run trade complete (${effectiveRate}:1 ${give.name.lowercase()})."

            rs.copy(
                players = players,
                bascomTradeTokens = bascomTokens
            )
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

            "ROBBER_DISCARD" -> {
                if (parts.size != 3) return
                val playerId = parts[1]
                val discard = decodeResourceMap(parts[2])
                mutate { rs -> applyRobberDiscard(rs, playerId, discard) }
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

            "TRADE_REJECT" -> mutate { rs ->
                eventText.value = "Trade rejected."
                rs.copy(pendingTrade = null)
            }

            "TRADE_CANCEL" -> mutate { rs ->
                eventText.value = "Trade cancelled."
                rs.copy(pendingTrade = null)
            }

            "TRADE_FLAMINGO" -> {
                if (parts.size != 4) return
                val playerId = parts[1]
                val giveIdx = parts[2].toIntOrNull() ?: return
                val getIdx = parts[3].toIntOrNull() ?: return
                applyFlamingoTradeInternal(
                    playerId,
                    resourceOrder.getOrNull(giveIdx) ?: return,
                    resourceOrder.getOrNull(getIdx) ?: return
                )
            }

            "CAPITOL_SPEND" -> {
                if (parts.size != 2) return
                val pid = parts[1]
                mutate { rs ->
                    val map = rs.capitolRerollTokens.toMutableMap()
                    val cur = (map[pid] ?: 0) - 1
                    if (cur > 0) map[pid] = cur else map.remove(pid)
                    rs.copy(capitolRerollTokens = map)
                }
            }

            // DEV: force a win for a given player
            "DEV_FORCEWIN" -> {
                if (parts.size != 2) return
                val playerId = parts[1]
                devForceWinInternal(playerId)
            }

            "PROFILE" -> {
                // GAME:PROFILE:playerId:nickname:colorLong
                if (parts.size != 4) return
                val playerId = parts[1]
                val nickname = parts[2]
                val colorLong = parts[3].toLongOrNull()

                mutate { rs ->
                    val players = rs.players.toMutableMap()
                    val existing = players[playerId] ?: return@mutate rs

                    players[playerId] = existing.copy(
                        name = if (nickname.isNotBlank()) nickname else existing.name,
                        color = colorLong ?: existing.color
                    )
                    rs.copy(players = players)
                }
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
        if (current.turn != playerId ||
            current.phase != GamePhase.PLAY ||
            current.hasRolledThisTurn ||
            current.pendingTrade != null
        ) return

        // How many Capitol reroll tokens this player currently has
        val tokenCount = current.capitolRerollTokens[playerId] ?: 0

        if (tokenCount > 0) {
            // They have at least one token → roll once and let them choose keep/reroll
            val firstRoll = (1..6).random() + (1..6).random()
            rerollOffer.value = RerollOffer(playerId, firstRoll)
        } else {
            // Normal behavior: just roll and immediately apply
            val roll = (1..6).random() + (1..6).random()
            if (roll == 7) {
                startRobberPhase(playerId)
            } else {
                applyRollResult(roll)
            }
            p2p.send("GAME:ROLL:$playerId:$roll")
        }
    }

    fun resolveReroll(keep: Boolean) {
        val offer = rerollOffer.value ?: return
        val playerId = offer.playerId
        val first = offer.firstRoll

        // Clear dialog state
        rerollOffer.value = null

        if (keep) {
            // Keep first roll; do NOT spend a token
            if (first == 7) {
                startRobberPhase(playerId)
            } else {
                applyRollResult(first)
            }
            p2p.send("GAME:ROLL:$playerId:$first")
        } else {
            // Spend one Capitol token before rerolling
            mutate { rs ->
                val map = rs.capitolRerollTokens.toMutableMap()
                val cur = (map[playerId] ?: 0) - 1
                if (cur > 0) map[playerId] = cur else map.remove(playerId)
                rs.copy(capitolRerollTokens = map)
            }
            p2p.send("GAME:CAPITOL_SPEND:$playerId")

            // New roll is final
            val second = (1..6).random() + (1..6).random()
            if (second == 7) {
                startRobberPhase(playerId)
            } else {
                applyRollResult(second)
            }
            p2p.send("GAME:ROLL:$playerId:$second")
        }
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

    /**
     * Local player confirms which resources to discard for the robber.
     * We apply it and broadcast to the peer.
     */
    fun onLocalRobberDiscard(playerId: String, discard: Map<Resource, Int>) {
        mutate { rs -> applyRobberDiscard(rs, playerId, discard) }
        val encoded = encodeResourceMap(discard)
        p2p.send("GAME:ROBBER_DISCARD:$playerId:$encoded")
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

    fun onLocalFlamingoTrade(playerId: String, give: Resource, get: Resource) {
        applyFlamingoTradeInternal(playerId, give, get)
        val giveIdx = resourceOrder.indexOf(give)
        val getIdx = resourceOrder.indexOf(get)
        p2p.send("GAME:TRADE_FLAMINGO:$playerId:$giveIdx:$getIdx")
    }

    // DEV: quickly force a win for local testing
    fun devForceWinLocal(playerId: String) {
        devForceWinInternal(playerId)
        p2p.send("GAME:DEV_FORCEWIN:$playerId")
    }

    // DEV: internal implementation for forcing a win
    private fun devForceWinInternal(playerId: String) {
        mutate { rs ->
            val players = rs.players.toMutableMap()
            val me = players[playerId] ?: return@mutate rs

            // We want this player to end up with 10 total points.
            // points = settlements + 2 * cities + bonusPoints
            val currentBase = me.settlements.size + me.cities.size * 2
            val targetPoints = 10
            val neededBonus = (targetPoints - currentBase).coerceAtLeast(0)

            val updated = me.copy(bonusPoints = neededBonus)
            players[playerId] = updated

            eventText.value =
                "Dev: ${rs.players[playerId]?.name ?: playerId} boosted to $targetPoints points."

            // Recompute points for everyone and set winnerId accordingly.
            rs.withUpdatedPlayers(players)
        }
    }

    // Still here if you ever want to clear the message manually
    fun consumeEvent() {
        eventText.value = null
    }

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
    private fun generateBoard(seed: Long): List<Tile> {
        val rng = Random(seed)

        // --- All axial coords for radius 3 ---
        val allCoords = mutableListOf<HexCoord>()
        val radius = 3
        for (q in -radius..radius) {
            for (r in -radius..radius) {
                val s = -q - r
                if (maxOf(abs(q), abs(r), abs(s)) <= radius) {
                    allCoords.add(HexCoord(q, r))
                }
            }
        }
        val allCoordSet = allCoords.toSet()

        val innerCoords = allCoords.filter { ringOf(it) <= 2 }  // radius <= 2
        val outerCoords = allCoords.filter { ringOf(it) == 3 }  // rim

        // --- Landmarks: spread around the outer rim (bonus tiles on the edge) ---
        // Use all Landmark enum values so if you add more, they automatically get placed.
        val landmarkTypes = Landmark.values().toList()
        val landmarkByCoord = mutableMapOf<HexCoord, Landmark>()

        if (landmarkTypes.isNotEmpty() && outerCoords.isNotEmpty()) {
            // Axial -> 2D for angle ordering; exact scale doesn’t matter.
            fun axialToCartesian(c: HexCoord): Pair<Double, Double> {
                val x = c.q.toDouble() + 0.5 * c.r.toDouble()
                val y = (sqrt(3.0) / 2.0) * c.r.toDouble()
                return x to y
            }

            val outerOrderedByAngle = outerCoords.sortedBy { coord ->
                val (x, y) = axialToCartesian(coord)
                atan2(y, x)
            }

            // Rotate so that the top-center tile (0, -radius) is first, if it exists.
            val topCoord = HexCoord(0, -radius)
            val startIndex = outerOrderedByAngle.indexOf(topCoord)
            val ringOrdered = if (startIndex >= 0) {
                outerOrderedByAngle.drop(startIndex) + outerOrderedByAngle.take(startIndex)
            } else {
                outerOrderedByAngle
            }

            val count = minOf(landmarkTypes.size, ringOrdered.size)
            val step = ringOrdered.size.toDouble() / count.toDouble()
            val usedIndices = mutableSetOf<Int>()
            val shuffledLandmarks = landmarkTypes.shuffled(rng)

            var pos = 0.0
            for (i in 0 until count) {
                var idx = pos.toInt().coerceIn(0, ringOrdered.lastIndex)
                // Ensure we don't reuse the same coord.
                while (idx in usedIndices) {
                    idx = (idx + 1) % ringOrdered.size
                }
                usedIndices += idx
                val coord = ringOrdered[idx]
                val lm = shuffledLandmarks[i]
                landmarkByCoord[coord] = lm
                pos += step
            }
        }

        // --- Lakes: big (4) + small (3) inside inner region ---
        val innerAvailable = innerCoords.toMutableSet()

        fun growLake(startFrom: HexCoord, targetSize: Int, available: MutableSet<HexCoord>): Set<HexCoord> {
            val cluster = mutableSetOf<HexCoord>()
            val frontier = ArrayDeque<HexCoord>()
            frontier.add(startFrom)

            while (cluster.size < targetSize && frontier.isNotEmpty()) {
                val current = frontier.removeFirst()
                if (!available.contains(current)) continue

                cluster.add(current)
                available.remove(current)

                // neighbors that are still in innerAvailable
                for (dir in HEX_DIRECTIONS) {
                    val n = HexCoord(current.q + dir.q, current.r + dir.r)
                    if (available.contains(n)) {
                        frontier.addLast(n)
                    }
                }
            }
            return cluster
        }

        fun pickStart(avoid: Set<HexCoord>): HexCoord {
            val candidates = innerAvailable.filter { it !in avoid }
            val pool = if (candidates.isNotEmpty()) candidates else innerAvailable.toList()
            return pool.random(rng)
        }

        // Big lake (4 tiles)
        val bigLakeStart = pickStart(emptySet())
        val bigLake = growLake(bigLakeStart, 4, innerAvailable)

        // Keep the small lake a bit away from the big one
        val excludedForSmall = buildSet {
            addAll(bigLake)
            for (c in bigLake) {
                for (d in HEX_DIRECTIONS) {
                    add(HexCoord(c.q + d.q, c.r + d.r))
                }
            }
        }

        val smallLakeStart = pickStart(excludedForSmall)
        val smallLake = growLake(smallLakeStart, 3, innerAvailable)

        val lakeCoords = (bigLake + smallLake).toSet()

        // --- Land coords (non-lake) ---
        val landCoords = allCoords.filter { it !in lakeCoords }
        // For radius 3: 37 total - 7 water = 30 land

        // --- Resource bag for land tiles (5 resources × 6 = 30) ---
        val resourceBag = mutableListOf<Resource>().apply {
            repeat(6) { add(Resource.CONCRETE) }
            repeat(6) { add(Resource.STUDENT) }
            repeat(6) { add(Resource.BUCKY) }
            repeat(6) { add(Resource.CHAIR) }
            repeat(6) { add(Resource.CHEESE_CURD) }
        }.shuffled(rng)

        // --- Neighbors for adjacency constraints ---
        val neighborsByCoord = allCoords.associateWith { coord ->
            HEX_DIRECTIONS
                .map { d -> HexCoord(coord.q + d.q, coord.r + d.r) }
                .filter { allCoordSet.contains(it) }
        }

        // --- First, assign resources (including lakes), numbers later ---
        val tilesByCoord = mutableMapOf<HexCoord, Tile>()

        // Lakes = water tiles with no number token
        for (coord in lakeCoords) {
            tilesByCoord[coord] = Tile(
                coord = coord,
                resource = Resource.LAKE,
                number = 0,
                landmark = null
            )
        }

        // Land resources + landmarks (bonus tiles)
        landCoords.shuffled(rng).forEachIndexed { index, coord ->
            val res = resourceBag.getOrNull(index) ?: Resource.CONCRETE
            val lm = landmarkByCoord[coord]
            tilesByCoord[coord] = Tile(
                coord = coord,
                resource = res,
                number = -1,
                landmark = lm
            )
        }

        // --- Number bag for 30 land tiles ---
        val numberBag = mutableListOf<Int>().apply {
            add(2); add(12)
            repeat(3) { add(3); add(11) }
            repeat(4) { add(4); add(10) }
            repeat(5) { add(5); add(9) }
            repeat(2) { add(6); add(8) }
        }.shuffled(rng)

        val landCoordsList = landCoords.toList()
        var chosenNumbers: Map<HexCoord, Int>? = null

        attempt@ for (attempt in 0 until 2000) {
            val candidate = mutableMapOf<HexCoord, Int>()
            val shuffledNumbers = numberBag.shuffled(rng)

            for (i in landCoordsList.indices) {
                val coord = landCoordsList[i]
                val n = shuffledNumbers[i]

                // Landmarks cannot be 6 or 8
                val lm = landmarkByCoord[coord]
                if (lm != null && (n == 6 || n == 8)) {
                    continue@attempt
                }

                // Adjacency constraints:
                // - same number can't touch
                // - 6/8 can't touch 6/8
                // - 2 and 12 can't touch each other
                val neighbors = neighborsByCoord[coord].orEmpty()
                for (nb in neighbors) {
                    val prev = candidate[nb] ?: continue
                    if (prev == n) continue@attempt
                    if ((n == 6 || n == 8) && (prev == 6 || prev == 8)) continue@attempt
                    if ((n == 2 && prev == 12) || (n == 12 && prev == 2)) continue@attempt
                }

                candidate[coord] = n
            }

            if (candidate.size == landCoordsList.size) {
                chosenNumbers = candidate
                break
            }
        }

        val finalNumbers = chosenNumbers ?: run {
            // Fallback: just assign numbers in order, only enforcing
            // "no 6/8 on landmark" to avoid hard-breaking landmarks.
            val map = mutableMapOf<HexCoord, Int>()
            val it = numberBag.iterator()
            for (coord in landCoordsList) {
                var n = if (it.hasNext()) it.next() else 5
                val lm = landmarkByCoord[coord]
                if (lm != null && (n == 6 || n == 8)) {
                    n = numberBag.firstOrNull { x -> x != 6 && x != 8 } ?: 5
                }
                map[coord] = n
            }
            map
        }

        // --- Build final tile list in stable order ---
        return allCoords
            .sortedWith(compareBy<HexCoord> { it.q }.thenBy { it.r })
            .map { coord ->
                val base = tilesByCoord[coord]
                    ?: Tile(coord, Resource.CONCRETE, number = 0, landmark = null)

                if (base.resource == Resource.LAKE) {
                    base.copy(number = 0)   // lakes never get a token
                } else {
                    base.copy(number = finalNumbers[coord] ?: base.number)
                }
            }
    }

    private fun computeLakePorts(graph: BoardGraph): Set<VertexKey> {
        // Water tiles = lakes
        val waterCoords = graph.tiles
            .filter { it.resource == Resource.LAKE }
            .map { it.coord }
            .toSet()

        if (waterCoords.isEmpty()) return emptySet()

        data class Candidate(val vertexKey: VertexKey, val landCoord: HexCoord)

        val candidates = mutableListOf<Candidate>()

        // Shore vertices = vertices touching at least one lake + at least one land tile
        for ((vKey, vertex) in graph.vertices) {
            val touching = vertex.tileCoords
            val touchesWater = touching.any { it in waterCoords }
            if (!touchesWater) continue

            val land = touching.firstOrNull { it !in waterCoords } ?: continue
            candidates.add(Candidate(vKey, land))
        }

        if (candidates.isEmpty()) return emptySet()

        fun hexDistance(a: HexCoord, b: HexCoord): Int {
            val aq = a.q; val ar = a.r; val asCoord = -aq - ar
            val bq = b.q; val br = b.r; val bs = -bq - br
            return maxOf(
                abs(aq - bq),
                abs(ar - br),
                abs(asCoord - bs)
            )
        }

        val rng = Random(seed)
        val shuffled = candidates.shuffled(rng)
        val chosen = mutableListOf<Candidate>()

        // "No port within two hexes" -> require distance >= 3 between their land anchor tiles
        for (cand in shuffled) {
            if (chosen.any { hexDistance(it.landCoord, cand.landCoord) < 3 }) {
                continue
            }
            chosen.add(cand)
        }

        // Safety net: in pathological layouts, still guarantee at least one port
        if (chosen.isEmpty()) {
            chosen.add(shuffled.first())
        }

        return chosen.mapTo(mutableSetOf()) { it.vertexKey }
    }

    // --- UNUSED helpers kept for reference (unchanged) ---

    private fun hexCoords(radius: Int): List<HexCoord> {
        val result = mutableListOf<HexCoord>()
        for (q in -radius..radius) {
            for (r in -radius..radius) {
                val s = -q - r
                if (maxOf(
                        kotlin.math.abs(q),
                        kotlin.math.abs(r),
                        kotlin.math.abs(s)
                    ) <= radius
                ) {
                    result.add(HexCoord(q, r))
                }
            }
        }
        return result
    }

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

        repeat(50_000) {
            val shuffled = resourceBag.shuffled(rng)
            val layout = coords.zip(shuffled).toMap()
            if (isResourceLayoutValid(layout, neighbors)) {
                return layout
            }
        }

        Log.w("BoardGen", "Falling back to unconstrained resource layout (random).")
        val fallback = coords.zip(resourceBag.shuffled(rng)).toMap()
        return fallback
    }

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
        )

        for (terrain in terrainTypes) {
            val visited = mutableSetOf<HexCoord>()
            for ((coord, res) in layout) {
                if (res != terrain || coord in visited) continue

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

                if (nA == b) return false

                if ((nA == 6 && b == 8) || (nA == 8 && b == 6)) return false

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
