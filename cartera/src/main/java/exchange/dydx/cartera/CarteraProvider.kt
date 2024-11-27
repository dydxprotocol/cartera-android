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
import exchange.dydx.cartera.walletprovider.WalletOperationStatus
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol
import exchange.dydx.cartera.walletprovider.WalletUserConsentStatus
import exchange.dydx.cartera.walletprovider.userconsent.SkippedWalletUserConsent

class CarteraProvider(private val context: Context) : WalletOperationProviderProtocol {
    private var currentRequestHandler: WalletOperationProviderProtocol? = null

    private val debugQrCodeProvider = CarteraConfig.shared?.getProvider(WalletConnectionType.WalletConnectV2)

    fun startDebugLink(chainId: String, completion: WalletConnectCompletion) {
        updateCurrentHandler(WalletRequest(null, null, chainId, context))
        currentRequestHandler?.connect(WalletRequest(null, null, chainId, context), completion)
    }

    // WalletOperationProviderProtocol

    override val walletStatus: WalletStatusProtocol?
        get() = currentRequestHandler?.walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
        set(value) {
            currentRequestHandler?.walletStatusDelegate = value
            field = value
        }
    override var userConsentDelegate: exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol? = null
        set(value) {
            currentRequestHandler?.userConsentDelegate = value
            field = value
        }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        updateCurrentHandler(request)
        currentRequestHandler?.connect(request, completion)
    }

    override fun disconnect() {
        currentRequestHandler?.disconnect()
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        updateCurrentHandler(request)
        currentRequestHandler?.signMessage(request, message, connected, status, completion)
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        updateCurrentHandler(request)
        currentRequestHandler?.sign(request, typedDataProvider, connected, status, completion)
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        updateCurrentHandler(request.walletRequest)
        userConsentDelegate?.showTransactionConsent(request) { consentStatus ->
            when (consentStatus) {
                WalletUserConsentStatus.CONSENTED -> currentRequestHandler?.send(request, connected, status, completion)
                WalletUserConsentStatus.REJECTED -> {
                    val error = WalletError(CarteraErrorCode.USER_CANCELED, "User canceled")
                    completion(null, error)
                }
            }
        }
    }

    override fun addChain(
        request: WalletRequest,
        chain: EthereumAddChainRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        updateCurrentHandler(request)
        // Disregard chainId, since we don't want to check for chainId match here.
        val addChainRequest = WalletRequest(request.wallet, null, null, context)
        currentRequestHandler?.addChain(addChainRequest, chain, connected, status, completion)
    }

    // Private

    private fun updateCurrentHandler(request: WalletRequest) {
        val provider = if (request.useModal) {
            CarteraConfig.shared?.getProvider(WalletConnectionType.WalletConnectModal)
        } else {
            request.wallet?.config?.connectionType(context)?.let {
                CarteraConfig.shared?.getProvider(it)
            }
        }

        val newHandler = provider ?: debugQrCodeProvider

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
