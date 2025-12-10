package com.cs407.settlersofmadison.ui.lobby

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onDone: () -> Unit,
    vm: ProfileViewModel
) {
    val profileState by vm.profile.collectAsState()

    var nickname by rememberSaveable { mutableStateOf(profileState.nickname) }
    var selectedColor by rememberSaveable { mutableStateOf(profileState.preferredColor) }
    var avatarUriString by rememberSaveable { mutableStateOf(profileState.avatarUri) }

    // Keep local editable state in sync if ViewModel changes underneath
    LaunchedEffect(profileState) {
        nickname = profileState.nickname
        selectedColor = profileState.preferredColor
        avatarUriString = profileState.avatarUri
    }

    // Image picker for avatar
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val asString = uri?.toString()
        avatarUriString = asString
        vm.setAvatar(asString)
    }

    val colorOptions: List<Long> = listOf(
        0xFFE53935, // red
        0xFF1E88E5, // blue
        0xFF43A047, // green
        0xFFFDD835, // yellow
        0xFF8E24AA  // purple
    ).map { it.toLong() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Your Profile") },
                navigationIcon = {
                    TextButton(onClick = onDone) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ---------- Avatar + edit pencil ----------
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Main circular avatar
                    Surface(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                        color = Color(0xFF202020),
                        tonalElevation = 4.dp
                    ) {
                        if (!avatarUriString.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUriString,
                                contentDescription = "Profile picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile picture",
                                tint = Color.White,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            )
                        }
                    }

                    // Pencil overlay button
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Choose profile image",
                                tint = Color.White
                            )
                        }
                    }
                }

                // ---------- Nickname field ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Nickname",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = {
                                nickname = it
                                vm.updateNickname(it)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("BadgerLegend123") }
                        )
                        Text(
                            "This name will be shown in lobbies and games instead of “Host” / “Guest”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // ---------- Preferred color chips ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Preferred Color",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Used for your roads / settlements and avatar accents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colorOptions.forEach { colorLong ->
                                val isSelected = selectedColor == colorLong
                                val color = Color(colorLong)

                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 40.dp else 32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            selectedColor = colorLong
                                            vm.updateColor(colorLong)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.8f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ---------- Done button ----------
                Button(
                    onClick = {
                        vm.updateAll(
                            nickname = nickname,
                            color = selectedColor,
                            avatarUri = avatarUriString
                        )
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}
