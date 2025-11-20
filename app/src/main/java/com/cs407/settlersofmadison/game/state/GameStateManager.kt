package com.cs407.settlersofmadison.game.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Small interface so game logic does not know about sockets/Bluetooth/etc.
 */
interface GameTransport {
    // text lines from network
    val incoming: Flow<String>

    // send raw text line
    fun send(message: String)
}

/**
 * simple text protocol for now.
 * will swap for JSON or protobuf after functionality implementation
 */
object GameActionCodec {

    // outgoing text for the wire
    fun encode(action: GameAction): String = when (action) {
        GameAction.IncrementDebugCounter -> "INC_DEBUG"
    }

    // incoming text from the wire
    fun decode(raw: String): GameAction? = when (raw) {
        "INC_DEBUG" -> GameAction.IncrementDebugCounter
        else -> null
    }
}

/**
 * Holds game state and syncs actions with peers through a GameTransport.
 */
class GameStateManager(
    private val scope: CoroutineScope,
    private val transport: GameTransport
) {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        // Listen to remote messages
        scope.launch {
            transport.incoming.collect { line ->
                // We reserve "GAME:" prefix for game actions
                if (!line.startsWith("GAME:")) return@collect

                val payload = line.removePrefix("GAME:")
                val action = GameActionCodec.decode(payload) ?: return@collect
                apply(action, fromNetwork = true)
            }
        }
    }

    /**
     * Called by UI for local user actions.
     */
    fun dispatchLocal(action: GameAction) {
        // Update local state immediately
        apply(action, fromNetwork = false)

        // Send to peers
        val encoded = GameActionCodec.encode(action)
        transport.send("GAME:$encoded")
    }

    private fun apply(action: GameAction, fromNetwork: Boolean) {
        _state.value = applyGameAction(_state.value, action)
    }
}