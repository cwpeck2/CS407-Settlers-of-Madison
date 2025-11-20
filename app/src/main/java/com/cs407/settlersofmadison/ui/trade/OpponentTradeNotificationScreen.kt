package com.cs407.settlersofmadison.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.settlersofmadison.ui.components.GameBackground
import com.cs407.settlersofmadison.ui.components.OrangeGradientButton
import com.cs407.settlersofmadison.R


@Composable
fun OpponentTradeNotificationScreen(
    requestingPlayerName: String = "Player 1",
    onOpenTrade: () -> Unit = {},
    onIgnore: () -> Unit = {}
) {
    GameBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    Text(
                        text = "$requestingPlayerName has requested a trade.",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "Would you like to review the offer?",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    OrangeGradientButton(
                        text = "Open Trade Window",
                        backgroundRes = R.drawable.create_room, // same orange gradient
                        onClick = { onOpenTrade() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = onIgnore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ignore")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 300)
@Composable
fun OpponentTradeNotificationPreview() {
    OpponentTradeNotificationScreen()
}