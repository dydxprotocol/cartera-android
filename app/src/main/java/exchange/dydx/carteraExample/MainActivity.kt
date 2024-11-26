package exchange.dydx.carteraexample

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.navigation.material.BottomSheetNavigator
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.walletconnect.wcmodal.ui.walletConnectModalGraph
import exchange.dydx.cartera.CarteraConfig
import exchange.dydx.cartera.WalletConnectionType
import exchange.dydx.cartera.walletprovider.providers.WalletConnectModalProvider

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterialNavigationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            CarteraConfig.handleResponse(uri)
        }

        CarteraConfig.shared = CarteraConfig(
            walletProvidersConfig = WalletProvidersConfigUtil.getWalletProvidersConfig(),
            application = applicationContext as Application,
            launcher = launcher,
        )
        CarteraConfig.shared?.registerWallets(context = applicationContext)

        setContent {
            MyApp {
                val sheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    skipHalfExpanded = true,
                )
                val bottomSheetNavigator = BottomSheetNavigator(sheetState)
                val navController = rememberNavController(bottomSheetNavigator)

                LaunchedEffect(Unit) {
                    // Need to set the nav controller for the WalletConnectModalProvider
                    // so that it can open the modal when needed
                    val modal = CarteraConfig.shared?.getProvider(WalletConnectionType.WalletConnectModal) as? WalletConnectModalProvider
                    modal?.nav = navController
                }

                ModalBottomSheetLayout(bottomSheetNavigator = bottomSheetNavigator) {
                    NavHost(
                        navController = navController,
                        startDestination = "walletList",
                    ) {
                        composable("walletList") {
                            WalletList.Content()
                        }

                        walletConnectModalGraph(navController)
                    }
                }
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface {
            content()
        }
    }
}
