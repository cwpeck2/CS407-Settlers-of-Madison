package com.cs407.settlersofmadison.game.state

data class GameState(
    val debugCounter: Int = 0,
    // TODO: val players: Map<String, PlayerState> = emptyMap(),
    // TODO: val currentTurnPlayerId: String? = null,
    // TODO: val board: BoardState = BoardState()
)

/**
 * All possible game actions.
 * e.g data class PlaceRoad(val playerId: String, val edgeId: String) : GameAction
 */
sealed interface GameAction {
    data object IncrementDebugCounter : GameAction
}

/**
 * A basic reducer - based on the current state and an action taken, returns the new state.
 */
fun applyGameAction(old: GameState, action: GameAction): GameState =
    when (action) {
        GameAction.IncrementDebugCounter ->
            old.copy(debugCounter = old.debugCounter + 1)
    }