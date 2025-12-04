package com.cs407.settlersofmadison.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.cs407.settlersofmadison.ui.main.MenuViewModel

@Composable
fun SharedCounterDebug(vm: MenuViewModel) {
    val gameState by vm.gameState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Shared counter: ${gameState.debugCounter}",
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = { vm.incrementDebugCounter() },
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("+1 (sync test)")
        }
    }
}