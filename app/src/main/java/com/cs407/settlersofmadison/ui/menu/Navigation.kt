package com.cs407.settlersofmadison.ui.menu

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cs407.settlersofmadison.ui.menu.HostRoomScreen
import com.cs407.settlersofmadison.ui.menu.JoinRoomScreen
import com.cs407.settlersofmadison.ui.menu.MainMenu

// ---------- Navigation ----------
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: MenuViewModel = viewModel()
    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainMenu(
                onCreateRoom = { nav.navigate("host") },
                onJoinRoom = { nav.navigate("join") }
            )
        }
        composable("host") { HostRoomScreen(vm = vm, onBack = { nav.popBackStack() }) }
        composable("join") { JoinRoomScreen(vm = vm, onBack = { nav.popBackStack() }) }
    }
}