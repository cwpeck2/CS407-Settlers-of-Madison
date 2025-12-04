package com.cs407.settlersofmadison.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.ConnState
import com.cs407.settlersofmadison.data.p2p.P2PService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LobbyViewModel : ViewModel() {
    private val p2p = P2PService(viewModelScope)

    val state: StateFlow<ConnState> = p2p.state
    val messages = p2p.messages

    // local ready (host on host device, joiner on join device)
    private val _localReady = MutableStateFlow(false)
    val localReady: StateFlow<Boolean> = _localReady

    // remote ready (other device)
    val peerReady: StateFlow<Boolean> = p2p.peerReady

    fun host(port: Int) {

        _localReady.value = false
        p2p.host(port)
    }

    fun connect(ip: String, port: Int) {
        _localReady.value = false
        p2p.connect(ip, port)
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
