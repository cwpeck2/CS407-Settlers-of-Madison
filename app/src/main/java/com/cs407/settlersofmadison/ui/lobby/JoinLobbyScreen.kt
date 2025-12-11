package com.cs407.settlersofmadison.ui.lobby

import com.cs407.settlersofmadison.R
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
fun JoinLobbyScreen(
    vm: LobbyViewModel,
    onBackToMain: () -> Unit,
    onGameStarted: () -> Unit,
    profileVm: ProfileViewModel
) {
    val state by vm.state.collectAsState()
    val localReady by vm.localReady.collectAsState(initial = false)
    val peerReady by vm.peerReady.collectAsState(initial = false)
    val gameStarted by vm.gameStarted.collectAsState(initial = false)

    val profile by profileVm.profile.collectAsState(initial = ProfileSettings())
    val remoteProfile by vm.remoteProfile.collectAsState()

    val isConnected = state == ConnState.Connected

    val guestLabel = profile.nickname.takeIf { it.isNotBlank() } ?: "You (Guest)"
    val guestColor = profile.preferredColor
    val guestAvatar = profile.avatarUri

    val hostLabel = when {
        remoteProfile.nickname.isNotBlank() -> remoteProfile.nickname
        isConnected -> "Host"
        else -> "Waiting for host…"
    }
    val hostColor = remoteProfile.preferredColor
    val hostAvatar = remoteProfile.avatarUri


    LaunchedEffect(gameStarted) {
        if (gameStarted) {
            onGameStarted()
        }
    }


    LaunchedEffect(isConnected, profile) {
        if (isConnected) {
            vm.sendLobbyProfile("guest", profile)
        }
    }

    ScreenImageBackground(imageRes = R.drawable.join_room) {
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
                            active = isConnected,
                            isHost = true,
                            ready = peerReady,
                            colorArgb = hostColor,
                            avatarUri = hostAvatar
                        )


                        PlayerAvatar(
                            label = guestLabel,
                            active = true,
                            isHost = false,
                            ready = localReady,
                            colorArgb = guestColor,
                            avatarUri = guestAvatar
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { vm.toggleReady() },
                        enabled = isConnected && !gameStarted
                    ) {
                        Text(if (localReady) "Unready" else "Ready Up")
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
