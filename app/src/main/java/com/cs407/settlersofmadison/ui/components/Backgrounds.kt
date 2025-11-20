package com.cs407.settlersofmadison.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun ScreenImageBackground(@DrawableRes imageRes: Int, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
        content()
    }
}

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val c1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val c2 = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c1, Color.Transparent, c2)))) { content() }
}
