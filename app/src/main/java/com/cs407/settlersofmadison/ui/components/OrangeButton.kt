package com.cs407.settlersofmadison.ui.components


import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp



@Composable
fun OrangeGradientButton(
    text: String,
    @DrawableRes backgroundRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)

    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        shape = shape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
        ) {
            Image(
                painter = painterResource(backgroundRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.12f))
            )

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
