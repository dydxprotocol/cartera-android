package exchange.dydx.carteraExample

import exchange.dydx.cartera.WalletConnectV1Config
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.WalletProvidersConfig
import exchange.dydx.cartera.WalletSegueConfig

class WalletProvidersConfigUtil {
    companion object {
        fun getConfig(): WalletProvidersConfig {
            val walletConnectV1Config = WalletConnectV1Config(
                "dYdX",
                "dYdX Trading App",
                "https://media.dydx.exchange/logos/dydx-x.png",
                "dydx:",
                "https://trade.dydx.exchange/",
                "wss://api.stage.dydx.exchange/wc/"
            )

            val walletConnectV2Config = WalletConnectV2Config(
                "47559b2ec96c09aed9ff2cb54a31ab0e",
                "dYdX",
                "dYdX Trading App",
                "https://trade.dydx.exchange/",
                listOf<String>("https://media.dydx.exchange/logos/dydx-x.png")
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
}