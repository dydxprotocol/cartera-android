package exchange.dydx.carteraexample

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.entities.installed
import exchange.dydx.cartera.entities.openPlayStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.glxn.qrgen.android.QRCode

object WalletList {
    enum class WalletAction {
        Connect, SignMessage, SignTypedData, SignTransaction, Disconnect
    }

    enum class QrCodeAction {
        V1, V2
    }

    data class WalletListState(
        val wallets: List<Wallet> = listOf(),
        var selectedWallet: Wallet? = null,
        val walletAction: ((WalletAction, Wallet?, Boolean, Boolean) -> Unit)? = null,
        val debugQRCodeAction: ((QrCodeAction, Boolean) -> Unit)? = null,
        val wcModalAction: (() -> Unit)? = null
    ) {
        var showingQrCodeState: Boolean by mutableStateOf(false)
        var deeplinkUri: String? by mutableStateOf(null)
        var showBottomSheet: Boolean by mutableStateOf(false)
        var useTestnet: Boolean by mutableStateOf(true)
        var useWcModal: Boolean by mutableStateOf(false)
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    @Composable
    fun Content() {
        val context = LocalContext.current
        val viewModel = remember {
            WalletListViewModel(context)
        }
        val state = viewModel.viewState

        val bottomSheetState =
            rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)

        val coroutineScope = rememberCoroutineScope()

        ModalBottomSheetLayout(
            sheetContent = {
                WalletActionSheetView(viewState = state.value)
            },
            sheetState = bottomSheetState,
        ) {
            WalletListContent(
                viewState = state.value,
                coroutineScope = coroutineScope,
                bottomSheetState = bottomSheetState,
            )
        }

        if (state.value.showingQrCodeState) {
            QrCodeDialog(uri = state.value.deeplinkUri) {
                state.value.showingQrCodeState = false
            }
        }

        if (state.value.showBottomSheet) {
            coroutineScope.launch {
                bottomSheetState.show()
            }
        } else {
            coroutineScope.launch {
                bottomSheetState.hide()
            }
        }
    }

    @Composable
    fun WalletListContent(
        viewState: WalletList.WalletListState,
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
                                if (wallet.installed(context)) {
                                    viewState.selectedWallet = wallet
                                    viewState.useWcModal = false
                                    coroutineScope.launch {
                                        bottomSheetState.show()
                                    }
                                } else {
                                    wallet.openPlayStore(context)
                                }
                            },
                    ) {
                        Text(
                            walletName,
                            modifier = Modifier
                                .padding(16.dp)
                                .weight(1f, false),
                            textAlign = TextAlign.Start,
                        )

                        val subText: String =
                            when (wallet.installed(context)) {
                                true -> "Installed"
                                false -> "Not Installed"
                            }
                        Text(
                            subText,
                            modifier = Modifier
                                .padding(14.dp)
                                .weight(1f, false),
                            textAlign = TextAlign.End,
                        )
                    }
                    Divider()
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Divider()
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // nav.openWalletConnectModal()
                            viewState.wcModalAction?.invoke()
                        },
                ) {
                    Text(
                        "WalletConnect Modal",
                        modifier = Modifier
                            .padding(16.dp)
                            .weight(1f, false),
                        textAlign = TextAlign.Start,
                    )
                }
                Divider()
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Divider()
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewState.debugQRCodeAction?.let {
                                it(WalletList.QrCodeAction.V2, viewState.useTestnet)
                            }
                        },
                ) {
                    Text(
                        "Debug QR Code",
                        modifier = Modifier
                            .padding(16.dp)
                            .weight(1f, false),
                        textAlign = TextAlign.Start,
                    )

                    Text(
                        "WalletConnectV2",
                        modifier = Modifier
                            .padding(14.dp)
                            .weight(1f, false),
                        textAlign = TextAlign.End,
                    )
                }
                Divider()
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    Text(
                        "Testnet:",
                        modifier = Modifier
                            .padding(16.dp)
                            .weight(1f, false),
                        textAlign = TextAlign.Start,
                    )

                    Switch(
                        checked = viewState.useTestnet,
                        onCheckedChange = { viewState.useTestnet = it },
                    )
                }
            }
        }
    }

    @Composable
    private fun WalletActionSheetView(viewState: WalletListState) {
        val buttonModifier = Modifier
            .padding(all = 15.dp)
            .fillMaxWidth()
        val buttonTextStyle = TextStyle(fontSize = 20.sp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Select Wallet Action to Test:", style = MaterialTheme.typography.h5)
            TextButton(
                onClick = {
                    viewState.walletAction?.invoke(
                        WalletAction.Connect,
                        viewState.selectedWallet,
                        viewState.useTestnet,
                        viewState.useWcModal,
                    )
                },
                modifier = buttonModifier,
            ) {
                Text("Connect", style = buttonTextStyle)
            }
            TextButton(
                onClick = {
                    viewState.walletAction?.invoke(
                        WalletAction.SignMessage,
                        viewState.selectedWallet,
                        viewState.useTestnet,
                        viewState.useWcModal,
                    )
                },
                modifier = buttonModifier,
            ) {
                Text("Sign Personal Message", style = buttonTextStyle)
            }
            TextButton(
                onClick = {
                    viewState.walletAction?.invoke(
                        WalletAction.SignTypedData,
                        viewState.selectedWallet,
                        viewState.useTestnet,
                        viewState.useWcModal,
                    )
                },
                modifier = buttonModifier,
            ) {
                Text("Sign TypedData", style = buttonTextStyle)
            }
            TextButton(
                onClick = {
                    viewState.walletAction?.invoke(
                        WalletAction.SignTransaction,
                        viewState.selectedWallet,
                        viewState.useTestnet,
                        viewState.useWcModal,
                    )
                },
                modifier = buttonModifier,
            ) {
                Text("Send Transaction", style = buttonTextStyle)
            }
            TextButton(
                onClick = {
                    viewState.walletAction?.invoke(
                        WalletAction.Disconnect,
                        viewState.selectedWallet,
                        viewState.useTestnet,
                        viewState.useWcModal,
                    )
                },
                modifier = buttonModifier,
            ) {
                Text("Disconnect", style = buttonTextStyle)
            }
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(
                onClick = {
                    viewState.showBottomSheet = false
                },
                modifier = buttonModifier,
            ) {
                Text("Cancel", style = buttonTextStyle)
            }
        }
    }

    @Composable
    private fun QrCodeDialog(
        uri: String?,
        onDismissRequest: () -> Unit
    ) {
        ModalTransitionDialog.ModalTransitionDialog(onDismissRequest = onDismissRequest) { modalTransitionDialogHelper ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            ) {
                Spacer(modifier = Modifier.size(32.dp))

                uri?.let {
                    val qr = QRCode.from(it)
                        .withSize(320, 320)
                        .bitmap()

                    Image(
                        bitmap = qr.asImageBitmap(),
                        alignment = Alignment.Center,
                        contentScale = ContentScale.Fit,
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f),
                    )

                    Text(it, modifier = Modifier.padding(16.dp))
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Button(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.black)),
                        onClick = modalTransitionDialogHelper::triggerAnimatedClose,
                    ) {
                        Text(text = "Close it", color = Color.White)
                    }
                }
            }
        }
    }
}
