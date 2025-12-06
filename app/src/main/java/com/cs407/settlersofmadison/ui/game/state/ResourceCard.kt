package com.cs407.settlersofmadison.game.state

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs407.settlersofmadison.R

@Composable
fun ResourceCard(
    res: ResourceCount,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Card(
        modifier = modifier
            .width(110.dp)
            .height(170.dp),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            // The important part: image fills card & is clipped to rounded corners
            Image(
                painter = painterResource(id = res.type.toCardDrawable()),
                contentDescription = res.type.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )

            // Little "x2" / "x3" badge like in your screenshot
            if (res.amount > 1) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "x${res.amount}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun ResourceType.toCardDrawable(): Int = when (this) {
    ResourceType.CONCRETE    -> R.drawable.concrete_resource      // your concrete card PNG
    ResourceType.STUDENT     -> R.drawable.student_resource       // your purple student card
    ResourceType.BUCKY       -> R.drawable.bucky_resource
    ResourceType.CHAIR       -> R.drawable.chair_resource
    ResourceType.CHEESE_CURD -> R.drawable.cheese_curd_resource
    ResourceType.LAKE        -> 0
}