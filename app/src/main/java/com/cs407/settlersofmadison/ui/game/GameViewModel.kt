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

// Overall phase – now includes ROBBER when a 7 is rolled.
enum class GamePhase { SETUP, PLAY, ROBBER }

/**
 * Per–player state.
 *
 * id     – "host" or "guest"
 * name   – display name
 * resources – card counts by Resource type
 * settlements – canonical vertex keys where they’ve placed houses
 * roads      – canonical edge keys where they’ve placed roads (future)
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
    val phase: GamePhase = GamePhase.PLAY,
    val lastRoll: Int? = null,
    // Robber lives on a single tile; null = not placed yet.
    val robberCoord: HexCoord? = null,
    // While in ROBBER phase, only this player can place/move robber.
    val robberMoverId: String? = null
)

class GameViewModel : ViewModel() {

    private val p2p = P2PHolder.service

    // --- Board definition ----------------------------------------------------

    // Standard Catan-like radius-2 board with randomized resources/numbers.
    private val boardTiles: List<Tile> = standardCatanBoardRandom()

    // Single immutable graph describing tiles + shared vertices/edges.
    private val boardGraph: BoardGraph = buildBoardGraph(boardTiles)

    // Reactive game state used by the UI.
    private val _state = MutableStateFlow(
        RoomState(
            tiles = boardGraph.tiles,
            players = mapOf(
                "host" to PlayerState("host", "Host"),
                "guest" to PlayerState("guest", "Guest")
            ),
            turn = "host",
            phase = GamePhase.PLAY
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
     * Vertices adjacent to v (distance 1 in the vertex graph).
     * Computed via edges in BoardGraph: any edge that contains v connects to
     * the other endpoint.
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

    /** True if this vertex is distance-1 from any settlement (any player). */
    private fun isAdjacentToSettlement(rs: RoomState, v: VertexKey): Boolean {
        val neighbors = adjacentVerticesOf(v)
        return rs.players.values.any { player ->
            player.settlements.any { it in neighbors }
        }
    }

    // -------------------------------------------------------------------------
    // Core actions (local logic – no network)
    // -------------------------------------------------------------------------

    /**
     * Try to place a settlement for playerId at vertex v.
     * Enforces:
     *  - vertex must exist on the board
     *  - must be that player's turn
     *  - vertex not already occupied
     *  - Catan distance rule: no neighboring settlements
     */
    private fun tryPlaceSettlement(playerId: String, v: VertexKey) {
        mutate { rs ->
            // Only during PLAY phase.
            if (rs.phase != GamePhase.PLAY) return@mutate rs

            // Vertex must be part of the current board.
            if (!boardGraph.vertices.containsKey(v)) return@mutate rs

            // Only current player can act.
            if (rs.turn != playerId) return@mutate rs

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

            val newSet = me.settlements.toMutableSet().apply { add(v) }
            players[playerId] = me.copy(
                settlements = newSet,
                points = newSet.size // 1 point per settlement for now
            )

            // For UI: what tiles does this vertex really touch?
            val vertex = boardGraph.vertices[v]
            val touchingTiles = vertex?.tileCoords
                ?.mapNotNull { coord -> boardGraph.tilesByCoord[coord] }
                ?.sortedWith(compareBy<Tile> { it.coord.q }.thenBy { it.coord.r })
                ?: emptyList()

            val touchingDesc =
                if (touchingTiles.isEmpty()) {
                    "no tiles"
                } else {
                    touchingTiles.joinToString { "${it.number}-${it.resource.name}" }
                }

            eventText.value =
                "${me.name} placed a settlement touching: $touchingDesc"

            val newState = rs.copy(players = players)

            // Optional: debug dump after each placement
            debugDumpEverything()

            newState
        }
    }

    /**
     * Apply a dice roll to all players, granting resources for each settlement
     * that touches a tile with this number, EXCEPT the tile that currently has
     * the robber (if any).
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
                lastRoll = roll
            )
        }
    }

    private fun internalEndTurn() {
        mutate { rs ->
            // Only end turn from PLAY phase
            if (rs.phase != GamePhase.PLAY) return@mutate rs
            val next = if (rs.turn == "host") "guest" else "host"
            rs.copy(turn = next)
        }
    }

    /**
     * Enter robber phase after rolling a 7.
     */
    private fun startRobberPhase(playerId: String) {
        mutate { rs ->
            eventText.value =
                "${rs.players[playerId]?.name ?: playerId} rolled 7. Tap a tile to move the robber."
            rs.copy(
                phase = GamePhase.ROBBER,
                lastRoll = 7,
                robberMoverId = playerId
            )
        }
    }

    /**
     * Place/move robber on a given tile coord; only allowed once by the player
     * who rolled the 7, during ROBBER phase.
     */
    private fun placeRobberInternal(playerId: String, coord: HexCoord) {
        mutate { rs ->
            if (rs.phase != GamePhase.ROBBER ||
                rs.turn != playerId ||
                rs.robberMoverId != playerId
            ) {
                return@mutate rs
            }

            eventText.value =
                "${rs.players[playerId]?.name ?: playerId} moved the robber."

            rs.copy(
                robberCoord = coord,
                phase = GamePhase.PLAY,
                robberMoverId = null
            )
        }
    }

    // -------------------------------------------------------------------------
    // Network message handling (remote actions)
    // -------------------------------------------------------------------------

    /**
     * Handles payload of the form:
     *  - SETTLEMENT:<playerId>:<q>:<r>:<corner>
     *  - ROLL:<playerId>:<roll>
     *  - ENDTURN:<playerId>
     *  - ROBBER:<playerId>:<q>:<r>
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
        }
    }

    // -------------------------------------------------------------------------
    // Public API for the UI (local + send over network)
    // -------------------------------------------------------------------------

    fun onLocalVertexTap(playerId: String, v: VertexKey) {
        // Apply locally first…
        tryPlaceSettlement(playerId, v)
        // …then notify peer.
        p2p.send("GAME:SETTLEMENT:$playerId:${v.q}:${v.r}:${v.corner}")
    }

    fun onLocalRollDice(playerId: String) {
        val current = _state.value
        if (current.turn != playerId || current.phase != GamePhase.PLAY) {
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
        if (current.turn != playerId || current.phase != GamePhase.PLAY) return

        internalEndTurn()
        p2p.send("GAME:ENDTURN:$playerId")
    }

    fun onLocalPlaceRobber(playerId: String, coord: HexCoord) {
        placeRobberInternal(playerId, coord)
        p2p.send("GAME:ROBBER:$playerId:${coord.q}:${coord.r}")
    }

    fun consumeEvent() {
        eventText.value = null
    }

    // -------------------------------------------------------------------------
    // Debug dump – called from a debug button or inside actions
    // -------------------------------------------------------------------------

    companion object {
        private const val HEX_TAG = "HEXDEBUG"
    }

    /**
     * Dumps:
     *  - each VertexKey → which tiles (coord/number/resource)
     *  - each player's settlements → which tiles they touch
     */
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
    // Board generation: radius-2 Catan-style, randomized.
    // -------------------------------------------------------------------------

    /**
     * Generate a radius-2 hex board (19 tiles) with randomized resources and
     * numbers. This is not a perfect match to official Catan distribution,
     * but close enough for gameplay.
     */
    private fun standardCatanBoardRandom(): List<Tile> {
        // Axial coords for radius-2 hex: |q|<=2, |r|<=2, |q+r|<=2
        val coords = mutableListOf<HexCoord>()
        for (q in -2..2) {
            for (r in -2..2) {
                if (abs(q + r) <= 2) {
                    coords.add(HexCoord(q, r))
                }
            }
        }

        // 19 resource tiles – rough distribution.
        val resourceBag = mutableListOf<Resource>().apply {
            repeat(4) { add(Resource.WOOD) }
            repeat(3) { add(Resource.BRICK) }
            repeat(4) { add(Resource.SHEEP) }
            repeat(4) { add(Resource.WHEAT) }
            repeat(4) { add(Resource.ORE) }
        }

        // 19 number tokens (no 7). This is "good enough" for now.
        val numberBag = mutableListOf<Int>().apply {
            addAll(
                listOf(
                    2,
                    3, 3,
                    4, 4,
                    5, 5, 5,     // one extra 5 to reach 19 tokens
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
