package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.entities.Wallet
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WalletConnectV2Provider(
    private val walletConnectV2Config: WalletConnectV2Config?,
    private val application: Application
    ): WalletOperationProviderProtocol {
    private var _walletStatus = WalletStatusImp()
        set(value) {
            field = value
            walletStatusDelegate?.statusChanged(value)
        }

    private var requestingWallet: Wallet? = null

    override val walletStatus: WalletStatusProtocol?
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private val dappDelegate = object : SignClient.DappDelegate {
        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
            // Triggered when Dapp receives the session approval from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionApproved")
        }

        override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
            // Triggered when Dapp receives the session rejection from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionRejected: $rejectedSession")
        }

        override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
            // Triggered when Dapp receives the session update from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionUpdate")
        }

        override fun onSessionExtend(session: Sign.Model.Session) {
            // Triggered when Dapp receives the session extend from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionExtend")
        }

        override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
            // Triggered when the peer emits events that match the list of events agreed upon session settlement
            Log.d(tag(this@WalletConnectV2Provider), "onSessionEvent")
        }

        override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
            // Triggered when Dapp receives the session delete from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionDelete: $deletedSession")
        }

        override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
            // Triggered when Dapp receives the session request response from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionRequestResponse: $response")
        }

        override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
            //Triggered whenever the connection state is changed
            Log.d(tag(this@WalletConnectV2Provider), "onConnectionStateChange: $state")
        }

        override fun onError(error: Sign.Model.Error) {
            // Triggered whenever there is an issue inside the SDK
            Log.d(tag(this@WalletConnectV2Provider), "onError: $error")
        }
    }

    private var currentPairing: Core.Model.Pairing? = null

    private var connectCompletions: MutableList<WalletConnectCompletion> = mutableListOf()

    init {
        walletConnectV2Config?.let { walletConnectV2Config ->

            // Reference: https://docs.walletconnect.com/2.0/android/sign/dapp-usage

            val projectId = walletConnectV2Config.projectId
            val relayUrl = "relay.walletconnect.com"
            val serverUrl = "wss://$relayUrl?projectId=$projectId"
            val connectionType = ConnectionType.AUTOMATIC // or ConnectionType.MANUAL

            val metadata = Core.Model.AppMetaData(
                name = walletConnectV2Config.clientName,
                description = walletConnectV2Config.clientDescription,
                url = walletConnectV2Config.clientUrl,
                icons = walletConnectV2Config.iconUrls,
                redirect = "kotlin-dapp-wc:/request"
            )

            CoreClient.initialize(
                metaData = metadata,
                relayServerUrl = serverUrl,
                connectionType = connectionType,
                application = application,
                onError = { error ->
                    Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                }
            )

            val init = Sign.Params.Init(core = CoreClient)

            SignClient.initialize(init) { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
            }
        }
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        SignClient.setDappDelegate(dappDelegate)

        if (walletStatus?.connectedWallet != null) {
            completion(walletStatus?.connectedWallet, null)
        } else {
            requestingWallet = request.wallet
            connectCompletions.add(completion)

            CoroutineScope(IO).launch {
                doConnect(completion = { pairing, error ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (error != null) {
                            currentPairing = null
                            completion(null, error)
                        } else if (pairing != null && request.wallet != null) {
                            currentPairing = pairing
                            _walletStatus.connectedWallet = fromPairing(pairing, request.wallet)
                            completion(_walletStatus.connectedWallet, null)
                            // completion will be sent via dappDelegate
                        } else {
                            currentPairing = null
                            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED))
                        }
                    }
                })
            }
        }
    }

    override fun disconnect() {
        currentPairing?.let { it
            CoreClient.Pairing.disconnect(Core.Params.Disconnect(it.topic)) { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
            }
            currentPairing = null
        }
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        connect(request) { info, error ->
            if (error != null) {
                completion(null, error)
            } else {
                if (connected != null) {
                    connected(info)
                }

//                val requestParams = Sign.Params.Request(
//                    sessionTopic = requireNotNull(DappDelegate.selectedSessionTopic),
//                    method = "personal_sign",
//                    params = params, // stringified JSON
//                    chainId = "$parentChain:$chainId"
//                )
//
//                reallyMakeRequest(requestParams) { result, error ->
//                    if (error != null) {
//                        disconnect()
//                    }
//                    completion(result, error)
//                }
            }
        }
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

    private fun resetStates() {
        currentPairing = null
        requestingWallet = null
        _walletStatus.connectedWallet = null
        _walletStatus.state = WalletState.IDLE
        connectCompletions.clear()
    }

    private fun doConnect(completion: (pairing: Core.Model.Pairing?, error: WalletError?) -> Unit) {
        val namespace: String = "eip155" /*Namespace identifier, see for reference: https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md#syntax*/
        val chains: List<String> = requestingWallet?.chains ?: emptyList()
        val methods: List<String> = listOf(
            "eth_sendTransaction",
            "personal_sign",
            "eth_signTypedData",
            "wallet_addEthereumChain"
        )
        val events: List<String> = emptyList()
        val proposal = Sign.Model.Namespace.Proposal(chains, methods, events)
        val requiredNamespaces: Map<String, Sign.Model.Namespace.Proposal> = mapOf(namespace to proposal) /*Required namespaces to setup a session*/
        val optionalNamespaces: Map<String, Sign.Model.Namespace.Proposal> = emptyMap() /*Optional namespaces to setup a session*/

        val pairing: Core.Model.Pairing?
        val pairings = CoreClient.Pairing.getPairings()
        if (pairings.isNotEmpty()) {
            pairing = pairings.first()
        } else {
            pairing = CoreClient.Pairing.create() { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, "Pairing is null", error.throwable.stackTraceToString()))
            }
        }
        val expiry = (System.currentTimeMillis() / 1000) + TimeUnit.SECONDS.convert(7, TimeUnit.DAYS)
        val properties: Map<String, String> = mapOf("sessionExpiry" to "$expiry")

        if (pairing != null) {
            val connectParams =
                Sign.Params.Connect(
                    namespaces = requiredNamespaces,
                    optionalNamespaces = optionalNamespaces,
                    properties = properties,
                    pairing = pairing
                )

            SignClient.connect(
                connect = connectParams,
                onSuccess = {
                    Log.d(tag(this@WalletConnectV2Provider), "Connected to wallet")
                    completion(pairing, null)
                },
                onError = { error ->
                    currentPairing = null
                    Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                    completion(
                        null,
                        WalletError(CarteraErrorCode.CONNECTION_FAILED, "SignClient.connect error", error.throwable.stackTraceToString())
                    )
                }
            )
        }
    }

    private fun reallyMakeRequest(requestParams:  Sign.Params.Request, completion: WalletOperationCompletion) {
        SignClient.request(
            request = requestParams,
            onSuccess = { request: Sign.Model.SentRequest ->
                Log.d(tag(this@WalletConnectV2Provider), "Wallet request made.")
               // completion(request, null)
            },
            onError = { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                completion(
                    null,
                    WalletError(CarteraErrorCode.CONNECTION_FAILED, "SignClient.request error", error.throwable.stackTraceToString())
                )

            }
        )
    }

    private fun fromPairing(pairing: Core.Model.Pairing, wallet: Wallet): WalletInfo {
        return WalletInfo(
            address = "address",
            chainId = 0,
            wallet = wallet,
            peerName =  pairing.peerAppMetaData?.name,
            peerImageUrl = pairing.peerAppMetaData?.icons?.firstOrNull()
        )
    }

}
