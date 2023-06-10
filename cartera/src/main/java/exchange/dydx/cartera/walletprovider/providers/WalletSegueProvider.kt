package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.coinbase.android.nativesdk.CoinbaseWalletSDK
import com.coinbase.android.nativesdk.message.request.Account
import com.coinbase.android.nativesdk.message.request.Action
import com.coinbase.android.nativesdk.message.request.Web3JsonRPC
import com.coinbase.android.nativesdk.message.response.ActionResult
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.WalletSegueConfig
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.WalletConnectCompletion
import exchange.dydx.cartera.walletprovider.WalletConnectedCompletion
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletInfo
import exchange.dydx.cartera.walletprovider.WalletOperationCompletion
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletState
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusImp
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol

class WalletSegueProvider(
    private val walletSegueConfig: WalletSegueConfig?,
    private val application: Application,
    private val launcher: ActivityResultLauncher<Intent>?
    ): WalletOperationProviderProtocol {

    private var _walletStatus = WalletStatusImp()
        set(value) {
            field = value
            walletStatusDelegate?.statusChanged(value)
        }

    override val walletStatus: WalletStatusProtocol?
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private var client: CoinbaseWalletSDK? = null
    private var account: Account? = null

    init {
        walletSegueConfig?.callbackUrl?.let { walletSegueCallbackUrl ->
            client = CoinbaseWalletSDK(
                appContext = application.applicationContext,
                domain = Uri.parse(walletSegueCallbackUrl),
                openIntent = { intent -> launcher?.launch(intent) }
            )
        }
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        if (walletStatus?.connectedWallet == null || client?.isConnected ?: false == false) {
            _walletStatus.state = WalletState.IDLE
        }
        val wallet = request.wallet
        val expected = WalletInfo(address = request.address, chainId = request.chainId, wallet = wallet)

        when (_walletStatus.state) {
            WalletState.IDLE -> {

                val requestAccount = Web3JsonRPC.RequestAccounts().action()
                client?.initiateHandshake(
                    initialActions = listOf(requestAccount)
                ) { result: Result<List<ActionResult>>, account: Account? ->
                    result.onSuccess { actionResults: List<ActionResult> ->
                        if (account != null) {
                            if (expected.chainId != null && account.networkId.toInt() != expected.chainId) {
                                val errorTitle = "Network Mismatch"
                                val errorMessage = if (expected.chainId == 1) {
                                    "Set your wallet network to 'Ethereum Mainnet'."
                                } else {
                                    "Set your wallet network to 'Goerli Test Network'."
                                }
                                completion(
                                    null,
                                    WalletError(
                                        code = CarteraErrorCode.NETWORK_MISMATCH,
                                        title = errorTitle,
                                        message = errorMessage
                                    )
                                )
                            } else if (expected.address != null && expected.address?.lowercase() != account.address.lowercase()) {
                                val errorTitle = "Wallet Mismatch"
                                val errorMessage =
                                    "Please switch your wallet to $expected.address"
                                completion(
                                    null,
                                    WalletError(
                                        code = CarteraErrorCode.WALLET_MISMATCH,
                                        title = errorTitle,
                                        message = errorMessage
                                    )
                                )
                            } else {
                                this?.account = account
                                this?._walletStatus?.connectedWallet = WalletInfo(
                                    address = account.address,
                                    chainId = expected.chainId,
                                    wallet = wallet
                                )
                                this?._walletStatus?.state = WalletState.CONNECTED_TO_WALLET
                                completion(this?._walletStatus?.connectedWallet, null)
                            }

                        } else {
                            completion(null, WalletError(CarteraErrorCode.WALLET_CONTAINS_NO_ACCOUNT, CarteraErrorCode.WALLET_CONTAINS_NO_ACCOUNT.message))
                        }
                    }
                    result.onFailure { err ->
                        completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, err.message))
                    }
                }
            }
            WalletState.LISTENING -> {
               Log.e(tag(this@WalletSegueProvider), "Invalid state")
            }
            WalletState.CONNECTED_TO_SERVER -> {
                completion(_walletStatus.connectedWallet, null)
            }
            WalletState.CONNECTED_TO_WALLET -> {
                completion(_walletStatus.connectedWallet, null)
            }
        }
    }

    override fun disconnect() {
        _walletStatus.state = WalletState.IDLE
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {

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

    fun handleResponse(url: Uri): Boolean {
        return client?.handleResponse(url) ?: false
    }

}