package exchange.dydx.carteraExample

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import exchange.dydx.cartera.CarteraConfig
import exchange.dydx.cartera.entities.Wallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WalletList {
    enum class WalletAction {
        Connect, SignMessage, SignTypedData, SignTransaction
    }

    data class WalletListState(
        val wallets: List<Wallet> = listOf(),
        val walletAction: ((WalletAction) -> Unit)? = null
    )

    companion object {
        @OptIn(ExperimentalMaterialApi::class)
        @Composable
        fun Content() {
            val context = LocalContext.current
            val state = remember {
                val viewModel = WalletListViewModel(context)
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
            val contextForToast = LocalContext.current.applicationContext

            LazyColumn {
                items(items = viewState.wallets) { wallet ->
                    wallet.name?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        bottomSheetState.show()
                                    }
                                }
                        ) {
                            Text(
                                it,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .weight(1f, false)
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
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.Connect) },
                    modifier = buttonModifier)
                {
                    Text("Connect", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignMessage) },
                    modifier = buttonModifier)
                {
                    Text("Sign Personal Message", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignTypedData) },
                    modifier = buttonModifier)
                {
                    Text("Sign TypedData", style = buttonTextStyle)
                }
                TextButton(onClick = { viewState.walletAction?.invoke(WalletAction.SignTransaction) },
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

class WalletListViewModel(val context: Context): ViewModel() {
    var viewState: MutableState<WalletList.WalletListState> = mutableStateOf(WalletList.WalletListState(listOf()))

    init {
        viewModelScope.launch {
            CarteraConfig.shared = CarteraConfig(WalletProvidersConfigUtil.getConfig())
            CarteraConfig.shared.registerWallets(context)
            viewState.value = WalletList.WalletListState(CarteraConfig.shared.wallets,
                walletAction = { action ->
                when (action) {
                    WalletList.WalletAction.Connect -> {

                    }
                    WalletList.WalletAction.SignMessage -> {

                    }
                    WalletList.WalletAction.SignTypedData -> {

                    }
                    WalletList.WalletAction.SignTransaction -> {

                    }
                }
            })
        }
    }
}