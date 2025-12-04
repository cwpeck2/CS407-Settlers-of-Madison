package com.cs407.settlersofmadison.ui.lobby
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.MenuViewModel
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.data.p2p.ConnState
import com.cs407.settlersofmadison.ui.components.ScreenImageBackground
@Composable
fun JoinSetupScreen(
    vm: LobbyViewModel,
    onJoined: () -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    var ip by remember { mutableStateOf("10.0.2.2") }
    var port by remember { mutableStateOf("8989") }

    ScreenImageBackground(imageRes = R.drawable.join_room) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Join a Game",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it },
                        label = { Text("Host IP") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            vm.connect(ip, port.toIntOrNull() ?: 8989)
                            onJoined()
                        },
                        enabled = state == ConnState.Idle || state == ConnState.Closed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Join Room")
                    }
                    Text(
                        "Status: $state",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = {
                vm.close()
                onBack()
            }) {
                Text("Back to Main Menu", color = Color.White)
            }
        }
    }
}
