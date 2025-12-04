package com.cs407.settlersofmadison.ui.lobby

import com.cs407.settlersofmadison.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.network.ConnState
import com.cs407.settlersofmadison.ui.components.ScreenImageBackground
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinLobbyScreen(
    vm: LobbyViewModel,
    onBackToMain: () -> Unit
) {
    val state by vm.state.collectAsState()
    val localReady by vm.localReady.collectAsState(initial = false)
    val peerReady by vm.peerReady.collectAsState(initial = false)

    val isConnected = state == ConnState.Connected

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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(
                            label = "Host",
                            active = isConnected,
                            isHost = true,
                            ready = peerReady
                        )
                        PlayerAvatar(
                            label = "You",
                            active = true,
                            isHost = false,
                            ready = localReady
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { vm.toggleReady() },
                        enabled = isConnected
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