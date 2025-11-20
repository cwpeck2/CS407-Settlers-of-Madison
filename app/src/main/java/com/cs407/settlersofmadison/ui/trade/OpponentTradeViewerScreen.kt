package com.cs407.settlersofmadison.ui.trade


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.game.state.ResourceCount
import com.cs407.settlersofmadison.game.state.ResourceType
import com.cs407.settlersofmadison.ui.components.OrangeGradientButton

@Composable
fun OpponentTradeViewerScreen(
    proposerName: String,
    theirOffer: List<ResourceCount>,
    theirRequest: List<ResourceCount>,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Background
        Image(
            painter = painterResource(id = R.drawable.main_menu),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // HEADER
            Text(
                text = "$proposerName Proposed a Trade",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                SummaryCard(
                    title = "They Offer:",
                    list = theirOffer,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(20.dp))

                SummaryCard(
                    title = "They Request:",
                    list = theirRequest,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ACCEPT BUTTON
            OrangeGradientButton(
                text = "Accept Trade",
                backgroundRes = R.drawable.create_room,
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Reject Trade",
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.clickable { onReject() }
            )
        }
    }
}


@Composable
fun SummaryCard(title: String, list: List<ResourceCount>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            if (list.isEmpty()) {
                Text("Nothing")
            } else {
                list.forEach { res ->
                    Text("${res.type.name} x${res.amount}")
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun OpponentTradeViewerPreview() {
    OpponentTradeViewerScreen(
        proposerName = "Player 1",
        theirOffer = listOf(
            ResourceCount(ResourceType.BRICK, 2),
            ResourceCount(ResourceType.WOOD, 1)
        ),
        theirRequest = listOf(
            ResourceCount(ResourceType.WHEAT, 1)
        ),
        onAccept = {},
        onReject = {}
    )
}

