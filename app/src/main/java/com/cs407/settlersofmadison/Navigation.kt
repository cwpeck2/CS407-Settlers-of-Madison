package com.cs407.settlersofmadison

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cs407.settlersofmadison.ui.game.GameScreen
import com.cs407.settlersofmadison.ui.lobby.HostLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.HostSetupScreen
import com.cs407.settlersofmadison.ui.lobby.JoinLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.JoinSetupScreen
import com.cs407.settlersofmadison.ui.lobby.LobbyViewModel
import com.cs407.settlersofmadison.ui.main.MainMenuScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()

    // One shared LobbyViewModel for the whole graph
    val lobbyVm: LobbyViewModel = viewModel()

    // Track whether this device is acting as "host" or "guest"
    var playerId by remember { mutableStateOf("host") }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainMenuScreen(
                onCreateRoom = {
                    playerId = "host"
                    navController.navigate("hostSetup")
                },
                onJoinRoom = {
                    playerId = "guest"
                    navController.navigate("joinSetup")
                }
            )
        }

        composable("hostSetup") {
            HostSetupScreen(
                vm = lobbyVm,
                onHosted = { navController.navigate("hostLobby") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("hostLobby") {
            HostLobbyScreen(
                vm = lobbyVm,
                onBackToMain = {
                    lobbyVm.leave()
                    navController.popBackStack("main", inclusive = false)
                },
                onStartGame = {
                    navController.navigate("game")
                }
            )
        }

        composable("joinSetup") {
            JoinSetupScreen(
                vm = lobbyVm,
                onJoined = { navController.navigate("joinLobby") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("joinLobby") {
            JoinLobbyScreen(
                vm = lobbyVm,
                onBackToMain = {
                    lobbyVm.leave()
                    navController.popBackStack("main", inclusive = false)
                },
                onGameStarted = {
                    navController.navigate("game")
                }
            )
        }

        composable("game") {
            GameScreen(
                gameManager = lobbyVm.gameManager, // shared game state + P2P
                playerId = playerId,               // "host" or "guest"
                onExit = {
                    // You can decide whether to keep the connection alive or not
                    navController.popBackStack("main", inclusive = false)
                }
            )
        }
    }
}