package com.cs407.settlersofmadison.ui.lobby

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PlayerAvatar(
    label: String,
    active: Boolean,
    isHost: Boolean,
    ready: Boolean
) {
    val borderColor = when {
        !active -> Color.Gray.copy(alpha = 0.6f)
        ready   -> Color(0xFF21C35E) // green “ready” ring
        else    -> MaterialTheme.colorScheme.primary
    }

    ElevatedCard(
        shape = CircleShape,
        elevation = CardDefaults.elevatedCardElevation(if (active) 10.dp else 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.Black.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, borderColor, CircleShape)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isHost) Icons.Filled.AccountCircle else Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            if (ready) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ready",
                    color = Color(0xFF21C35E),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}