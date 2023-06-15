package exchange.dydx.carteraexample

import exchange.dydx.cartera.WalletConnectV1Config
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.WalletProvidersConfig
import exchange.dydx.cartera.WalletSegueConfig

object WalletProvidersConfigUtil {
    fun getWalletProvidersConfig(): WalletProvidersConfig {
        val walletConnectV1Config = WalletConnectV1Config(
            "dYdX",
            "dYdX Trading App",
            "https://media.dydx.exchange/logos/dydx-x.png",
            "dydx:",
            "https://trade.dydx.exchange/",
            "wss://api.stage.dydx.exchange/wc/"
        )

        val walletConnectV2Config = WalletConnectV2Config(
            "156a34507d8e657347be0ecd294659bb",
            "dYdX",
            "dYdX Trading App",
            "https://trade.dydx.exchange/",
            listOf<String>("https://media.dydx.exchange/logos/dydx-x.png"),
        )

        val walletSegueConfig = WalletSegueConfig(
            "https://trade.stage.dydx.exchange/walletsegueCarteraExample"
        )

        return WalletProvidersConfig(
            walletConnectV1Config,
            walletConnectV2Config,
            walletSegueConfig
        )
    }
}