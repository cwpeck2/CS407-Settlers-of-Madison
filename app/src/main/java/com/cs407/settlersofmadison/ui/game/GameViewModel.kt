package com.cs407.settlersofmadison.ui.game

import androidx.lifecycle.ViewModel
import com.cs407.settlersofmadison.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class GamePhase { SETUP, PLAY }

data class PlayerState(
    val id: String,
    val name: String,
    val resources: MutableMap<Resource, Int> = mutableMapOf(),
    val settlements: MutableSet<VertexKey> = mutableSetOf(),
    val roads: MutableSet<EdgeKey> = mutableSetOf(),
    val points: Int = 0
)

data class RoomState(
    val tiles: List<Tile>,
    val players: MutableMap<String, PlayerState>,
    val turn: String,
    val phase: GamePhase = GamePhase.SETUP,
    val lastRoll: Int? = null
)

class GameViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        RoomState(
            tiles = tinyBoard(),
            players = mutableMapOf(
                "host" to PlayerState("host", "Host"),
                "guest" to PlayerState("guest", "Guest")
            ),
            turn = "host",
            phase = GamePhase.SETUP
        )
    )
    val state: StateFlow<RoomState> = _state

    // Simple UI event text (snackbar/Toast)
    val eventText = MutableStateFlow<String?>(null)

    private fun mutate(block: (RoomState) -> RoomState) { _state.value = block(_state.value) }

    /** Return true if that vertex is adjacent (distance-1) to any settlement. */
    private fun isAdjacentToSettlement(rs: RoomState, v: VertexKey): Boolean {
        // A vertex touches up to 3 tiles; any neighbor vertex sharing an edge is "adjacent"
        // For the simple rule: forbid placing on any of the 6 neighbor vertices around the same hex corners.
        val neighbors = buildSet {
            // Same hex: corners next to v.corner
            add(VertexKey(v.q, v.r, (v.corner + 1) % 6))
            add(VertexKey(v.q, v.r, (v.corner + 5) % 6))
            // Across the edge: the corresponding corner on neighbor hex
            val n = neighbor(HexCoord(v.q, v.r), v.corner)
            add(VertexKey(n.q, n.r, (v.corner + 3) % 6))
        }
        return rs.players.values.any { p -> p.settlements.any { it in neighbors } }
    }

    fun tryPlaceSettlement(playerId: String, v: VertexKey) {
        mutate { rs ->
            if (rs.turn != playerId) return@mutate rs
            // Block duplicates
            if (rs.players.values.any { v in it.settlements }) return@mutate rs
            // Distance rule (no adjacent settlement)
            if (isAdjacentToSettlement(rs, v)) {
                eventText.value = "Too close to another settlement."
                return@mutate rs
            }
            val players = rs.players.toMutableMap()
            val me = players[playerId]!!
            val newSet = me.settlements.toMutableSet().apply { add(v) }
            players[playerId] = me.copy(settlements = newSet, points = newSet.size)
            eventText.value = "${me.name} placed a settlement."
            rs.copy(players = players)
        }
    }

    fun rollDice() {
        mutate { rs ->
            if (rs.phase != GamePhase.PLAY) return@mutate rs
            val roll = (1..6).random() + (1..6).random()
            val awardTiles = rs.tiles.filter { it.number == roll }
            val players = rs.players.toMutableMap()

            val gains = mutableMapOf<String, MutableList<String>>()

            players.values.forEach { p ->
                var gained = 0
                awardTiles.forEach { t ->
                    val corners = (0 until 6).map { canonicalVertex(t.coord, it) }.toSet()
                    if (p.settlements.any { it in corners }) {
                        p.resources[t.resource] = (p.resources[t.resource] ?: 0) + 1
                        gained++
                        gains.getOrPut(p.name) { mutableListOf() }.add(t.resource.name.lowercase().replaceFirstChar { it.uppercaseChar() })
                    }
                }
                if (gained > 0) {
                    // will show in snackbar
                }
            }

            val msg = if (gains.isEmpty()) "Rolled $roll. No resources." else
                "Rolled $roll → " + gains.entries.joinToString(" | ") { (name, list) ->
                    "$name: ${list.groupingBy { it }.eachCount().entries.joinToString { "${it.value} ${it.key}" }}"
                }
            eventText.value = msg
            rs.copy(players = players, lastRoll = roll)
        }
    }

    fun endTurn() {
        mutate { rs ->
            val next = if (rs.turn == "host") "guest" else "host"
            rs.copy(turn = next)
        }
    }
}
