package com.cs407.settlersofmadison.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.network.ConnState
import com.cs407.settlersofmadison.network.P2PService
import com.cs407.settlersofmadison.game.state.*
import com.cs407.settlersofmadison.network.NetConfig
import kotlinx.coroutines.flow.StateFlow

class MenuViewModel : ViewModel() {
    // Network
    private val p2p = P2PService(viewModelScope)

    val state: StateFlow<ConnState> = p2p.state
    val messages: StateFlow<List<String>> = p2p.messages

    // Game state
    private val gameTransport = P2PGameTransport(p2p)
    private val gameManager = GameStateManager(viewModelScope, gameTransport)

    val gameState: StateFlow<GameState> = gameManager.state

    // Network control API used by the UI
    fun host(port: Int) {
        if (NetConfig.USE_RELAY) {
            // DEV MODE:
            // "Host" just connects to the relay server on your PC.
            // We still treat this side as the host logically in the UI.
            p2p.connect(NetConfig.RELAY_IP, NetConfig.RELAY_PORT)
        } else {
            // REAL P2P MODE:
            // Direct device-to-device hosting.
            p2p.host(port)
        }
    }

    fun connect(ip: String, port: Int) {
        if (NetConfig.USE_RELAY) {
            // DEV MODE:
            // Ignore user IP/port; always go to relay.
            p2p.connect(NetConfig.RELAY_IP, NetConfig.RELAY_PORT)
        } else {
            // REAL P2P MODE:
            // Use the values typed on the join screen.
            p2p.connect(ip, port)
        }
    }
    fun sendChat(msg: String) = p2p.send("CHAT:$msg")
    fun close() = p2p.close()

    // ame API: UI calls these instead of making the actions itself

    fun incrementDebugCounter() {
        gameManager.dispatchLocal(GameAction.IncrementDebugCounter)
    }
}