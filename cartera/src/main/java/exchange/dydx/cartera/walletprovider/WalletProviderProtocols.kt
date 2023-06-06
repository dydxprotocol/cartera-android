package exchange.dydx.cartera.walletprovider

import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol

data class WalletRequest(
    val wallet: Wallet? = null,
    val address: String? = null,
    val chainId: Int? = null,
)

data class WalletTransactionRequest(
    val walletRequest: WalletRequest,
 //   val ethereum: EthereumTransactionRequest?
) {
  //  constructor(walletRequest: WalletRequest, ethereum: EthereumTransactionRequest?) : this(walletRequest, ethereum)
}

data class EthereumAddChainRequest(
    val chainId: String,
)

typealias WalletConnectedCompletion = (info: WalletInfo?) -> Unit
typealias WalletOperationCompletion = (signed: String?, error: Error?) -> Unit
typealias WalletConnectCompletion = (info: WalletInfo?, error: Error?) -> Unit

interface WalletOperationProtocol {
    fun connect(request: WalletRequest, completion: WalletConnectCompletion)
    fun disconnect()
    fun signMessage(request: WalletRequest, message: String, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion)
    fun sign(request: WalletRequest, typedDataProvider: WalletTypedDataProviderProtocol?, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion)
    fun send(request: WalletTransactionRequest, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion)
    fun addChain(request: WalletRequest, chain: EthereumAddChainRequest, connected: WalletConnectedCompletion?, completion: WalletOperationCompletion)
}

interface WalletUserConsentOperationProtocol : WalletOperationProtocol {
    var userConsentDelegate: WalletUserConsentProtocol?
}

interface WalletOperationProviderProtocol : WalletStatusProviding, WalletUserConsentOperationProtocol