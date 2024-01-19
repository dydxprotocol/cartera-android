package exchange.dydx.cartera.walletprovider

import exchange.dydx.cartera.CarteraErrorCode

data class WalletError(
    val code: CarteraErrorCode,
    val title: String? = null,
    val message: String? = null
)
