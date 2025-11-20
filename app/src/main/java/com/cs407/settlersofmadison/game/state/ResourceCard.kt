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
){
    val icon = when (res.type) {
        ResourceType.BRICK -> R.drawable.brick
        ResourceType.WHEAT -> R.drawable.wheat
        ResourceType.ORE -> R.drawable.ore
        ResourceType.SHEEP -> R.drawable.sheep
        ResourceType.WOOD -> R.drawable.wood
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier
            .padding(8.dp)
            .size(width = 110.dp, height = 140.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Image(
                painter = painterResource(icon),
                contentDescription = res.type.name,
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = res.type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium
            )
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            {
                Text("x${res.amount}", style = MaterialTheme.typography.bodyLarge)
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

@Preview(showBackground = true)
@Composable
fun Preview_AllResources() {
    Column {
        ResourceCard(ResourceCount(ResourceType.BRICK, 2))
        ResourceCard(ResourceCount(ResourceType.WHEAT, 5))
        ResourceCard(ResourceCount(ResourceType.ORE, 1))
        ResourceCard(ResourceCount(ResourceType.SHEEP, 4))
        ResourceCard(ResourceCount(ResourceType.WOOD, 3))
    }
}


