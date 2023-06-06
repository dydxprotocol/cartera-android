package exchange.dydx.cartera.providers

import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.WalletConnectCompletion
import exchange.dydx.cartera.walletprovider.WalletConnectedCompletion
import exchange.dydx.cartera.walletprovider.WalletOperationCompletion
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol

class MagicLinkProvider: WalletOperationProviderProtocol {
    override val walletStatus: WalletStatusProtocol?
        get() = TODO("Not yet implemented")
    override var walletStatusDelegate: WalletStatusDelegate?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var userConsentDelegate: WalletUserConsentProtocol?
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        TODO("Not yet implemented")
    }

    override fun disconnect() {
        TODO("Not yet implemented")
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    override fun addChain(
        request: WalletRequest,
        chain: EthereumAddChainRequest,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }
}