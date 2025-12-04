package com.cs407.settlersofmadison.game.state

import com.cs407.settlersofmadison.domain.model.VertexKey

/**
 * All possible game actions that can be applied to GameState and
 * synchronized over the network.
 */
sealed interface GameAction {

    // Debug action you already had
    data object IncrementDebugCounter : GameAction

    // Gameplay actions
    data class PlaceSettlement(
        val playerId: String,
        val vertex: VertexKey
    ) : GameAction

    /**
     * Dice roll is passed in so the initiating device chooses the roll
     * and it stays deterministic across the network.
     */
    data class RollDice(
        val roll: Int
    ) : GameAction

    data object EndTurn : GameAction

    data object StartGame : GameAction
}