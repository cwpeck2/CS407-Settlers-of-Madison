package com.cs407.settlersofmadison.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cs407.settlersofmadison.domain.model.VertexKey
import com.cs407.settlersofmadison.game.state.GameAction
import com.cs407.settlersofmadison.game.state.GameState
import com.cs407.settlersofmadison.game.state.GameStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class GameViewModel(
    private val gameManager: GameStateManager,
    private val localPlayerId: String
) : ViewModel() {

    /** Expose the synced game state to the UI. */
    val state: StateFlow<GameState> = gameManager.state

    val playerId: String get() = localPlayerId

    // One-shot UI messages (snackbar).
    private val _eventText = MutableStateFlow<String?>(null)
    val eventText: StateFlow<String?> = _eventText

    fun consumeEvent() {
        _eventText.value = null
    }

    /*  Actions                                                              */

    fun placeSettlement(vertex: VertexKey) {
        gameManager.dispatchLocal(
            GameAction.PlaceSettlement(
                playerId = localPlayerId,
                vertex = vertex
            )
        )
    }

    fun rollDice() {
        val roll = (1..6).random() + (1..6).random()
        _eventText.value = "You rolled $roll"
        gameManager.dispatchLocal(GameAction.RollDice(roll))
    }

    fun endTurn() {
        gameManager.dispatchLocal(GameAction.EndTurn)
    }

    fun debugDumpBoard() {
        val s = state.value
        val summary = s.players.values.joinToString { p ->
            "${p.name}: ${p.points} pts, settlements=${p.settlements.size}"
        }
        android.util.Log.d("GAMEDEBUG", "Board summary: $summary")
    }
}

/**
 * Factory so we can create GameViewModel with dependencies from Compose.
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