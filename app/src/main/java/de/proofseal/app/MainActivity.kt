package de.proofseal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.proofseal.app.ui.screens.GuidedCaptureScreen
import de.proofseal.app.ui.screens.HomeScreen
import de.proofseal.app.ui.screens.ProtocolDetailScreen
import de.proofseal.app.ui.screens.SealVerifierScreen
import de.proofseal.app.ui.theme.ProofSealTheme
import de.proofseal.app.ui.viewmodel.ProofSealViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val proofViewModel: ProofSealViewModel = viewModel()
            val uiPreferences by proofViewModel.uiPreferences.collectAsState()

            ProofSealTheme(
                themeMode = uiPreferences.themeMode,
                useDynamicColor = uiPreferences.useDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProofSealAppNavigation(proofViewModel = proofViewModel)
                }
            }
        }
    }
}

@Composable
fun ProofSealAppNavigation(
    proofViewModel: ProofSealViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = proofViewModel,
                onStartCaptureMode = { selectedMode ->
                    proofViewModel.startNewCaptureSession(selectedMode)
                    navController.navigate("capture/${selectedMode.id}")
                },
                onOpenVerifier = {
                    navController.navigate("verifier")
                },
                onOpenProtocolDetail = { protocolId ->
                    navController.navigate("detail/$protocolId")
                }
            )
        }

        composable(
            route = "capture/{modeId}",
            arguments = listOf(navArgument("modeId") { type = NavType.StringType })
        ) {
            GuidedCaptureScreen(
                viewModel = proofViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProtocolSealed = { createdProtocolId ->
                    navController.navigate("detail/$createdProtocolId") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }

        composable("verifier") {
            SealVerifierScreen(
                viewModel = proofViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "detail/{protocolId}",
            arguments = listOf(navArgument("protocolId") { type = NavType.StringType })
        ) { backStackEntry ->
            val protocolId = backStackEntry.arguments?.getString("protocolId").orEmpty()
            ProtocolDetailScreen(
                protocolId = protocolId,
                viewModel = proofViewModel,
                onNavigateBack = {
                    navController.popBackStack("home", inclusive = false)
                },
                onOpenVerifierWithProtocol = {
                    navController.navigate("verifier")
                }
            )
        }
    }
}
