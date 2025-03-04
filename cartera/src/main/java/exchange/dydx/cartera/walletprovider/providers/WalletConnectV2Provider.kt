package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.WalletConnectionType
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.entities.toJsonRequest
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.typeddata.typedDataAsString
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.WalletConnectCompletion
import exchange.dydx.cartera.walletprovider.WalletConnectedCompletion
import exchange.dydx.cartera.walletprovider.WalletError
import exchange.dydx.cartera.walletprovider.WalletInfo
import exchange.dydx.cartera.walletprovider.WalletOperationCompletion
import exchange.dydx.cartera.walletprovider.WalletOperationProviderProtocol
import exchange.dydx.cartera.walletprovider.WalletOperationStatus
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
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WalletConnectV2Provider(
    private val walletConnectV2Config: WalletConnectV2Config?,
    private val application: Application
) : WalletOperationProviderProtocol {
    private var _walletStatus = WalletStatusImp()
        set(value) {
            field = value
            walletStatusDelegate?.statusChanged(value)
        }
    override val walletStatus: WalletStatusProtocol
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private val connectCompletions: MutableList<WalletConnectCompletion> = mutableListOf()
    private val operationCompletions: MutableMap<String, WalletOperationCompletion> = mutableMapOf()

    private var requestingWallet: WalletRequest? = null
    private var currentSession: Sign.Model.ApprovedSession? = null

    private var currentPairing: Core.Model.Pairing? = null

    // expiry must be between current timestamp + MIN_INTERVAL and current timestamp + MAX_INTERVAL (MIN_INTERVAL: 300, MAX_INTERVAL: 604800)
    private val requestExpiry: Long
        get() = (System.currentTimeMillis() / 1000) + 400

    private val nilDelegate = object : SignClient.DappDelegate {
        override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
        }

        override fun onError(error: Sign.Model.Error) {
        }

        override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
        }

        override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
        }

        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
        }

        override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
        }

        override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
        }

        override fun onSessionExtend(session: Sign.Model.Session) {
        }

        override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
        }

        override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
        }

        override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
        }
    }

    private val dappDelegate = object : SignClient.DappDelegate {
        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
            // Triggered when Dapp receives the session approval from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionApproved")

            CoroutineScope(Dispatchers.Main).launch {
                val requestChainId = requestingWallet?.chainId
                val walletChainIds = approvedSession.chainIds() ?: emptyList()
                if (requestChainId != null && !walletChainIds.contains(requestChainId)) {
                    for (connectCompletion in connectCompletions) {
                        connectCompletion.invoke(
                            null,
                            WalletError(
                                code = CarteraErrorCode.WALLET_MISMATCH,
                                message = CarteraErrorCode.WALLET_MISMATCH.message,
                            ),
                        )
                    }
                    connectCompletions.clear()
                    return@launch
                }

                currentSession = approvedSession

                _walletStatus.state = WalletState.CONNECTED_TO_WALLET
                _walletStatus.connectedWallet = fromApprovedSession(approvedSession, requestingWallet?.wallet)

                for (connectCompletion in connectCompletions) {
                    connectCompletion.invoke(
                        _walletStatus.connectedWallet,
                        null,
                    )
                }
                connectCompletions.clear()

                walletStatusDelegate?.statusChanged(_walletStatus)
            }
        }

        override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
            // Triggered when Dapp receives the session rejection from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionRejected: $rejectedSession")

            CoroutineScope(Dispatchers.Main).launch {
                currentSession = null

                _walletStatus.state = WalletState.IDLE
                _walletStatus.connectedWallet = null

                for (connectCompletion in connectCompletions) {
                    connectCompletion.invoke(
                        null,
                        WalletError(
                            code = CarteraErrorCode.REFUSED_BY_WALLET,
                            message = rejectedSession.reason,
                        ),
                    )
                }
                connectCompletions.clear()

                walletStatusDelegate?.statusChanged(_walletStatus)
            }
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

            CoroutineScope(Dispatchers.Main).launch {
                currentSession = null
            }
        }

        override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
            // Triggered when Dapp receives the session request response from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionRequestResponse: $response")

            CoroutineScope(Dispatchers.Main).launch {
                val completion = operationCompletions[response.topic]
                if (completion != null) {
                    when (response.result) {
                        is Sign.Model.JsonRpcResponse.JsonRpcResult -> {
                            val result =
                                response.result as Sign.Model.JsonRpcResponse.JsonRpcResult
                            completion.invoke(
                                result.result,
                                null,
                            )
                        }

                        is Sign.Model.JsonRpcResponse.JsonRpcError -> {
                            val error =
                                response.result as Sign.Model.JsonRpcResponse.JsonRpcError
                            completion.invoke(
                                null,
                                WalletError(
                                    code = CarteraErrorCode.UNEXPECTED_RESPONSE,
                                    message = error.message,
                                ),
                            )
                        }
                    }

                    operationCompletions.remove(response.topic)
                }
            }
        }

        override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
            // Triggered whenever the connection state is changed
            Log.d(tag(this@WalletConnectV2Provider), "onConnectionStateChange: $state")
        }

        override fun onError(error: Sign.Model.Error) {
            // Triggered whenever there is an issue inside the SDK

            Log.d(tag(this@WalletConnectV2Provider), "onError: $error")
        }

        override fun onProposalExpired(proposal: Sign.Model.ExpiredProposal) {
            Log.d(tag(this@WalletConnectV2Provider), "onProposalExpired: $proposal")
        }

        override fun onRequestExpired(request: Sign.Model.ExpiredRequest) {
            Log.d(tag(this@WalletConnectV2Provider), "onRequestExpired: $request")
        }
    }

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
                redirect = "exchange.dydx.carteraexample",
            )

            CoreClient.initialize(
                metaData = metadata,
                relayServerUrl = serverUrl,
                connectionType = connectionType,
                application = application,
                onError = { error ->
                    Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                },
            )

            val init = Sign.Params.Init(core = CoreClient)

            SignClient.initialize(init) { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
            }
        }
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        if (_walletStatus.state == WalletState.CONNECTED_TO_WALLET) {
            completion(walletStatus.connectedWallet, null)
        } else {
            requestingWallet = request

            SignClient.setDappDelegate(dappDelegate)

            CoroutineScope(IO).launch {
                doConnect(request = request) { pairing, error ->
                    CoroutineScope(Dispatchers.Main).launch {
                        if (error != null) {
                            currentPairing = null
                            _walletStatus.connectedWallet = null
                            _walletStatus.connectionDeeplink = null
                            _walletStatus.state = WalletState.IDLE
                            walletStatusDelegate?.statusChanged(_walletStatus)
                            completion(null, error)
                        } else if (pairing != null) {
                            currentPairing = pairing
                            _walletStatus.state = WalletState.CONNECTED_TO_SERVER
                            if (request.wallet != null) {
                                _walletStatus.connectedWallet =
                                    fromPairing(pairing, request.wallet)
                            }
                            _walletStatus.connectionDeeplink =
                                pairing.uri.replace("wc:", "wc://")

                            walletStatusDelegate?.statusChanged(_walletStatus)

                            // let dappDelegate call the completion
                            connectCompletions.add(completion)
                        } else {
                            currentPairing = null
                            _walletStatus.state = WalletState.IDLE
                            _walletStatus.connectedWallet = null
                            walletStatusDelegate?.statusChanged(_walletStatus)
                            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED))
                        }
                    }
                }
            }
        }
    }

    override fun disconnect() {
        currentPairing?.let {
            CoreClient.Pairing.disconnect(Core.Params.Disconnect(it.topic)) { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
            }
            currentPairing = null
        }

        _walletStatus.state = WalletState.IDLE
        _walletStatus.connectedWallet = null
        _walletStatus.connectionDeeplink = null
        walletStatusDelegate?.statusChanged(_walletStatus)

        connectCompletions.clear()
        operationCompletions.clear()

        SignClient.setDappDelegate(nilDelegate)
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = request.chainId ?: currentSession?.chainId()
            return if (sessionTopic != null && account != null && namespace != null && chainId != null) {
                Sign.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "personal_sign",
                    params = "[\"${message}\", \"${account}\"]",
                    chainId = "$namespace:$chainId",
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, completion)
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = request.chainId ?: currentSession?.chainId()
            val message = typedDataProvider?.typedDataAsString?.replace("\"", "\\\"")

            return if (sessionTopic != null && account != null && namespace != null && chainId != null && message != null) {
                Sign.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "eth_signTypedData",
                    params = "[\"${account}\", \"${message}\"]",
                    chainId = "$namespace:$chainId",
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, completion)
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = request.walletRequest.chainId ?: currentSession?.chainId()
            val message = request.ethereum?.toJsonRequest()
            return if (sessionTopic != null && account != null && namespace != null && chainId != null && message != null) {
                Sign.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "eth_sendTransaction",
                    params = "[$message]",
                    chainId = "$namespace:$chainId",
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request.walletRequest, { requestParams() }, connected, completion)
    }

    override fun addChain(
        request: WalletRequest,
        chain: EthereumAddChainRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    private fun doConnect(request: WalletRequest, completion: (pairing: Core.Model.Pairing?, error: WalletError?) -> Unit) {
        val namespace: String = "eip155" /*Namespace identifier, see for reference: https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md#syntax*/
        val chain: String = if (request.chainId != null) {
            "eip155:${request.chainId}"
        } else {
            "eip155:5"
        }
        val chains: List<String> = listOf(chain)
        val methods: List<String> = listOf(
            "personal_sign",
            "eth_sendTransaction",
            "eth_signTypedData",
            //   "wallet_addEthereumChain"
        )
        val events: List<String> = listOf(
            "accountsChanged",
            "chainChanged",
        )
        val proposal = Sign.Model.Namespace.Proposal(chains, methods, events)
        val requiredNamespaces: Map<String, Sign.Model.Namespace.Proposal> = mapOf(namespace to proposal) /*Required namespaces to setup a session*/
        val optionalNamespaces: Map<String, Sign.Model.Namespace.Proposal> = emptyMap() /*Optional namespaces to setup a session*/

        val pairings = CoreClient.Pairing.getPairings()
        for (pairing in pairings) {
            CoreClient.Pairing.disconnect(pairing.topic)
        }
        val pairing = CoreClient.Pairing.create() { error ->
            Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
        }

        val expiry = (System.currentTimeMillis() / 1000) + TimeUnit.SECONDS.convert(7, TimeUnit.DAYS)
        val properties: Map<String, String> = mapOf("sessionExpiry" to "$expiry")

        if (pairing != null) {
            val connectParams =
                Sign.Params.Connect(
                    namespaces = requiredNamespaces,
                    optionalNamespaces = optionalNamespaces,
                    properties = properties,
                    pairing = pairing,
                )

            SignClient.connect(
                connect = connectParams,
                onSuccess = { value ->
                    Log.d(tag(this@WalletConnectV2Provider), "Connected to wallet: $value")
                    completion(pairing, null)
                },
                onError = { error: Sign.Model.Error ->
                    Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                    completion(
                        null,
                        WalletError(CarteraErrorCode.CONNECTION_FAILED, "SignClient.connect error", error.throwable.stackTraceToString()),
                    )
                },
            )
            openPeerDeeplink(request, pairing)
        }
    }

    private fun connectAndMakeRequest(
        request: WalletRequest,
        requestParams: (() -> Sign.Params.Request?),
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        connect(request) { info, error ->
            if (error != null) {
                completion(null, error)
            } else if (currentSession != null) {
                if (connected != null) {
                    connected(info)
                }

                val params = requestParams()
                if (params != null) {
                    reallyMakeRequest(request, params) { result, requestError ->
                        CoroutineScope(Dispatchers.Main).launch {
                            completion(result, requestError)
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        completion(null, WalletError(CarteraErrorCode.INVALID_SESSION))
                    }
                }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    completion(null, WalletError(CarteraErrorCode.INVALID_SESSION))
                }
            }
        }
    }

    private fun reallyMakeRequest(
        request: WalletRequest,
        requestParams: Sign.Params.Request,
        completion: WalletOperationCompletion
    ) {
        SignClient.request(
            request = requestParams,
            onSuccess = { sendRequest: Sign.Model.SentRequest ->
                Log.d(tag(this@WalletConnectV2Provider), "Wallet request made.")
                operationCompletions[sendRequest.sessionTopic] = completion
            },
            onError = { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                completion(
                    null,
                    WalletError(
                        CarteraErrorCode.CONNECTION_FAILED,
                        "SignClient.request error",
                        error.throwable.stackTraceToString(),
                    ),
                )
            },
        )

        openPeerDeeplink(request, currentPairing)
    }

    private fun fromPairing(pairing: Core.Model.Pairing, wallet: Wallet): WalletInfo {
        return WalletInfo(
            address = "address",
            chainId = "0",
            wallet = wallet,
            peerName = pairing.peerAppMetaData?.name,
            peerImageUrl = pairing.peerAppMetaData?.icons?.firstOrNull(),
        )
    }

    private fun fromApprovedSession(session: Sign.Model.ApprovedSession, wallet: Wallet?): WalletInfo {
        return WalletInfo(
            address = session.account(),
            chainId = session.chainId().toString(),
            wallet = wallet,
            peerName = session.metaData?.name,
            peerImageUrl = session.metaData?.icons?.firstOrNull(),
        )
    }

    private fun openPeerDeeplink(request: WalletRequest, pairing: Core.Model.Pairing?) {
        if (request.wallet == null) {
            Log.d(tag(this@WalletConnectV2Provider), "Wallet is null")
            return
        }
        if (pairing == null) {
            Log.d(tag(this@WalletConnectV2Provider), "Pairing is null")
            return
        }
        // val deeplinkPairingUri = it.replace("wc:", "wc://")
        val uri = WalletConnectUtils.createUrl(
            wallet = request.wallet,
            deeplink = pairing.uri,
            type = WalletConnectionType.WalletConnectV2,
            context = request.context,
        )
        if (uri != null) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                Log.d(tag(this@WalletConnectV2Provider), "There is no app to handle deep link")
            }
        } else {
            Log.d(tag(this@WalletConnectV2Provider), "Imvalid deeplink uri")
        }
    }
}

private fun Sign.Model.ApprovedSession.chainId(): Int? {
    val split = accounts.first().split(":")
    return if (split.count() > 1) {
        split[1].toInt()
    } else {
        null
    }
}

private fun Sign.Model.ApprovedSession.chainIds(): List<String>? {
    return accounts.mapNotNull {
        val split = it.split(":")
        if (split.count() > 1) {
            split[1]
        } else {
            null
        }
    }
}

private fun Sign.Model.ApprovedSession.namespace(): String? {
    val split = accounts.first().split(":")
    return if (split.count() > 0) {
        split[0]
    } else {
        null
    }
}

private fun Sign.Model.ApprovedSession.account(): String? {
    val split = accounts.first().split(":")
    return if (split.count() > 2) {
        split[2]
    } else {
        null
    }
}
