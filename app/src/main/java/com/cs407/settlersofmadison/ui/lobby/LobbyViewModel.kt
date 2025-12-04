package com.cs407.settlersofmadison.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.network.ConnState
import com.cs407.settlersofmadison.network.P2PService
import com.cs407.settlersofmadison.game.state.GameStateManager
import com.cs407.settlersofmadison.game.state.P2PGameTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.cs407.settlersofmadison.network.NetConfig

class LobbyViewModel : ViewModel() {
    private val p2p = P2PService(viewModelScope)

    val state: StateFlow<ConnState> = p2p.state
    val messages = p2p.messages

    // local ready (host on host device, joiner on join device)
    private val _localReady = MutableStateFlow(false)
    val localReady: StateFlow<Boolean> = _localReady

    // remote ready (other device)
    val peerReady: StateFlow<Boolean> = p2p.peerReady

    val gameManager: GameStateManager =
        GameStateManager(viewModelScope, P2PGameTransport(p2p))

    fun host(port: Int) {
        _localReady.value = false

        if (NetConfig.USE_RELAY) {
            // Both “host” and “join” connect to the relay on your PC
            p2p.connect(NetConfig.RELAY_IP, NetConfig.RELAY_PORT)
        } else {
            // Real device-to-device hosting
            p2p.host(port)
        }
    }

    fun connect(ip: String, port: Int) {
        _localReady.value = false

        if (NetConfig.USE_RELAY) {
            // Ignore user IP/port; always use relay
            p2p.connect(NetConfig.RELAY_IP, NetConfig.RELAY_PORT)
        } else {
            // Direct connect to the host’s IP/port
            p2p.connect(ip, port)
        }
    }

    fun toggleReady() {
        val newReady = !_localReady.value
        _localReady.value = newReady
        p2p.setReady(newReady)
    }

    fun send(msg: String) = p2p.send(msg)

    fun leave() {
        _localReady.value = false
        p2p.leave()
    }

    fun close() = leave()
}