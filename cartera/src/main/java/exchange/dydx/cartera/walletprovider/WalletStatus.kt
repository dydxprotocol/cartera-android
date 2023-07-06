package exchange.dydx.cartera.walletprovider

import exchange.dydx.cartera.entities.Wallet

data class WalletInfo(
    val address: String? = null,
    val chainId: Int? = null,
    val wallet: Wallet? = null,
    val peerName: String? = null,
    val peerImageUrl: String? = null
)

enum class WalletState {
    IDLE,
    LISTENING,
    CONNECTED_TO_SERVER,
    CONNECTED_TO_WALLET
}

interface WalletStatusProtocol {
    val connectedWallet: WalletInfo?
    val state: WalletState
    val connectionDeeplink: String?
}

interface WalletStatusDelegate {
    fun statusChanged(status: WalletStatusProtocol)
}

interface WalletStatusProviding {
    val walletStatus: WalletStatusProtocol?
    var walletStatusDelegate: WalletStatusDelegate?
}

data class WalletStatusImp(
    override var connectedWallet: WalletInfo? = null,
    override var state: WalletState = WalletState.IDLE,
    override var connectionDeeplink: String? = null,
) : WalletStatusProtocol