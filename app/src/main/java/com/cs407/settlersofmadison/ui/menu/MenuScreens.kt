package com.cs407.settlersofmadison.ui.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.network.ConnState
import com.cs407.settlersofmadison.ui.menu.MenuViewModel
import com.cs407.settlersofmadison.network.P2PService
import com.cs407.settlersofmadison.network.NetworkUtils


// ---------- UI ----------

@Composable
private fun GradientBackground(content: @Composable () -> Unit) {
    val c1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val c2 = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c1, Color.Transparent, c2)))
    ) { content() }
}
@Composable
fun FloatingMenuButton(
    text: String,
    onClick: () -> Unit,
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    contentColor: Color = Color.White
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    ElevatedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        interactionSource = interaction,
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = Color.Transparent, // we draw the image ourselves
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 10.dp,
            pressedElevation = 14.dp
        )
    ) {
        // Full-bleed image background + subtle scrim for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            // Scrim: a bit stronger while pressed
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Color.Black.copy(alpha = if (pressed) 0.35f else 0.20f)
                    )
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
@Composable
private fun TopScrim(height: Dp = 200.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                )
            )
    )
}

@Composable
fun TopTitleBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gradient title (built-in Serif font)
        val gradient = Brush.horizontalGradient(
            listOf(Color.White, Color(0xFFFFE7C2)) // tweak if you like
        )
        val title = buildAnnotatedString {
            withStyle(SpanStyle(brush = gradient)) { append("Settlers of Madison") }
        }

        Text(
            text = title,
            color = Color.Unspecified,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.ExtraBold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f
                )
            )
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Quick match • Local P2P",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f)
        )
    }
}
@Composable
fun FancyBuiltInTitle() {
    val gradient = Brush.horizontalGradient(
        listOf(Color.White, Color(0xFFFFE7C2))
    )

    val text = buildAnnotatedString {
        withStyle(SpanStyle(brush = gradient)) { append("Settlers of Madison") }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.ExtraBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.35f),
                offset = Offset(0f, 2f),
                blurRadius = 8f
            )
        ),
        color = Color.Unspecified
    )
}
@Composable
fun MainMenu(onCreateRoom: () -> Unit, onJoinRoom: () -> Unit) {
    MenuBackground {
        Box(Modifier.fillMaxSize()) {
            // ensure readability over the sky area
            TopScrim(height = 220.dp)
            // title + subtitle at the very top
            TopTitleBar()

            // floating, centered buttons (no card)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingMenuButton(
                    text = "Create Room (Host)",
                    onClick = onCreateRoom,

                    imageRes = R.drawable.create_room,
                    modifier = Modifier.fillMaxWidth(),
                )
                FloatingMenuButton(
                    text = "Join Room",
                    onClick = onJoinRoom,
                    imageRes = R.drawable.join_room,
                    modifier = Modifier.fillMaxWidth(),

                    )
            }
        }
    }
}

@Composable
private fun MenuBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.main_menu),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Subtle dark scrim so text stays readable
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
        content()
    }
}

@Composable
fun HostRoomScreen(vm: MenuViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val msgs by vm.messages.collectAsState(initial = emptyList())
    var portText by remember { mutableStateOf("8989") }

    val isPeerConnected = state == ConnState.Connected
    val hostIp = remember { NetworkUtils.getLocalIpAddress() ?: "Unknown" }

    ScreenImageBackground(imageRes = R.drawable.create_room) {   // ← was GradientBackground
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Host Room", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors( // slightly translucent card over photo
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Your IP (give this to players): $hostIp",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { vm.host(portText.toIntOrNull() ?: 8989) },
                            enabled = state == ConnState.Idle || state == ConnState.Closed,
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Start Hosting") }

                        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                            Text("Back")
                        }
                    }

                    Divider()
                    PlayerLobbyStatus(isPeerConnected = isPeerConnected) // ← your existing host lobby

                    Text("Status: $state", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { vm.sendChat("PING from Host") },
                        enabled = state == ConnState.Connected,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Send \"PING\"") }

                    Divider()

                    SharedCounterDebug(vm)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                LazyColumn(Modifier.padding(12.dp)) { items(msgs) { Text(it) } }
            }
        }
    }
}

@Composable
private fun PlayerLobbyStatus(isPeerConnected: Boolean) {
    // Count
    val countText = if (isPeerConnected) "Players: 2/2" else "Players: 1/2"
    Text(countText, style = MaterialTheme.typography.titleMedium)

    Spacer(Modifier.height(8.dp))

    // Two slots: Host (left, always active) + Peer (right, gray until connected)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerSlot(
            label = "You (Host)",
            icon = Icons.Filled.AccountCircle,
            active = true
        )
        PlayerSlot(
            label = if (isPeerConnected) "Player 2" else "Waiting…",
            icon = Icons.Filled.Person,
            active = isPeerConnected
        )
    }
}

@Composable
private fun PlayerSlot(label: String, icon: ImageVector, active: Boolean) {
    val bgColor = if (active) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val dotColor = if (active) Color(0xFF21C35E) else Color(0xFF9E9E9E) // green/gray status dot

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation( if (active) 10.dp else 2.dp ),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor)
    ) {
        Box(Modifier.size(width = 140.dp, height = 88.dp)) {
            // Big icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
            // Label
            Text(
                label,
                color = fgColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
            // Status dot (top-right)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(12.dp)
                    .background(dotColor, shape = RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun ScreenImageBackground(@DrawableRes imageRes: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // soft dark scrim for legibility
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        content()
    }
}

@Composable
private fun PlayerLobbyStatusJoin(isConnected: Boolean) {
    val countText = if (isConnected) "Players: 2/2" else "Players: 1/2"
    Text(countText, style = MaterialTheme.typography.titleMedium)

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerSlot(
            label = if (isConnected) "Host" else "Waiting for Host…",
            icon = Icons.Filled.AccountCircle,
            active = isConnected
        )
        PlayerSlot(
            label = "You (Client)",
            icon = Icons.Filled.Person,
            active = true
        )
    }
}
@Composable
fun JoinRoomScreen(vm: MenuViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val msgs by vm.messages.collectAsState(initial = emptyList())
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8989") }

    val isConnected = state == ConnState.Connected

    ScreenImageBackground(imageRes = R.drawable.join_room) {   // ← was GradientBackground
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join Room", style = MaterialTheme.typography.titleLarge, color = Color.White)

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("Host IP") })
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { vm.connect(ip, port.toIntOrNull() ?: 8989) },
                            enabled = state == ConnState.Idle || state == ConnState.Closed,
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Connect") }

                        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) { Text("Back") }
                    }

                    Divider()
                    PlayerLobbyStatusJoin(isConnected = isConnected)   // ← new join lobby

                    Text("Status: $state", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { vm.sendChat("PING from Client") },
                        enabled = state == ConnState.Connected,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Send \"PING\"") }

                    Divider()

                    SharedCounterDebug(vm)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                LazyColumn(Modifier.padding(12.dp)) { items(msgs) { Text(it) } }
            }
        }
    }
}

@Composable
fun SharedCounterDebug(vm: MenuViewModel) {
    val gameState by vm.gameState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Shared counter: ${gameState.debugCounter}",
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = { vm.incrementDebugCounter() },
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("+1 (sync test)")
        }
    }
}