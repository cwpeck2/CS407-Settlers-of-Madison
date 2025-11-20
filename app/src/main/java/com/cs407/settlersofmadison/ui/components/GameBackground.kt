package com.cs407.settlersofmadison.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.cs407.settlersofmadison.R


@Composable
fun GameBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.main_menu), // same map as home
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        content()
    }
}
