import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cs407.settlersofmadison.ui.main.MainMenuScreen
import com.cs407.settlersofmadison.ui.lobby.HostSetupScreen
import com.cs407.settlersofmadison.ui.lobby.HostLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.JoinSetupScreen
import com.cs407.settlersofmadison.ui.lobby.JoinLobbyScreen
import com.cs407.settlersofmadison.ui.lobby.LobbyViewModel
import com.cs407.settlersofmadison.ui.game.GameScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val vm: LobbyViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainMenuScreen(
                onCreateRoom = { navController.navigate("hostSetup") },
                onJoinRoom   = { navController.navigate("joinSetup") }
            )
        }

        composable("hostSetup") {
            HostSetupScreen(
                vm = vm,
                onHosted = { navController.navigate("hostLobby") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("hostLobby") {
            val vm: LobbyViewModel = viewModel()

            HostLobbyScreen(
                vm = vm,
                onBackToMain = {
                    vm.leave()
                    navController.popBackStack("main", inclusive = false)
                },
                onStartGame = {
                    navController.navigate("game")
                }
            )
        }


        composable("joinSetup") {
            JoinSetupScreen(
                vm = vm,
                onJoined = { navController.navigate("joinLobby") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("joinLobby") {
            JoinLobbyScreen(
                vm = vm,
                onBackToMain = {
                    vm.close()
                    navController.popBackStack("main", inclusive = false)
                }
            )
        }

        composable("game") {
            GameScreen(
                onExit = {
                    vm.leave()
                    navController.popBackStack("main", inclusive = false)
                }
            )
        }
    }
}
