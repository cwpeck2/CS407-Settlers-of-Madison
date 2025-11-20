package com.cs407.settlersofmadison.ui.trade

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.cs407.settlersofmadison.R
import com.cs407.settlersofmadison.game.state.ResourceCount
import com.cs407.settlersofmadison.game.state.ResourceType
import com.cs407.settlersofmadison.ui.components.OrangeGradientButton

@Composable
fun TradeInitiatorScreen(
    myResources: List<ResourceCount>,
    opponentResources: List<ResourceCount>,
    onConfirmTrade: (List<ResourceCount>, List<ResourceCount>) -> Unit,
    onCancel: () -> Unit
) {
    // Selected offers
    var myOffer by remember { mutableStateOf(emptyList<ResourceCount>()) }
    var theirOffer by remember { mutableStateOf(emptyList<ResourceCount>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.main_menu),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )

        Row(
            modifier = Modifier.fillMaxSize()
                .padding(bottom = 110.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // LEFT PANEL - Your Offer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Your Offer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(myResources) { res ->
                        SelectableResourceCard(
                            res = res,
                            cardSize = 120.dp,
                            onAdd = {
                                myOffer = myOffer + it
                            },
                            onRemove = {
                                myOffer = myOffer.filterNot { r -> r.type == res.type }
                            }
                        )
                    }
                }

                SummaryCard("Offering:", myOffer)
            }

            // RIGHT PANEL - Their Offer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "What You Want",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(opponentResources) { res ->
                        SelectableResourceCard(
                            res = res,
                            cardSize = 120.dp,
                            onAdd = {
                                theirOffer = theirOffer + it
                            },
                            onRemove = {
                                theirOffer = theirOffer.filterNot { r -> r.type == res.type }
                            }
                        )
                    }
                }


                SummaryCard("Requesting:", theirOffer)
            }
        }

        // Bottom Confirm Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            OrangeGradientButton(
                text = "Propose Trade",
                backgroundRes = R.drawable.create_room,
                onClick = { onConfirmTrade(myOffer, theirOffer) },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Cancel",
                modifier = Modifier.clickable { onCancel() },
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun SelectableResourceCard(
    res: ResourceCount,
    cardSize: Dp = 120.dp,
    onAdd: (ResourceCount) -> Unit,
    onRemove: (ResourceCount) -> Unit
) {
    Card(
        modifier = Modifier.size(cardSize),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val icon = when (res.type) {
                ResourceType.BRICK -> R.drawable.brick
                ResourceType.WHEAT -> R.drawable.wheat
                ResourceType.ORE -> R.drawable.ore
                ResourceType.SHEEP -> R.drawable.sheep
                ResourceType.WOOD -> R.drawable.wood
            }

            Image(
                painter = painterResource(icon),
                contentDescription = res.type.name,
                modifier = Modifier.size(40.dp)
            )

            Text(
                res.type.name.lowercase().replaceFirstChar { it.uppercase() },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "-",
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = .1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable { onRemove(res) }
                )
                Text(
                    "+",
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable { onAdd(res) },
                    color = Color.White
                )
            }
        }
    }
}


@Composable
fun SummaryCard(title: String, list: List<ResourceCount>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            if (list.isEmpty()) {
                Text("Nothing selected")
            } else {
                list.forEach { res ->
                    Text("${res.type.name} x${res.amount}")
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420
)
@Composable
fun TradeInitiatorPreview() {
    // Sample resources for preview only
    val myResources = listOf(
        ResourceCount(ResourceType.BRICK, 2),
        ResourceCount(ResourceType.WHEAT, 3),
        ResourceCount(ResourceType.WOOD, 1),
        ResourceCount(ResourceType.ORE, 1),
        ResourceCount(ResourceType.SHEEP, 2),
    )

    val opponentResources = listOf(
        ResourceCount(ResourceType.BRICK, 1),
        ResourceCount(ResourceType.WHEAT, 2),
        ResourceCount(ResourceType.WOOD, 3),
        ResourceCount(ResourceType.ORE, 1),
        ResourceCount(ResourceType.SHEEP, 1),
    )

    TradeInitiatorScreen(
        myResources = myResources,
        opponentResources = opponentResources,
        onConfirmTrade = { _, _ -> },
        onCancel = {}
    )
}

