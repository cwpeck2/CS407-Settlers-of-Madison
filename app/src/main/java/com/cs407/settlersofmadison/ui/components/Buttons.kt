package com.cs407.settlersofmadison.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color.Transparent, contentColor = contentColor),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 10.dp, pressedElevation = 14.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            Image(painter = painterResource(imageRes), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = if (pressed) 0.35f else 0.20f)))
            Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
        }
    }
}