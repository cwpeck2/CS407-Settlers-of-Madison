package com.cs407.settlersofmadison.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

// ---------- Transport (socket-based stand-in for Bluetooth on emulator) ----------
enum class ConnState { Idle, Hosting, Connecting, Connected, Error, Closed }

class P2PService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var readerJob: Job? = null
    private val closed = AtomicBoolean(false)

    private val _state = MutableStateFlow(ConnState.Idle)
    private val _incoming = MutableSharedFlow<String>()
    val state: StateFlow<ConnState> = _state
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages
    private fun setState(newState: ConnState) {
        scope.launch(Dispatchers.Main) {
            _state.value = newState
        }
    }
    fun host(port: Int = 8989) {
        if (state.value != ConnState.Idle && state.value != ConnState.Closed) return
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
        if (state.value != ConnState.Idle && state.value != ConnState.Closed) return
        setState(ConnState.Connecting)
        closed.set(false)

        scope.launch(Dispatchers.IO) {
            try {
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
                        append("Peer: $line")
                        _incoming.emit(line)
                    }
                }
            } catch (t: Throwable) {
                if (!closed.get()) {
                    append("Read error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                    Log.e("P2P", "reader failed", t)
                }
            } finally {
                if (!closed.get()) {
                    close()
                }
            }
        }
    }

    fun send(text: String) {
        val s = socket ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val pw = PrintWriter(s.getOutputStream(), true)
                pw.println(text)
                append("You: $text")
            } catch (t: Throwable) {
                append("Send error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "send() failed", t)
                close()
            }
        }
    }

    fun close() {
        if (closed.getAndSet(true)) return
        try { socket?.close() } catch (_: Throwable) {}
        try { serverSocket?.close() } catch (_: Throwable) {}
        readerJob?.cancel()
        _state.value = ConnState.Closed
    }

    private fun append(s: String) {
        _messages.value = _messages.value + s
    }
}
