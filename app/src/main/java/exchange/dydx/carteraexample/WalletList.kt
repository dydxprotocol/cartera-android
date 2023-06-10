package exchange.dydx.carteraexample

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import exchange.dydx.cartera.CarteraConfig
import exchange.dydx.cartera.CarteraProvider
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.entities.connections
import exchange.dydx.cartera.entities.installed
import exchange.dydx.cartera.entities.openPlayStore
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletInfo
import exchange.dydx.cartera.walletprovider.WalletRequest
import exchange.dydx.cartera.walletprovider.WalletStatusDelegate
import exchange.dydx.cartera.walletprovider.WalletStatusProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WalletList {
    enum class WalletAction {
        Connect, SignMessage, SignTypedData, SignTransaction
    }

    data class WalletListState(
        val wallets: List<Wallet> = listOf(),
        var selectedWallet: Wallet? = null,
        val walletAction: ((WalletAction, Wallet?) -> Unit)? = null
    )

    companion object {
        @OptIn(ExperimentalMaterialApi::class)
        @Composable
        fun Content(launcher: ActivityResultLauncher<Intent>) {
            val context = LocalContext.current
            val state = remember {
                val viewModel = WalletListViewModel(context, launcher)
                viewModel.viewState
            }

            val bottomSheetState =
                rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)

            val coroutineScope = rememberCoroutineScope()

            ModalBottomSheetLayout(
                sheetContent = {
                    walletActionSheetView(viewState = state.value)
                },
                sheetState = bottomSheetState,
            ) {
                walletListContent(viewState = state.value,
                    coroutineScope = coroutineScope,
                    bottomSheetState = bottomSheetState)
            }
        }

        @OptIn(ExperimentalMaterialApi::class)
        @Composable
        fun walletListContent(viewState: WalletListState,
                              coroutineScope: CoroutineScope,
                              bottomSheetState: ModalBottomSheetState
        ) {
            val context = LocalContext.current
            LazyColumn {
                items(items = viewState.wallets) { wallet ->
                    wallet.name?.let { walletName ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (wallet.installed(context) ?: false) {
                                        viewState.selectedWallet = wallet
                                        coroutineScope.launch {
                                            bottomSheetState.show()
                                        }
                                    } else {
                                        wallet.openPlayStore(context)
                                    }
                                }
                        ) {
                            Text(
                                walletName,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .weight(1f, false),
                                textAlign = TextAlign.Start
                            )

                            val subText: String =
                                when (wallet.installed(context)) {
                                    true -> "Installed"
                                    false -> "Not Installed"
                                    null -> ""
                                }
                            Text(
                                subText,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .weight(1f, false),
                                textAlign = TextAlign.End
                            )
                        }
                        Divider()
                    }
                }
            }
        }

        @Composable
        fun walletActionSheetView(viewState: WalletListState) {
            val buttonModifier = Modifier
                .padding(all = 15.dp)
                .fillMaxWidth()
            val buttonTextStyle = TextStyle(fontSize = 20.sp)

            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth())
            {
                Text("Select Wallet Action to Test:", style = MaterialTheme.typography.h5)
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.Connect, viewState.selectedWallet) },
                    modifier = buttonModifier)
                {
                    Text("Connect", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignMessage, viewState.selectedWallet) },
                    modifier = buttonModifier)
                {
                    Text("Sign Personal Message", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignTypedData, viewState.selectedWallet) },
                    modifier = buttonModifier)
                {
                    Text("Sign TypedData", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignTransaction, viewState.selectedWallet) },
                    modifier = buttonModifier)
                {
                    Text("Sign Transaction", style = buttonTextStyle)
                }
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(onClick = { /* Handle click */ },
                    modifier = buttonModifier)
                {
                    Text("Cancel", style = buttonTextStyle)
                }
            }
        }
    }
}

class WalletListViewModel(
    private val context: Context,
    private val launcher: ActivityResultLauncher<Intent>
    ): ViewModel(), WalletStatusDelegate {
    var viewState: MutableState<WalletList.WalletListState> =
        mutableStateOf(WalletList.WalletListState(listOf()))

    private val provider: CarteraProvider by lazy {
        val provider = CarteraProvider(context)
        provider.walletStatusDelegate = this
        provider
    }

    init {
        viewModelScope.launch {
            CarteraConfig.shared = CarteraConfig(
                walletProvidersConfig = WalletProvidersConfigUtil.getWalletProvidersConfig(),
                application = context.applicationContext as Application,
                launcher = launcher
            )
            CarteraConfig.shared?.registerWallets(context)
            viewState.value = WalletList.WalletListState(
                wallets = CarteraConfig.shared?.wallets ?: listOf(),
                walletAction = { action: WalletList.WalletAction, wallet: Wallet? ->
                    when (action) {
                        WalletList.WalletAction.Connect -> {
                            if (wallet != null) {
                                testConnect(wallet)
                            }
                        }

                        WalletList.WalletAction.SignMessage -> {
                            if (wallet != null) {
                                testSignMessage(wallet)
                            }
                        }

                        WalletList.WalletAction.SignTypedData -> {
                            if (wallet != null) {
                                testSignTypedData(wallet)
                            }
                        }

                        WalletList.WalletAction.SignTransaction -> {
                            if (wallet != null) {
                                testSendTransaction(wallet)
                            }
                        }
                    }
                }
            )
        }
    }

    private fun testConnect(wallet: Wallet) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = 5)
        provider.connect(request) { info, error ->
            if (error != null) {
                toastWalletError(error)
            } else {
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            }
        }
    }

    private fun testSignMessage(wallet: Wallet) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = 5)
        provider.signMessage(request = request,
            message = "Test Message",
            connected = { info ->
                toastMessage("Connected to: ${info?.peerName ?: info?.address}")
            },
            completion = { signature, error ->
                if (error != null) {
                    toastWalletError(error)
                } else {
                    toastMessage("Signature: $signature")
                }
            }
        )
    }

    private fun testSignTypedData(wallet: Wallet) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = 5)
    }

    private fun testSendTransaction(wallet: Wallet) {
        val request = WalletRequest(wallet = wallet, address = null, chainId = 5)
    }

    override fun statusChanged(status: WalletStatusProtocol) {
       // TODO("Not yet implemented")
    }

    private fun toastMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun toastWalletError(error: WalletError) {
        Toast.makeText(context, "$error.title: $error.message", Toast.LENGTH_SHORT).show()
    }
}