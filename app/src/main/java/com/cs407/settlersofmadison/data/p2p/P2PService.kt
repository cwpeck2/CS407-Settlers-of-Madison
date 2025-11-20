package com.cs407.settlersofmadison.data.p2p

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnState { Idle, Hosting, Connecting, Connected, Error, Closed }

class P2PService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var readerJob: Job? = null
    private val closed = AtomicBoolean(false)

    private val _state = MutableStateFlow(ConnState.Idle)
    val state: StateFlow<ConnState> = _state

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    // 👇 peer ready status (other device)
    private val _peerReady = MutableStateFlow(false)
    val peerReady: StateFlow<Boolean> = _peerReady

    private fun setState(newState: ConnState) {
        scope.launch(Dispatchers.Main) { _state.value = newState }
    }

    fun host(port: Int = 8989) {
        if (_state.value !in listOf(ConnState.Idle, ConnState.Closed)) return
        scope.launch(Dispatchers.Main) { _peerReady.value = false }
        setState(ConnState.Hosting)
        closed.set(false)

        scope.launch(Dispatchers.IO) {
            try {
                val s = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = s
                append("Listening on 0.0.0.0:$port")
                val client = s.accept()
                onSocketReady(client)
            } catch (t: Throwable) {
                setState(ConnState.Error)
                append("Host error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "host() failed", t)
            } finally {
                try { serverSocket?.close() } catch (_: Throwable) {}
                serverSocket = null
            }
        }
    }

    fun connect(hostIp: String = "10.0.2.2", port: Int = 8989) {
        if (_state.value !in listOf(ConnState.Idle, ConnState.Closed)) return
        scope.launch(Dispatchers.Main) { _peerReady.value = false }
        setState(ConnState.Connecting)
        closed.set(false)

        scope.launch(Dispatchers.IO) {
            try {
                append("Connecting to $hostIp:$port ...")
                val s = Socket(hostIp, port)
                onSocketReady(s)
            } catch (t: Throwable) {
                setState(ConnState.Error)
                append("Connect error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "connect() failed", t)
            }
        }
    }

    private fun onSocketReady(s: Socket) {
        socket = s
        setState(ConnState.Connected)
        append("Connected to ${s.inetAddress.hostAddress}:${s.port}")

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(s.getInputStream())).use { br ->
                    while (isActive && !closed.get()) {
                        val line = br.readLine() ?: break

                        // 👇 special protocol for READY messages
                        if (line.startsWith("READY:")) {
                            val ready = line.substringAfter("READY:") == "1"
                            launch(Dispatchers.Main) { _peerReady.value = ready }
                        } else if (line == "LEAVE") {
                            append("Peer left the lobby.")
                            break
                        } else {
                            append("Peer: $line")
                        }
                    }
                }
            } catch (t: Throwable) {
                append("Read error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "reader failed", t)
            } finally {
                close()
            }
        }
    }

    fun send(text: String) {
        val s = socket ?: return
        scope.launch {
            try {
                val pw = PrintWriter(s.getOutputStream(), true)
                pw.println(text)
                append("You: $text")
            } catch (t: Throwable) {
                // ❗ IMPORTANT: don't auto-close on send error
                append("Send error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "send() failed", t)
                // leave the state alone; connection might already be closing
            }
        }
    }

    // Called when *this* device changes its ready state
    fun setReady(ready: Boolean) {
        send(if (ready) "READY:1" else "READY:0")
    }

    fun leave() {
        // graceful disconnect message
        send("LEAVE")
        close()
    }

    fun close() {
        if (closed.getAndSet(true)) return
        try { socket?.close() } catch (_: Throwable) {}
        try { serverSocket?.close() } catch (_: Throwable) {}
        readerJob?.cancel()

        scope.launch(Dispatchers.Main) {
            _peerReady.value = false
            _state.value = ConnState.Closed
        }
    }

    private fun append(s: String) {
        _messages.value = _messages.value + s
    }
}
