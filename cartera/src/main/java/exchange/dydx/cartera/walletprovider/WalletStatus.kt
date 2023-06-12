package exchange.dydx.cartera.walletprovider

import exchange.dydx.cartera.entities.Wallet

data class WalletInfo(
    var address: String? = null,
    var chainId: Int? = null,
    var wallet: Wallet? = null,
    var peerName: String? = null,
    var peerImageUrl: String? = null
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