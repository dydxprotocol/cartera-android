package exchange.dydx.carteraexample

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import exchange.dydx.cartera.CarteraConfig
import exchange.dydx.cartera.CarteraProvider
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.typeddata.EIP712DomainTypedDataProvider
import exchange.dydx.cartera.typeddata.WalletTypedData
import exchange.dydx.cartera.walletprovider.EthereumTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import kotlinx.coroutines.launch
import java.math.BigInteger

class WalletListViewModel(
    private val context: Context
) : ViewModel(), WalletStatusDelegate {
    var viewState: MutableState<WalletList.WalletListState> =
        mutableStateOf(WalletList.WalletListState(listOf()))

    private val provider: CarteraProvider by lazy {
        val provider = CarteraProvider(context)
        provider.walletStatusDelegate = this
        provider
    }

    init {
        viewModelScope.launch {
            viewState.value = WalletList.WalletListState(
                wallets = CarteraConfig.shared?.wallets ?: listOf(),
                walletAction = { action: WalletList.WalletAction, wallet: Wallet?, useTestnet: Boolean ->
                    val chainId: Int = if (useTestnet) 5 else 1
                    when (action) {
                        WalletList.WalletAction.Connect -> {
                            testConnect(wallet, chainId)
                        }

                        WalletList.WalletAction.SignMessage -> {
                            testSignMessage(wallet, chainId)
                        }

                        WalletList.WalletAction.SignTypedData -> {
                            testSignTypedData(wallet, chainId)
                        }

                        WalletList.WalletAction.SignTransaction -> {
                            testSendTransaction(wallet, chainId)
                        }
                    }
                },
                debugQRCodeAction = { action: WalletList.QrCodeAction, useTestnet: Boolean ->
                    val chainId: Int = if (useTestnet) 5 else 1
                    when (action) {
                        WalletList.QrCodeAction.V1 -> {
                            // TODO: testQRCodeV1()
                        }

                        WalletList.QrCodeAction.V2 -> {
                            testQRCodeV2(chainId)
                        }
                    }
                },
            )
        }
    }

    private fun testQRCodeV2(chainId: Int) {
        viewState.value.showingQrCodeState = true
        viewState.value.showBottomSheet = false
        viewState.value.selectedWallet = null

        provider.startDebugLink(chainId = chainId) { _, error ->
            viewState.value.showingQrCodeState = false
            if (error != null) {
                toastWalletError(error)
            } else {
                viewState.value.showBottomSheet = true
            }
        }
    }

    private fun testConnect(wallet: Wallet?, chainId: Int) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context)
        provider.connect(request) { info, error ->
            if (error != null) {
                toastWalletError(error)
            } else {
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            }
        }
    }

    private fun testSignMessage(wallet: Wallet?, chainId: Int) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context)
        provider.signMessage(
            request = request,
            message = "Test Message",
            connected = { info ->
                Log.d(tag(this@WalletListViewModel), "Connected to: ${info?.peerName ?: info?.address}")
            },
            completion = { signature, error ->
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Signature: $signature")
                }
            },
        )
    }

    private fun testSignTypedData(wallet: Wallet?, chainId: Int) {
        val dydxSign = EIP712DomainTypedDataProvider(name = "dYdX", chainId = chainId)
        dydxSign.message = message(action = "Sample Action", chainId = chainId)

        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context)
        provider.sign(
            request = request,
            typedDataProvider = dydxSign,
            connected = { info ->
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            },
            completion = { signature, error ->
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Signature: $signature")
                }
            },
        )
    }

    private fun testSendTransaction(wallet: Wallet?, chainId: Int) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context)
        provider.connect(request) { info, error ->
            if (error != null) {
                toastWalletError(error)
            } else {
                val ethereumRequest = EthereumTransactionRequest(
                    fromAddress = info?.address ?: "0x00",
                    toAddress = "0x0000000000000000000000000000000000000000",
                    weiValue = BigInteger("1"),
                    data = "0x",
                    nonce = null,
                    gasPriceInWei = BigInteger("100000000"),
                    maxFeePerGas = null,
                    maxPriorityFeePerGas = null,
                    gasLimit = BigInteger("21000"),
                    chainId = chainId.toString(),
                )
                val request =
                    WalletTransactionRequest(walletRequest = request, ethereum = ethereumRequest)
                provider.send(
                    request = request,
                    connected = { info ->
                        toastMessage("Connected to: ${info?.peerName ?: info?.address}")
                    },
                    completion = { txHash, error ->
                        if (error != null) {
                            toastWalletError(error)
                        } else {
                            toastMessage("Transaction Hash: $txHash")
                        }
                    },
                )
            }
        }
    }

    override fun statusChanged(status: WalletStatusProtocol) {
        status.connectedWallet?.address?.let {
            toastMessage("Connected to: $it")
        }
        viewState.value.deeplinkUri = status.connectionDeeplink?.replace("wc://", "wc:")
    }

    private fun toastMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun toastWalletError(error: WalletError) {
        Toast.makeText(context, "$error.title: $error.message", Toast.LENGTH_SHORT).show()
    }

    private fun message(action: String, chainId: Int): WalletTypedData {
        val definitions = mutableListOf<Map<String, String>>()
        val data = mutableMapOf<String, Any>()
        definitions.add(type(name = "action", type = "string"))
        data["action"] = action
        if (chainId == 1) {
            definitions.add(type(name = "onlySignOn", type = "string"))
            data["onlySignOn"] = "https://trade.dydx.exchange"
        }

        val message = WalletTypedData(typeName = "dYdX")
        message.definitions = definitions
        message.data = data
        return message
    }

    private fun type(name: String, type: String): Map<String, String> {
        return mapOf("name" to name, "type" to type)
    }
}
