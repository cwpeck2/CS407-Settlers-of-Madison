import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cs407.settlersofmadison.ui.game.GameScreen
import com.cs407.settlersofmadison.ui.lobby.HostLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.HostSetupScreen
import com.cs407.settlersofmadison.ui.lobby.JoinLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.JoinSetupScreen
import com.cs407.settlersofmadison.ui.lobby.LobbyViewModel
import com.cs407.settlersofmadison.ui.lobby.ProfileScreen
import com.cs407.settlersofmadison.ui.lobby.ProfileViewModel
import com.cs407.settlersofmadison.ui.main.MainMenuScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()

    // ONE shared Lobby VM + ONE shared Profile VM
    val lobbyVm: LobbyViewModel = viewModel()
    val profileVm: ProfileViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainMenuScreen(
                onCreateRoom = { navController.navigate("hostSetup") },
                onJoinRoom   = { navController.navigate("joinSetup") },
                onProfileClick = { navController.navigate("profile") },
                profileVm = profileVm
            )
        }

        composable("profile") {
            ProfileScreen(
                onDone = { navController.popBackStack() },
                vm = profileVm
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
                    val seed = lobbyVm.gameSeed.value
                    navController.navigate("game/host/$seed")
                },
                profileVm = profileVm
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
                    lobbyVm.close()
                    navController.popBackStack("main", inclusive = false)
                },
                onGameStarted = {
                    val seed = lobbyVm.gameSeed.value
                    navController.navigate("game/guest/$seed")
                },
                profileVm = profileVm
            )
        }

        composable(
            route = "game/{role}/{seed}",
            arguments = listOf(
                navArgument("role") { type = NavType.StringType },
                navArgument("seed") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "host"
            val seed = backStackEntry.arguments?.getLong("seed") ?: 0L

            GameScreen(
                localPlayerId = role,
                seed = seed,
                onExit = {
                    lobbyVm.leave()
                    navController.popBackStack("main", inclusive = false)
                },
                profileVm = profileVm
            )
        }
    }
}
