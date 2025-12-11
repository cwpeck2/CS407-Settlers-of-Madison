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


    private val _localReady = MutableStateFlow(false)
    val localReady: StateFlow<Boolean> = _localReady


    val peerReady: StateFlow<Boolean> = p2p.peerReady


    val gameStarted: StateFlow<Boolean> = p2p.gameStarted


    val gameSeed: StateFlow<Long> = p2p.gameSeed


    private val _remoteProfile = MutableStateFlow(ProfileSettings())
    val remoteProfile: StateFlow<ProfileSettings> = _remoteProfile

    init {

        viewModelScope.launch {
            p2p.incoming.collect { line ->


                if (line.startsWith("LOBBY:PROFILE:")) {
                    val parts = line.split(":")

                    if (parts.size >= 5) {
                        val nickname = parts[3]
                        val colorLong = parts[4].toLongOrNull()


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


    fun sendLobbyProfile(playerId: String, profile: ProfileSettings) {

        val safeNickname = (profile.nickname).replace(":", " ")
        val colorStr = (profile.preferredColor ?: -1L).toString()


        val avatarPart = profile.avatarUri ?: ""



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
