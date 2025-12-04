package com.cs407.settlersofmadison.game.state

import com.cs407.settlersofmadison.domain.engine.tinyBoard
import com.cs407.settlersofmadison.domain.model.EdgeKey
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey

/**
 * Phases of the game.
 */
enum class GamePhase { SETUP, PLAY }

/**
 * Per-player state tracked in the game.
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
 * The full immutable game state snapshot.
 */
data class GameState(
    val debugCounter: Int = 0,

    val tiles: List<Tile> = tinyBoard(),
    val players: Map<String, PlayerState> = defaultPlayers(),
    val turn: String = "host",                      // "host" or "guest"
    val phase: GamePhase = GamePhase.SETUP,
    val lastRoll: Int? = null
)

private fun defaultPlayers(): Map<String, PlayerState> =
    mapOf(
        "host" to PlayerState(id = "host", name = "Host"),
        "guest" to PlayerState(id = "guest", name = "Guest")
    )