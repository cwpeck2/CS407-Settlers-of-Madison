package com.cs407.settlersofmadison.game.state

import com.cs407.settlersofmadison.domain.engine.canonicalVertex
import com.cs407.settlersofmadison.domain.engine.neighbor
import com.cs407.settlersofmadison.domain.model.HexCoord
import com.cs407.settlersofmadison.domain.model.Resource
import com.cs407.settlersofmadison.domain.model.Tile
import com.cs407.settlersofmadison.domain.model.VertexKey

/**
 * Applies a single GameAction to the current GameState and returns the new state.
 */
fun applyGameAction(old: GameState, action: GameAction): GameState =
    when (action) {
        is GameAction.IncrementDebugCounter ->
            old.copy(debugCounter = old.debugCounter + 1)

        is GameAction.PlaceSettlement ->
            applyPlaceSettlement(old, action)

        is GameAction.RollDice ->
            applyRollDice(old, action)

        is GameAction.EndTurn ->
            applyEndTurn(old)

        GameAction.StartGame ->
            applyStartGame(old)
    }

/*  Helpers for specific actions                                             */

private fun applyPlaceSettlement(
    old: GameState,
    action: GameAction.PlaceSettlement
): GameState {
    val playerId = action.playerId
    val v = action.vertex

    // Must be this player's turn
    if (old.turn != playerId) return old

    // Must not already be occupied by any settlement
    if (old.players.values.any { v in it.settlements }) return old

    // Must respect "no adjacent settlements" rule
    if (isAdjacentToSettlement(old, v)) return old

    // Copy players map immutably
    val players = old.players.toMutableMap()
    val me = players[playerId] ?: return old

    val newSettlements = me.settlements + v
    val newPoints = newSettlements.size // simple scoring for now

    players[playerId] = me.copy(
        settlements = newSettlements,
        points = newPoints
    )

    return old.copy(players = players.toMap())
}

/**
 * Return true if v is adjacent (distance-1) to any existing settlement.
 *
 * Same logic as your teammate's ViewModel:
 *  - Same hex: corners next to v.corner
 *  - Across the edge: the corresponding corner on the neighbor hex
 */
private fun isAdjacentToSettlement(state: GameState, v: VertexKey): Boolean {
    val neighbors: Set<VertexKey> = buildSet {
        // Same hex: adjacent corners
        add(VertexKey(v.q, v.r, (v.corner + 1) % 6))
        add(VertexKey(v.q, v.r, (v.corner + 5) % 6))

        // Across the edge: opposite corner on neighboring hex
        val n = neighbor(HexCoord(v.q, v.r), v.corner)
        add(VertexKey(n.q, n.r, (v.corner + 3) % 6))
    }

    return state.players.values.any { p ->
        p.settlements.any { it in neighbors }
    }
}

private fun applyRollDice(
    old: GameState,
    action: GameAction.RollDice
): GameState {
    // Only award resources in PLAY phase
    if (old.phase != GamePhase.PLAY) return old

    val roll = action.roll

    // Tiles that produce on this roll
    val awardTiles: List<Tile> = old.tiles.filter { it.number == roll }

    // Copy players map so we can mutate, then freeze
    val players = old.players.toMutableMap()

    players.values.forEach { p ->
        var gained = 0
        val newResources = p.resources.toMutableMap()

        awardTiles.forEach { t ->
            // All corners (canonicalized) for this tile
            val corners = (0 until 6)
                .map { corner -> canonicalVertex(t.coord, corner) }
                .toSet()

            if (p.settlements.any { it in corners }) {
                val current = newResources[t.resource] ?: 0
                newResources[t.resource] = current + 1
                gained++
            }
        }

        if (gained > 0) {
            players[p.id] = p.copy(resources = newResources.toMap())
        }
    }

    return old.copy(
        players = players.toMap(),
        lastRoll = roll
    )
}

private fun applyEndTurn(old: GameState): GameState {
    val next = if (old.turn == "host") "guest" else "host"
    return old.copy(turn = next)
}

private fun applyStartGame(old: GameState): GameState {
    // For now we just switch to PLAY and clear lastRoll
    if (old.phase == GamePhase.PLAY) return old
    return old.copy(phase = GamePhase.PLAY, lastRoll = null)
}