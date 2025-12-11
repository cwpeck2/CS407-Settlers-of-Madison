package com.cs407.settlersofmadison.ui.lobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

@Composable
fun PlayerAvatar(
    label: String,
    active: Boolean,
    isHost: Boolean,
    ready: Boolean,

    colorArgb: Long? = null,
    avatarUri: String? = null,

    modifier: Modifier = Modifier,
    showEditIcon: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ringColor = when {
        !active -> Color.Gray.copy(alpha = 0.5f)
        ready   -> Color(0xFF21C35E)
        colorArgb != null -> Color(colorArgb)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .border(
                        BorderStroke(3.dp, ringColor),
                        shape = CircleShape
                    )
                    .let { base ->
                        if (onClick != null) {
                            base.clickable { onClick() }
                        } else {
                            base
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = avatarUri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = if (isHost) Icons.Filled.AccountCircle else Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }


            if (showEditIcon && onClick != null) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit avatar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                            CircleShape
                        )
                        .clickable { onClick() }
                )
            }
        }

        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (ready) {
            Text(
                "Ready",
                color = Color(0xFF21C35E),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}