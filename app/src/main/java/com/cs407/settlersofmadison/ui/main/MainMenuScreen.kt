package com.cs407.settlersofmadison.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.ui.lobby.ProfileSettings
import com.cs407.settlersofmadison.ui.lobby.ProfileViewModel

@Composable
fun MainMenuScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onProfileClick: () -> Unit,
    profileVm: ProfileViewModel
) {
    val profile by profileVm.profile.collectAsState(initial = ProfileSettings())

    MenuBackground {
        Box(Modifier.fillMaxSize()) {
            TopScrim(height = 220.dp)
            TopTitleBar()

            // 🔹 Big profile circle, slightly below the very top
            ProfileBadge(
                profile = profile,
                onClick = onProfileClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            )

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
                    modifier = Modifier.fillMaxWidth()
                )
                FloatingMenuButton(
                    text = "Join Room",
                    onClick = onJoinRoom,
                    imageRes = R.drawable.join_room,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/* ---------- Visual helpers ---------- */

@Composable
private fun MenuBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.main_menu),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )
        content()
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
private fun TopTitleBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val gradient = Brush.horizontalGradient(
            listOf(Color.White, Color(0xFFFFE7C2))
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
private fun ProfileBadge(
    profile: ProfileSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)                // 🔹 bigger circle
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val avatarUri = profile.avatarUri

            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.8f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = if (profile.nickname.isNotBlank()) profile.nickname else "Profile",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun FloatingMenuButton(
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
            containerColor = Color.Transparent,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 10.dp,
            pressedElevation = 14.dp
        )
    ) {
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
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (pressed) 0.35f else 0.20f))
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
