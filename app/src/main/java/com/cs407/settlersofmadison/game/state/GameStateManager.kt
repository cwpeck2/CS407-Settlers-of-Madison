package com.cs407.settlersofmadison.game.state

import com.cs407.settlersofmadison.domain.model.VertexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * Simple text protocol for now.
 * We'll swap for JSON / protobuf later if needed.
 */
object GameActionCodec {

    // outgoing text for the wire
    fun encode(action: GameAction): String = when (action) {
        GameAction.IncrementDebugCounter -> "INC_DEBUG"

        is GameAction.PlaceSettlement ->
            "SETTLE:${action.playerId}:${action.vertex.q},${action.vertex.r},${action.vertex.corner}"

        is GameAction.RollDice ->
            "ROLL:${action.roll}"

        GameAction.EndTurn ->
            "END_TURN"
    }

    // incoming text from the wire
    fun decode(raw: String): GameAction? {
        return when {
            raw == "INC_DEBUG" ->
                GameAction.IncrementDebugCounter

            raw.startsWith("SETTLE:") -> {
                // SETTLE:<playerId>:q,r,corner
                val parts = raw.split(":")
                if (parts.size != 3) return null
                val playerId = parts[1]
                val coordParts = parts[2].split(",")
                if (coordParts.size != 3) return null
                val q = coordParts[0].toIntOrNull() ?: return null
                val r = coordParts[1].toIntOrNull() ?: return null
                val corner = coordParts[2].toIntOrNull() ?: return null
                GameAction.PlaceSettlement(playerId, VertexKey(q, r, corner))
            }

            raw.startsWith("ROLL:") -> {
                val value = raw.removePrefix("ROLL:").toIntOrNull() ?: return null
                GameAction.RollDice(value)
            }

            raw == "END_TURN" ->
                GameAction.EndTurn

            else -> null
        }
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