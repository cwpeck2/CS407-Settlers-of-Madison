package com.cs407.settlersofmadison.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun TopTitleBar() {
    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val gradient = Brush.horizontalGradient(listOf(Color.White, Color(0xFFFFE7C2)))
        val title = buildAnnotatedString { appendStyled("Settlers of Madison", gradient) }

        Text(
            text = title, color = Color.Unspecified,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.ExtraBold,
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f)
            )
        )
        Spacer(Modifier.height(4.dp))
        Text("Quick match • Local P2P", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStyled(text: String, brush: Brush) {
    withStyle(SpanStyle(brush = brush)) { append(text) }
}
