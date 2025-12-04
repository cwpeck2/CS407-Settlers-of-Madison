package com.cs407.settlersofmadison.game.state

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.R

@Composable
fun ResourceCard(
    res: ResourceCount,
    onClick: (() -> Unit)? = null
) {
    val icon = when (res.type) {
        ResourceType.BRICK -> R.drawable.brick
        ResourceType.WHEAT -> R.drawable.wheat
        ResourceType.ORE   -> R.drawable.ore
        ResourceType.SHEEP -> R.drawable.sheep
        ResourceType.WOOD  -> R.drawable.wood
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier
            .padding(2.dp)
            .size(width = 80.dp, height = 110.dp)  // smaller
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(6.dp)
            ) {
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = res.type.name,
                    modifier = Modifier.size(40.dp)   // smaller icon
                )

                Text(
                    text = res.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium
                )

                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("x${res.amount}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview_ResourceCard() {
    ResourceCard(
        res = ResourceCount(ResourceType.BRICK, 3)
    )
}
