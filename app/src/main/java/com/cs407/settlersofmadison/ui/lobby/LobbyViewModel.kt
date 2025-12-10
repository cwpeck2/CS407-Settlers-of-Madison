package com.cs407.settlersofmadison.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.settlersofmadison.data.p2p.ConnState
import com.cs407.settlersofmadison.data.p2p.P2PHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class LobbyViewModel : ViewModel() {

    private val p2p = P2PHolder.service

    val state: StateFlow<ConnState> = p2p.state
    val messages = p2p.messages

    // local ready (host on host device, joiner on join device)
    private val _localReady = MutableStateFlow(false)
    val localReady: StateFlow<Boolean> = _localReady

    // remote ready (other device)
    val peerReady: StateFlow<Boolean> = p2p.peerReady

    // game started flag (host -> guest)
    val gameStarted: StateFlow<Boolean> = p2p.gameStarted

    // Expose the seed from P2P
    val gameSeed: StateFlow<Long> = p2p.gameSeed

    // ---------- Remote profile for lobby (nickname, color, avatar) ----------
    private val _remoteProfile = MutableStateFlow(ProfileSettings())
    val remoteProfile: StateFlow<ProfileSettings> = _remoteProfile

    init {
        // Listen for lobby-level profile messages
        viewModelScope.launch {
            p2p.incoming.collect { line ->
                // Format:
                // LOBBY:PROFILE:<playerId>:<nickname>:<colorLong>:<avatarUri?>
                if (line.startsWith("LOBBY:PROFILE:")) {
                    val parts = line.split(":")

                    if (parts.size >= 5) {
                        val nickname = parts[3]
                        val colorLong = parts[4].toLongOrNull()

                        // Everything after the 5th colon is the avatarUri (so it can contain ':')
                        val avatarUri: String? = if (parts.size >= 6) {
                            parts.subList(5, parts.size).joinToString(":").ifBlank { null }
                        } else {
                            null
                        }

                        _remoteProfile.value = ProfileSettings(
                            nickname = nickname,
                            preferredColor = colorLong,
                            avatarUri = avatarUri
                        )
                    }
                }
            }
        }
    }

    // Send our lobby profile (host or guest) to the peer
    fun sendLobbyProfile(playerId: String, profile: ProfileSettings) {
        // Colon-safe nickname (just in case)
        val safeNickname = (profile.nickname).replace(":", " ")
        val colorStr = (profile.preferredColor ?: -1L).toString()

        // avatarUri may contain ':', so we put it at the END and reconstruct
        val avatarPart = profile.avatarUri ?: ""

        // Message format:
        // LOBBY:PROFILE:<playerId>:<nickname>:<colorLong>:<avatarUri?>
        val msg = "LOBBY:PROFILE:$playerId:$safeNickname:$colorStr:$avatarPart"
        p2p.send(msg)
    }

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

    // Generate seed and start, host side
    fun hostStartGame() {
        val seed = Random.nextLong()
        p2p.sendStartGame(seed)
    }

    fun send(msg: String) = p2p.send(msg)

    fun leave() {
        _localReady.value = false
        p2p.leave()
    }

    fun close() = leave()
}
