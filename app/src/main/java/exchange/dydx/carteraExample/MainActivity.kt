package exchange.dydx.carteraexample

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import exchange.dydx.cartera.CarteraConfig

class MainActivity : ComponentActivity() {

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
                WalletList.Content()
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
