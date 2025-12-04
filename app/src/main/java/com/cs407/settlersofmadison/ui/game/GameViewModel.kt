package com.cs407.settlersofmadison.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cs407.settlersofmadison.domain.model.VertexKey
import com.cs407.settlersofmadison.game.state.GameAction
import com.cs407.settlersofmadison.game.state.GameState
import com.cs407.settlersofmadison.game.state.GameStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin wrapper around GameStateManager that exposes the game state to the UI
 * and provides high-level methods for user actions.
 *
 * @param gameManager shared GameStateManager instance (already wired to P2P)
 * @param playerId logical player id for this device ("host" or "guest")
 */
class GameViewModel(
    private val gameManager: GameStateManager,
    private val playerId: String
) : ViewModel() {

    /** Observed by GameScreen to render the current game. */
    val state: StateFlow<GameState> = gameManager.state

    /**
     * One-shot UI messages (e.g., dice result text or error messages).
     * GameScreen can show these in a Snackbar/Toast.
     */
    private val _eventText = MutableStateFlow<String?>(null)
    val eventText: StateFlow<String?> = _eventText

    fun consumeEvent() {
        _eventText.value = null
    }

    /*  Actions                                                              */

    /**
     * Increment the shared debug counter (for network sync testing).
     */
    fun incrementDebugCounter() {
        gameManager.dispatchLocal(GameAction.IncrementDebugCounter)
    }

    /**
     * Try to place a settlement for this device's player at the given vertex.
     * In a real UI you call this when the user taps a vertex on the board.
     */
    fun placeSettlement(vertex: VertexKey) {
        gameManager.dispatchLocal(
            GameAction.PlaceSettlement(
                playerId = playerId,
                vertex = vertex
            )
        )
    }

    /**
     * Roll 2d6 and dispatch a RollDice action with that specific value so
     * all peers stay deterministic.
     */
    fun rollDice() {
        val roll = (1..6).random() + (1..6).random()
        _eventText.value = "You rolled $roll"
        gameManager.dispatchLocal(GameAction.RollDice(roll))
    }

    /**
     * End this player's turn and pass it to the other player.
     */
    fun endTurn() {
        gameManager.dispatchLocal(GameAction.EndTurn)
    }
}

/**
 * ViewModelProvider.Factory so we can create GameViewModel with our
 * custom constructor (GameStateManager + playerId) from Compose.
 */
class GameViewModelFactory(
    private val gameManager: GameStateManager,
    private val playerId: String
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(gameManager, playerId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}