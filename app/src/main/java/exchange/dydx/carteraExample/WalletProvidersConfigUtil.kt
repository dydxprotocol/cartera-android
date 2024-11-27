package exchange.dydx.carteraexample

import exchange.dydx.cartera.WalletConnectV1Config
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.WalletProvidersConfig
import exchange.dydx.cartera.WalletSegueConfig

object WalletProvidersConfigUtil {
    fun getWalletProvidersConfig(): WalletProvidersConfig {
        val walletConnectV1Config = WalletConnectV1Config(
            clientName = "dYdX",
            clientDescription = "dYdX Trading App",
            iconUrl = "https://media.dydx.exchange/logos/dydx-x.png",
            scheme = "dydx:",
            clientUrl = "https://trade.dydx.exchange/",
            bridgeUrl = "wss://api.stage.dydx.exchange/wc/",
        )

        val walletConnectV2Config = WalletConnectV2Config(
            projectId = "156a34507d8e657347be0ecd294659bb",
            clientName = "dYdX",
            clientDescription = "dYdX Trading App",
            clientUrl = "https://trade.dydx.exchange/",
            iconUrls = listOf<String>("https://media.dydx.exchange/logos/dydx-x.png"),
        )

        val walletSegueConfig = WalletSegueConfig(
            callbackUrl = "https://trade.stage.dydx.exchange/walletsegueCarteraExample",
        )

        return WalletProvidersConfig(
            walletConnectV1Config,
            walletConnectV2Config,
            walletSegueConfig,
        )
    }
}
