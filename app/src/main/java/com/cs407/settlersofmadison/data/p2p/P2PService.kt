package com.cs407.settlersofmadison.data.p2p

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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


    private val _peerReady = MutableStateFlow(false)
    val peerReady: StateFlow<Boolean> = _peerReady


    private val _gameStarted = MutableStateFlow(false)
    val gameStarted: StateFlow<Boolean> = _gameStarted


    private val _gameSeed = MutableStateFlow<Long>(0L)
    val gameSeed: StateFlow<Long> = _gameSeed


    private val _incoming = MutableSharedFlow<String>()
    val incoming: SharedFlow<String> = _incoming


    private val _peerLeft = MutableSharedFlow<Unit>()
    val peerLeft: SharedFlow<Unit> = _peerLeft

    private fun setState(newState: ConnState) {
        scope.launch(Dispatchers.Main) {
            _state.value = newState
        }
    }

    fun host(port: Int = 8989) {
        if (_state.value !in listOf(ConnState.Idle, ConnState.Closed)) return

        closed.set(false)
        scope.launch(Dispatchers.Main) {
            _peerReady.value = false
            _gameStarted.value = false
            _gameSeed.value = 0L
        }
        setState(ConnState.Hosting)

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
                if (!closed.get()) {
                    setState(ConnState.Error)
                    append("Host error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                    Log.e("P2P", "host() failed", t)
                }
            }
        }
    }

    fun connect(host: String, port: Int = 8989) {
        if (_state.value !in listOf(ConnState.Idle, ConnState.Closed)) return

        closed.set(false)
        scope.launch(Dispatchers.Main) {
            _peerReady.value = false
            _gameStarted.value = false
            _gameSeed.value = 0L
        }
        setState(ConnState.Connecting)

        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                onSocketReady(s)
            } catch (t: Throwable) {
                if (!closed.get()) {
                    setState(ConnState.Error)
                    append("Connect error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                    Log.e("P2P", "connect() failed", t)
                }
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

                        val raw = br.readLine()
                        if (raw == null) {

                            append("Peer disconnected.")
                            _peerLeft.emit(Unit)
                            break
                        }

                        val line = raw.trim()
                        append("RX: $line")

                        when {

                            line.startsWith("START_GAME") -> {
                                val parts = line.split(":")
                                val seed = if (parts.size > 1) parts[1].toLongOrNull() ?: 0L else 0L
                                scope.launch(Dispatchers.Main) {
                                    _gameSeed.value = seed
                                    _gameStarted.value = true
                                }
                            }
                            line.startsWith("READY:") -> {
                                val ready = line.substringAfter("READY:") == "1"
                                scope.launch(Dispatchers.Main) {
                                    _peerReady.value = ready
                                }
                            }
                            line == "LEAVE" -> {

                                append("Peer left the lobby.")
                                _peerLeft.emit(Unit)
                                break
                            }
                            else -> {

                                _incoming.emit(line)
                            }
                        }
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
                append("TX: $text")
            } catch (t: Throwable) {
                append("Send error: ${t::class.java.simpleName}: ${t.message ?: "no message"}")
                Log.e("P2P", "send() failed", t)
                close()
            }
        }
    }


    fun setReady(ready: Boolean) {
        send(if (ready) "READY:1" else "READY:0")
    }


    fun sendStartGame(seed: Long) {
        _gameSeed.value = seed
        _gameStarted.value = true
        send("START_GAME:$seed")
    }

    fun leave() {

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
            _gameStarted.value = false
            _state.value = ConnState.Closed
        }
    }

    private fun append(s: String) {
        _messages.value = _messages.value + s
    }
}
