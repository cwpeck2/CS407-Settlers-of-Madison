package com.cs407.settlersofmadison.ui.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.data.p2p.ConnState
import com.cs407.settlersofmadison.ui.components.ScreenImageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostLobbyScreen(
    vm: LobbyViewModel,
    onBackToMain: () -> Unit,
    onStartGame: () -> Unit,
    profileVm: ProfileViewModel
) {
    val state by vm.state.collectAsState()
    val peerReady by vm.peerReady.collectAsState(initial = false)

    val profile by profileVm.profile.collectAsState()
    val remoteProfile by vm.remoteProfile.collectAsState()

    val isConnected = state == ConnState.Connected
    val canStart = isConnected && peerReady

    val hostLabel = profile.nickname.takeIf { it.isNotBlank() } ?: "You (Host)"
    val hostColor = profile.preferredColor
    val hostAvatar = profile.avatarUri

    val guestLabel = when {
        remoteProfile.nickname.isNotBlank() -> remoteProfile.nickname
        isConnected -> "Guest"
        else -> "Waiting for guest…"
    }
    val guestColor = remoteProfile.preferredColor
    val guestAvatar = remoteProfile.avatarUri


    LaunchedEffect(isConnected, profile) {
        if (isConnected) {
            vm.sendLobbyProfile("host", profile)
        }
    }

    ScreenImageBackground(imageRes = com.cs407.settlersofmadison.R.drawable.create_room) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CenterAlignedTopAppBar(
                title = { Text("Lobby", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = {
                        vm.leave()
                        onBackToMain()
                    }) { Text("Leave", color = Color.White) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(8.dp))

            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(
                        text = if (isConnected) "Players: 2 / 2" else "Players: 1 / 2",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.Top
                    ) {

                        PlayerAvatar(
                            label = hostLabel,
                            active = true,
                            isHost = true,
                            ready = true,
                            colorArgb = hostColor,
                            avatarUri = hostAvatar
                        )


                        PlayerAvatar(
                            label = guestLabel,
                            active = isConnected,
                            isHost = false,
                            ready = peerReady,
                            colorArgb = guestColor,
                            avatarUri = guestAvatar
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            vm.hostStartGame()
                            onStartGame()
                        },
                        enabled = canStart
                    ) {
                        Text("Start Game")
                    }

                    Spacer(Modifier.height(8.dp))

                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Status: $state") }
                    )
                }
            }
        }
    }
}
