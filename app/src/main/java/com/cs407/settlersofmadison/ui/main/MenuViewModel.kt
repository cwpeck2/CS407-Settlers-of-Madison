package com.cs407.settlersofmadison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.ConnState
import com.cs407.settlersofmadison.data.p2p.P2PService
import kotlinx.coroutines.flow.StateFlow


class MenuViewModel : ViewModel() {
    private val p2p = P2PService(viewModelScope)

    val state: StateFlow<ConnState> = p2p.state
    val messages: StateFlow<List<String>> = p2p.messages

    fun host(port: Int) = p2p.host(port)
    fun connect(ip: String, port: Int) = p2p.connect(ip, port)
    fun send(msg: String) = p2p.send(msg)
    fun close() = p2p.close()
}
