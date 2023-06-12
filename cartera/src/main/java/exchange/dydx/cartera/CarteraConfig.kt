package exchange.dydx.cartera

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.providers.MagicLinkProvider
import exchange.dydx.cartera.providers.WalletConnectV1Provider
import exchange.dydx.cartera.providers.WalletConnectV2Provider
import exchange.dydx.cartera.providers.WalletSegueProvider
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol
import java.lang.reflect.Type


sealed class WalletConnectionType(val rawValue: String) {
    object WalletConnect : WalletConnectionType("walletConnect")
    object WalletConnectV2 : WalletConnectionType("walletConnectV2")
    object WalletSegue : WalletConnectionType("walletSegue")
    object MagicLink : WalletConnectionType("magicLink")
    class Custom(val value: String) : WalletConnectionType(value)
    object Unknown : WalletConnectionType("unknown")

    companion object {
        fun fromRawValue(rawValue: String): WalletConnectionType {
            return when (rawValue) {
                WalletConnect.rawValue -> WalletConnect
                WalletConnectV2.rawValue -> WalletConnectV2
                WalletSegue.rawValue -> WalletSegue
                MagicLink.rawValue -> MagicLink
                else -> Custom(rawValue)
            }
        }
    }
}

class CarteraConfig(
    val walletProvidersConfig: WalletProvidersConfig = WalletProvidersConfig()
) {
    companion object {
        var shared = CarteraConfig()
    }

    private val registration: MutableMap<WalletConnectionType, RegistrationConfig> = mutableMapOf()

    val wallets: List<Wallet>
        get() = _wallets ?: emptyList()

   init {
       registration[WalletConnectionType.WalletConnect] = RegistrationConfig(WalletConnectV1Provider(), null)
       registration[WalletConnectionType.WalletConnectV2] = RegistrationConfig(
           WalletConnectV2Provider(), null)
       registration[WalletConnectionType.WalletConnect] = RegistrationConfig(WalletSegueProvider(), null)
       registration[WalletConnectionType.MagicLink] = RegistrationConfig(MagicLinkProvider(), null)

//        URLHandler.shared = UIApplication.shared
//
//        walletProvidersConfig.walletSegue?.callbackUrl?.let { walletSegueCallbackUrl ->
//            if (!CoinbaseWalletSDK.isConfigured) {
//                CoinbaseWalletSDK.configure(URL(walletSegueCallbackUrl))
//            }
//        }
//
//        walletProvidersConfig.walletConnectV2?.let { walletConnectV2Config ->
//            Networking.configure(
//                projectId = walletConnectV2Config.projectId,
//                socketFactory = DefaultSocketFactory()
//            )
//
//            val metadata = AppMetadata(
//                name = walletConnectV2Config.clientName,
//                description = walletConnectV2Config.clientDescription,
//                url = walletConnectV2Config.clientUrl,
//                icons = walletConnectV2Config.iconUrls
//            )
//
//            Pair.configure(metadata)
//        }
    }

    fun registerProvider(
        connectionType: WalletConnectionType,
        provider: WalletOperationProviderProtocol,
        consent: WalletUserConsentProtocol? = null
    ) {
        registration[connectionType] = RegistrationConfig(provider, consent)
    }

    fun getProvider(connectionType: WalletConnectionType): WalletOperationProviderProtocol? {
        return registration[connectionType]?.provider
    }

    fun getUserConsentHandler(connectionType: WalletConnectionType): WalletUserConsentProtocol? {
        return registration[connectionType]?.consent
    }

    fun registerWallets(context: Context, walletConfigJsonData: String? = null) {
        val wallets: List<Wallet>? = if (walletConfigJsonData != null) {
            registerWalletsInternal(walletConfigJsonData)
        } else {
            val jsonData = context.getResources().openRawResource(R.raw.wallets_config)
                .bufferedReader().use { it.readText() }
            registerWalletsInternal(jsonData)
        }
        _wallets = wallets
    }

    private var _wallets: List<Wallet>? = null

    private fun registerWalletsInternal(walletConfigJsonData: String): List<Wallet>? {
        val gson = Gson()
        val walletListType: Type = object : TypeToken<ArrayList<Wallet?>?>() {}.type
        return gson.fromJson(walletConfigJsonData, walletListType)
    }

    private data class RegistrationConfig(
        val provider: WalletOperationProviderProtocol,
        val consent: WalletUserConsentProtocol?
    )
}

data class WalletProvidersConfig(
    val walletConnectV1: WalletConnectV1Config? = null,
    val walletConnectV2: WalletConnectV2Config? = null,
    val walletSegue: WalletSegueConfig? = null
)

data class WalletConnectV1Config(
    val clientName: String,
    val clientDescription: String? = null,
    val iconUrl: String? = null,
    val scheme: String,
    val clientUrl: String,
    val bridgeUrl: String
)

data class WalletConnectV2Config(
    val projectId: String,
    val clientName: String,
    val clientDescription: String,
    val clientUrl: String,
    val iconUrls: List<String>
)

data class WalletSegueConfig(
    val callbackUrl: String
)