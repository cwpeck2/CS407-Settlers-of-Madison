package com.cs407.settlersofmadison.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import com.cs407.settlersofmadison.network.NetworkUtils
import com.cs407.settlersofmadison.ui.components.TopTitleBar
import com.cs407.settlersofmadison.ui.components.FloatingMenuButton
import com.cs407.settlersofmadison.ui.components.ScreenImageBackground


// ---------- UI ----------

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
fun MainMenuScreen(onCreateRoom: () -> Unit, onJoinRoom: () -> Unit) {
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


