package exchange.dydx.cartera

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol
import exchange.dydx.cartera.walletprovider.providers.MagicLinkProvider
import exchange.dydx.cartera.walletprovider.providers.PhantomWalletProvider
import exchange.dydx.cartera.walletprovider.providers.WalletConnectModalProvider
import exchange.dydx.cartera.walletprovider.providers.WalletConnectV1Provider
import exchange.dydx.cartera.walletprovider.providers.WalletConnectV2Provider
import exchange.dydx.cartera.walletprovider.providers.WalletSegueProvider
import java.lang.reflect.Type

sealed class WalletConnectionType(val rawValue: String) {
    object WalletConnect : WalletConnectionType("walletConnect")
    object WalletConnectV2 : WalletConnectionType("walletConnectV2")
    object WalletConnectModal : WalletConnectionType("walletConnectModal")
    object WalletSegue : WalletConnectionType("walletSegue")
    object MagicLink : WalletConnectionType("magicLink")
    object PhantomWallet : WalletConnectionType("phantomWallet")
    class Custom(val value: String) : WalletConnectionType(value)
    object Unknown : WalletConnectionType("unknown")

    companion object {
        fun fromRawValue(rawValue: String): WalletConnectionType {
            return when (rawValue) {
                WalletConnect.rawValue -> WalletConnect
                WalletConnectV2.rawValue -> WalletConnectV2
                WalletConnectModal.rawValue -> WalletConnectModal
                WalletSegue.rawValue -> WalletSegue
                MagicLink.rawValue -> MagicLink
                PhantomWallet.rawValue -> PhantomWallet
                else -> Custom(rawValue)
            }
        }
    }
}

class CarteraConfig(
    private val walletProvidersConfig: WalletProvidersConfig = WalletProvidersConfig(),
    private val application: Application,
    private val launcher: ActivityResultLauncher<Intent>?
) {
    companion object {
        var shared: CarteraConfig? = null

        fun handleResponse(url: Uri): Boolean {
            shared?.registration?.values?.forEach {
                if (it.provider.handleResponse(url)) {
                    return@handleResponse true
                }
            }
            return false
        }
    }

    private val registration: MutableMap<WalletConnectionType, RegistrationConfig> = mutableMapOf()

    val wallets: List<Wallet>
        get() = _wallets ?: emptyList()

    init {
        registration[WalletConnectionType.WalletConnect] = RegistrationConfig(
            provider = WalletConnectV1Provider(),
        )
        if (walletProvidersConfig.walletConnectV2 != null) {
            registration[WalletConnectionType.WalletConnectV2] = RegistrationConfig(
                provider = WalletConnectV2Provider(
                    walletConnectV2Config = walletProvidersConfig.walletConnectV2,
                    application = application,
                ),
            )
        }
        if (walletProvidersConfig.walletSegue != null) {
            registration[WalletConnectionType.WalletSegue] = RegistrationConfig(
                provider = WalletSegueProvider(
                    walletSegueConfig = walletProvidersConfig.walletSegue,
                    application = application,
                    launcher = launcher,
                ),
            )
        }
        if (walletProvidersConfig.phantomWallet != null) {
            registration[WalletConnectionType.PhantomWallet] = RegistrationConfig(
                provider = PhantomWalletProvider(
                    phantomWalletConfig = walletProvidersConfig.phantomWallet,
                    application = application,
                ),
            )
        }
        registration[WalletConnectionType.MagicLink] = RegistrationConfig(
            provider = MagicLinkProvider(),
        )
    }

    fun updateModalConfig(walletConnectModal: WalletConnectModalConfig) {
        registration[WalletConnectionType.WalletConnectModal] = RegistrationConfig(
            provider = WalletConnectModalProvider(
                application = application,
                config = walletConnectModal,
            ),
        )
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
        val walletListType: Type = object : TypeToken<List<Wallet?>?>() {}.type
        return gson.fromJson(walletConfigJsonData, walletListType)
    }

    private data class RegistrationConfig(
        val provider: WalletOperationProviderProtocol,
        val consent: WalletUserConsentProtocol? = null
    )
}

data class WalletProvidersConfig(
    val walletConnectV1: WalletConnectV1Config? = null,
    val walletConnectV2: WalletConnectV2Config? = null,
    val walletConnectModal: WalletConnectModalConfig? = null,
    val walletSegue: WalletSegueConfig? = null,
    val phantomWallet: PhantomWalletConfig? = null,
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

data class WalletConnectModalConfig(
    val walletIds: List<String>?
) {
    companion object {
        val default: WalletConnectModalConfig = WalletConnectModalConfig(
            walletIds = listOf(
                "c57ca95b47569778a828d19178114f4db188b89b763c899ba0be274e97267d96", // Metamask
                "4622a2b2d6af1c9844944291e5e7351a6aa24cd7b23099efac1b2fd875da31a0", // Trust
                "971e689d0a5be527bac79629b4ee9b925e82208e5168b733496a09c0faed0709", // OKX
                "c03dfee351b6fcc421b4494ea33b9d4b92a984f87aa76d1663bb28705e95034a", // Uniswap
                "1ae92b26df02f0abca6304df07debccd18262fdf5fe82daa81593582dac9a369", // Rainbow
                "ecc4036f814562b41a5268adc86270fba1365471402006302e70169465b7ac18", // Zerion
                "c286eebc742a537cd1d6818363e9dc53b21759a1e8e5d9b263d0c03ec7703576", // 1inch
                "ef333840daf915aafdc4a004525502d6d49d77bd9c65e0642dbaefb3c2893bef", // imToken
                "38f5d18bd8522c244bdd70cb4a68e0e718865155811c043f052fb9f1c51de662", // Bitget
                "0b415a746fb9ee99cce155c2ceca0c6f6061b1dbca2d722b3ba16381d0562150", // Safepal
                "15c8b91ade1a4e58f3ce4e7a0dd7f42b47db0c8df7e0d84f63eb39bcb96c4e0f", // Bybit
                "19177a98252e07ddfc9af2083ba8e07ef627cb6103467ffebb3f8f4205fd7927", // Ledger Live
                "344d0e58b139eb1b6da0c29ea71d52a8eace8b57897c6098cb9b46012665c193", // Timeless X
                "225affb176778569276e484e1b92637ad061b01e13a048b35a9d280c3b58970f", // Safe
                "f2436c67184f158d1beda5df53298ee84abfc367581e4505134b5bcf5f46697d", // Crypto.com
                "18450873727504ae9315a084fa7624b5297d2fe5880f0982979c17345a138277", // Kraken
                "541d5dcd4ede02f3afaf75bf8e3e4c4f1fb09edb5fa6c4377ebf31c2785d9adf", // Ronin
            ),
        )
    }
}

data class WalletSegueConfig(
    val callbackUrl: String
)

data class PhantomWalletConfig(
    val callbackUrl: String,
    val appUrl: String,
    val solanaMainnetUrl: String? = null,
    val solanaTestnetUrl: String? = null,
)
