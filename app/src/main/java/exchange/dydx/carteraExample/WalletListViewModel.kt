package exchange.dydx.carteraexample

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import exchange.dydx.cartera.CarteraConfig
import exchange.dydx.cartera.CarteraConstants
import exchange.dydx.cartera.CarteraProvider
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.solana.SolanaInteractor
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.typeddata.EIP712DomainTypedDataProvider
import exchange.dydx.cartera.typeddata.WalletTypedData
import exchange.dydx.cartera.walletprovider.EthereumTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
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
                walletAction = { action: WalletList.WalletAction, wallet: Wallet?, useTestnet: Boolean, useModal: Boolean ->
                    val chainId: String = if (useTestnet) {
                        CarteraConstants.testnetChainId
                    } else {
                        "1"
                    }
                    when (action) {
                        WalletList.WalletAction.Connect -> {
                            testConnect(wallet, chainId, useModal)
                        }

                        WalletList.WalletAction.SignMessage -> {
                            testSignMessage(wallet, chainId, useModal)
                        }

                        WalletList.WalletAction.SignTypedData -> {
                            testSignTypedData(wallet, chainId, useModal)
                        }

                        WalletList.WalletAction.SignTransaction -> {
                            testSendTransaction(wallet, chainId, useTestnet, useModal)
                        }

                        WalletList.WalletAction.Disconnect -> {
                            provider.disconnect()
                        }
                    }
                },
                debugQRCodeAction = { action: WalletList.QrCodeAction, useTestnet: Boolean ->
                    val chainId: String = if (useTestnet) CarteraConstants.testnetChainId else "1"
                    when (action) {
                        WalletList.QrCodeAction.V1 -> {
                            // TODO: testQRCodeV1()
                        }

                        WalletList.QrCodeAction.V2 -> {
                            testQRCodeV2(chainId)
                        }
                    }
                },
                wcModalAction = {
                    testWcModal()
                },
            )
        }
    }

    private fun testWcModal() {
        viewState.value.showingQrCodeState = false
        viewState.value.showBottomSheet = false
        viewModelScope.launch {
            viewState.value.showBottomSheet = true
            viewState.value.selectedWallet = null

            viewState.value.useWcModal = true
        }
    }

    private fun testQRCodeV2(chainId: String) {
        viewState.value.showingQrCodeState = true
        viewState.value.showBottomSheet = false
        viewState.value.selectedWallet = null

        viewState.value.useWcModal = false

        provider.startDebugLink(chainId = chainId) { _, error ->
            viewState.value.showingQrCodeState = false
            if (error != null) {
                toastWalletError(error)
            } else {
                viewState.value.showBottomSheet = true
            }
        }
    }

    private fun testConnect(wallet: Wallet?, chainId: String, useModal: Boolean) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context, useModal = useModal)
        provider.connect(request) { info, error ->
            if (error != null) {
                toastWalletError(error)
            } else {
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            }
        }
    }

    private fun testSignMessage(wallet: Wallet?, chainId: String, useModal: Boolean) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context, useModal = useModal)
        provider.signMessage(
            request = request,
            message = "Test Message",
            connected = { info ->
                Timber.tag(tag(this@WalletListViewModel))
                    .d("Connected to: ${info?.peerName ?: info?.address}")
            },
            status = { requireAppSwitching ->
                Timber.tag(tag(this@WalletListViewModel))
                    .d("Require app switching: $requireAppSwitching")
                toastMessage("Please switch to the wallet app")
            },
            completion = { signature, error ->
                // delay for 1 second, according to app switching
                Thread.sleep(1000)
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Signature: $signature")
                }
            },
        )
    }

    private fun testSignTypedData(wallet: Wallet?, chainId: String, useModal: Boolean) {
        val chainIdInt = chainId.toIntOrNull()
        if (chainIdInt == null) {
            toastMessage("Invalid chainId: $chainId, must be an integer")
            return
        }
        val dydxSign = EIP712DomainTypedDataProvider(name = "dYdX", chainId = chainIdInt)
        dydxSign.message = message(action = "Sample Action", chainId = chainIdInt)

        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context, useModal = useModal)
        provider.sign(
            request = request,
            typedDataProvider = dydxSign,
            connected = { info ->
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            },
            status = { requireAppSwitching ->
                Timber.tag(tag(this@WalletListViewModel))
                    .d("Require app switching: $requireAppSwitching")
                toastMessage("Please switch to the wallet app")
            },
            completion = { signature, error ->
                // delay for 1 second
                Thread.sleep(1000)
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Signature: $signature")
                }
            },
        )
    }

    private fun testSendTransaction(wallet: Wallet?, chainId: String, useTestnet: Boolean, useModal: Boolean) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = chainId, context = context, useModal = useModal)
        provider.connect(request) { info, error ->
            if (error != null) {
                toastWalletError(error)
            } else {
                val publicKey = info?.address
                if (wallet?.id == "phantom-wallet" && publicKey != null) {
                    val interactor = if (useTestnet) {
                        SolanaInteractor(SolanaInteractor.devnetUrl)
                    } else {
                        SolanaInteractor(SolanaInteractor.mainnetUrl)
                    }
                    val scope = CoroutineScope(Dispatchers.Unconfined)
                    scope.launch {
                        val response = interactor.getRecentBlockhash()
                        val blockhash = response?.value?.blockhash
                        if (blockhash != null) {
//                            val sss = interactor.getBalance(publicKey = publicKey)
//                            print(sss)
//                            val ttt = interactor.getTokenBalance(publicKey = publicKey, tokenAddress = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
//                            print(ttt)

                            val memoInstruction = interactor.buildTestMemoTransaction(address = SolanaPublicKey.from(publicKey), memo = "Hello, Solana!")
                            val memoTxMessage = Message.Builder()
                                .addInstruction(memoInstruction) // Pass in instruction from previous step
                                .setRecentBlockhash(blockhash)
                                .build()
                            val unsignedTx = Transaction(memoTxMessage)
                            val request =
                                WalletTransactionRequest(
                                    walletRequest = request,
                                    ethereum = null,
                                    solana = unsignedTx.serialize(),
                                )
                            CoroutineScope(Dispatchers.Main).launch {
                                doSendTransaction(request)
                            }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                toastMessage("Error fetching blockhash")
                            }
                        }
                    }
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
                        WalletTransactionRequest(
                            walletRequest = request,
                            ethereum = ethereumRequest,
                            solana = null,
                        )
                    doSendTransaction(request)
                }
            }
        }
    }

    private fun doSendTransaction(request: WalletTransactionRequest) {
        provider.send(
            request = request,
            connected = { info ->
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            },
            status = { requireAppSwitching ->
                Timber.tag(tag(this@WalletListViewModel))
                    .d("Require app switching: $requireAppSwitching")
                toastMessage("Please switch to the wallet app")
            },

            completion = { txHash, error ->
                // delay for 1 second
                Thread.sleep(1000)
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Transaction Hash: $txHash")
                }
            },
        )
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
        Toast.makeText(context, "${error.title}: ${error.message}", Toast.LENGTH_SHORT).show()
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
