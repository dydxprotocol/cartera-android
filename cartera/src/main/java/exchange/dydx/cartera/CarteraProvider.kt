package exchange.dydx.cartera

import android.content.Context
import exchange.dydx.cartera.entities.connectionType
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.WalletConnectCompletion
import exchange.dydx.cartera.walletprovider.WalletConnectedCompletion
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletOperationCompletion
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol
import exchange.dydx.cartera.walletprovider.WalletUserConsentStatus
import exchange.dydx.cartera.walletprovider.userconsent.SkippedWalletUserConsent

class CarteraProvider(private val context: Context): WalletOperationProviderProtocol {
    private var currentRequestHandler: WalletOperationProviderProtocol? = null

    fun startDebugLink(chainId: Int, completion: WalletConnectCompletion) {
        currentRequestHandler = CarteraConfig.shared?.getProvider(WalletConnectionType.WalletConnect)
        currentRequestHandler?.walletStatusDelegate = walletStatusDelegate
        currentRequestHandler?.connect(WalletRequest(null, null, chainId), completion)
    }

    // WalletOperationProviderProtocol

    override var walletStatus: WalletStatusProtocol? = currentRequestHandler?.walletStatus

    override var walletStatusDelegate: WalletStatusDelegate?
        get() = currentRequestHandler?.walletStatusDelegate
        set(value) {
            currentRequestHandler?.walletStatusDelegate = value
        }
    override var userConsentDelegate: exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol?
        get() = currentRequestHandler?.userConsentDelegate
        set(value) {
            currentRequestHandler?.userConsentDelegate = value
        }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        updateCurrentHandler(request)
        currentRequestHandler?.connect(request, completion)
    }

    override fun disconnect() {
        currentRequestHandler?.disconnect()
    }

    override fun signMessage(request: WalletRequest, message: String, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion) {
        updateCurrentHandler(request)
        currentRequestHandler?.signMessage(request, message, connected, completion)
    }

    override fun sign(request: WalletRequest, typedDataProvider: WalletTypedDataProviderProtocol?, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion) {
        updateCurrentHandler(request)
        currentRequestHandler?.sign(request, typedDataProvider, connected, completion)
    }

    override fun send(request: WalletTransactionRequest, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion) {
        updateCurrentHandler(request.walletRequest)
        userConsentDelegate?.showTransactionConsent(request) { status ->
            when (status) {
                WalletUserConsentStatus.CONSENTED -> currentRequestHandler?.send(request, connected, completion)
                WalletUserConsentStatus.REJECTED -> {
                val error = WalletError(CarteraErrorCode.USER_CANCELED, "User canceled")
                completion(null, error)
            }
            }
        }
    }

    override fun addChain(request: WalletRequest, chain: EthereumAddChainRequest, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion) {
        updateCurrentHandler(request)
        // Disregard chainId, since we don't want to check for chainId match here.
        val request = WalletRequest(request.wallet, null, null)
        currentRequestHandler?.addChain(request, chain, connected, completion)
    }

    // Private

    private fun updateCurrentHandler(request: WalletRequest) {
        val newHandler = request.wallet?.config?.connectionType(context)?.let {
            CarteraConfig.shared?.getProvider(it)
        } ?: run {
            CarteraConfig.shared?.getProvider(WalletConnectionType.WalletConnect) // Debug QR-Code
        }

        if (newHandler !== currentRequestHandler) {
            currentRequestHandler?.disconnect()
            currentRequestHandler?.walletStatusDelegate = null
            currentRequestHandler?.userConsentDelegate = null

            currentRequestHandler = newHandler
            userConsentDelegate = getUserActionDelegate(request)
            currentRequestHandler?.userConsentDelegate = userConsentDelegate
            currentRequestHandler?.walletStatusDelegate = walletStatusDelegate
        }

        if (request.wallet != currentRequestHandler?.walletStatus?.connectedWallet?.wallet) {
            currentRequestHandler?.disconnect()
        }
    }

    private fun getUserActionDelegate(request: WalletRequest): WalletUserConsentProtocol {
        val connectionType = request.wallet?.config?.connectionType(context)
        val userConsentHandler = if (connectionType != null) {
            CarteraConfig.shared?.getUserConsentHandler(connectionType)
        } else {
            null
        }

        return userConsentHandler ?: SkippedWalletUserConsent()
    }
}
