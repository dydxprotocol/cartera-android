package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.push.common.Push
import com.walletconnect.push.dapp.client.PushDappClient
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.WalletConnectV2Config
import exchange.dydx.cartera.WalletConnectionType
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.toHexString
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.typeddata.typedDataAsString
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.EthereumTransactionRequest
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
import kotlinx.coroutines.launch
import okhttp3.internal.toHexString
import org.json.JSONException
import org.json.JSONObject
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
    override val walletStatus: WalletStatusProtocol?
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private var connectCompletions : MutableList<WalletConnectCompletion> = mutableListOf()
    private var operationCompletions: MutableMap<String, WalletOperationCompletion>  = mutableMapOf()

    private var requestingWallet: WalletRequest? = null
    private var currentSession: Sign.Model.ApprovedSession? = null

    private var currentPairing: Core.Model.Pairing? = null

    private val dappDelegate = object : SignClient.DappDelegate {
        override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
            // Triggered when Dapp receives the session approval from wallet
            Log.d(tag(this@WalletConnectV2Provider), "onSessionApproved")

            CoroutineScope(Dispatchers.Main).launch {
                if (requestingWallet?.chainId != null &&
                    approvedSession.chainId() != null &&
                    requestingWallet?.chainId != approvedSession.chainId()) {
                    for (connectCompletion in connectCompletions) {
                        connectCompletion.invoke(
                            null,
                            WalletError(
                                code = CarteraErrorCode.WALLET_MISMATCH,
                                message = CarteraErrorCode.WALLET_MISMATCH.message
                            )
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
                        null
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
                            message = rejectedSession.reason
                        )
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
                                null
                            )
                        }

                        is Sign.Model.JsonRpcResponse.JsonRpcError -> {
                            val error =
                                response.result as Sign.Model.JsonRpcResponse.JsonRpcError
                            completion.invoke(
                                null,
                                WalletError(
                                    code = CarteraErrorCode.UNEXPECTED_RESPONSE,
                                    message = error.message
                                )
                            )
                        }
                    }

                    operationCompletions.remove(response.topic)
                }
            }
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

             PushDappClient.initialize(Push.Dapp.Params.Init(CoreClient, null)) { error ->
                 Log.e(tag(this), error.throwable.stackTraceToString())
             }

             SignClient.setDappDelegate(dappDelegate)
         }
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        if ( _walletStatus.state == WalletState.CONNECTED_TO_WALLET) {
            completion(walletStatus?.connectedWallet, null)
        } else {
            requestingWallet = request
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
        currentPairing?.let { it
            CoreClient.Pairing.disconnect(Core.Params.Disconnect(it.topic)) { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
            }
            currentPairing = null
            _walletStatus.state = WalletState.IDLE
            _walletStatus.connectedWallet = null
            _walletStatus.connectionDeeplink = null
            walletStatusDelegate?.statusChanged(_walletStatus)

            connectCompletions.clear()
            operationCompletions.clear()
        }
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = currentSession?.chainId()
            return if (account != null && namespace != null && chainId != null) {
                return Sign.Params.Request(
                    sessionTopic = currentSession!!.topic,
                    method = "personal_sign",
                    params = "[\"${message}\", \"${account}\"]",
                    chainId = "${namespace}:${chainId}"
                )
            } else {
                return null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, completion)
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = currentSession?.chainId()
            val message = typedDataProvider?.typedDataAsString
            if (account != null && namespace != null && chainId != null && message != null) {
                return Sign.Params.Request(
                    sessionTopic = currentSession!!.topic,
                    method = "eth_signTypedData",
                    params = "[\"${account}\", ${message}]",
                    chainId = "${namespace}:${chainId}"
                )
            } else {
                return null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, completion)
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Sign.Params.Request? {
            val account = currentSession?.account()
            val namespace = currentSession?.namespace()
            val chainId = currentSession?.chainId()
            val message = request.ethereum?.toJsonRequest()
            if (account != null && namespace != null && chainId != null && message != null) {
                return Sign.Params.Request(
                    sessionTopic = currentSession!!.topic,
                    method = "eth_sendTransaction",
                    params = "[${message}]",
                    chainId = "${namespace}:${chainId}"
                )
            } else {
                return null
            }
        }

        connectAndMakeRequest(request.walletRequest, { requestParams() }, connected, completion)
    }

    override fun addChain(
        request: WalletRequest,
        chain: EthereumAddChainRequest,
        connected: WalletConnectedCompletion?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    private fun doConnect(request: WalletRequest, completion: (pairing: Core.Model.Pairing?, error: WalletError?) -> Unit) {
        val namespace: String = "eip155" /*Namespace identifier, see for reference: https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md#syntax*/
        val chain: String =  if (request.chainId != null) {
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
        val events: List<String> =  listOf(
            "accountsChanged",
            "chainChanged"
        )
        val proposal = Sign.Model.Namespace.Proposal(chains, methods, events)
        val requiredNamespaces: Map<String, Sign.Model.Namespace.Proposal> = mapOf(namespace to proposal) /*Required namespaces to setup a session*/
        val optionalNamespaces: Map<String, Sign.Model.Namespace.Proposal> = emptyMap() /*Optional namespaces to setup a session*/
//
//        val pairing: Core.Model.Pairing?
//        val pairings = CoreClient.Pairing.getPairings()
//        if (pairings.isNotEmpty()) {
//            pairing = pairings.first()
//        } else {
//            pairing = CoreClient.Pairing.create() { error ->
//                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
//            }!!
//        }

        val pairing = CoreClient.Pairing.create()

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
                    Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                    completion(
                        null,
                        WalletError(CarteraErrorCode.CONNECTION_FAILED, "SignClient.connect error", error.throwable.stackTraceToString())
                    )
                }
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
                    reallyMakeRequest(request, params) { result, error ->
                        completion(result, error)
                    }
                } else {
                    completion(null, WalletError(CarteraErrorCode.INVALID_SESSION))
                }
            } else {
                completion(null, WalletError(CarteraErrorCode.INVALID_SESSION))
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
            onSuccess = { request: Sign.Model.SentRequest ->
                Log.d(tag(this@WalletConnectV2Provider), "Wallet request made.")
                operationCompletions[request.sessionTopic] = completion
            },
            onError = { error ->
                Log.e(tag(this@WalletConnectV2Provider), error.throwable.stackTraceToString())
                completion(
                    null,
                    WalletError(
                        CarteraErrorCode.CONNECTION_FAILED,
                        "SignClient.request error",
                        error.throwable.stackTraceToString()
                    )
                )
            }
        )

        openPeerDeeplink(request, currentPairing)
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

    private fun fromApprovedSession(session: Sign.Model.ApprovedSession, wallet: Wallet?): WalletInfo {
        return WalletInfo(
            address = session.account(),
            chainId = session.chainId(),
            wallet = wallet,
            peerName =  session.metaData?.name,
            peerImageUrl = session.metaData?.icons?.firstOrNull()
        )
    }

    private fun openPeerDeeplink(request: WalletRequest, pairing: Core.Model.Pairing?) {
        if (request.wallet == null) {
            Log.d(tag(this@WalletConnectV2Provider), "Wallet is null")
            return
        }
        if (pairing?.uri == null) {
            Log.d(tag(this@WalletConnectV2Provider), "Pairing is null")
            return
        }
        // val deeplinkPairingUri = it.replace("wc:", "wc://")
        val url = WalletConnectUtils.createUrl(
            wallet = request.wallet,
            deeplink = pairing?.uri,
            type = WalletConnectionType.WalletConnectV2,
            context = request.context
        )
        val deeplinkPairingUri = url?.toURI()?.toString()
        if (deeplinkPairingUri != null) {
            try {
                val uri = Uri.parse(deeplinkPairingUri)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                Log.d(tag(this@WalletConnectV2Provider), "There is no app to handle deep linkt")
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

private fun EthereumTransactionRequest.toJsonRequest(): String? {
    var request: MutableMap<String, Any?> = mutableMapOf()

    request["from"] = fromAddress
    request["to"] = toAddress ?: "0x"
    request["gas"] = gasLimit?.toHexString()
    request["gasPrice"] = gasPriceInWei?.toHexString()
    request["value"] = weiValue.toHexString()
    request["data"] = data
    request["nonce"] = nonce?.let {
        "0x" + it.toHexString()
    }
    val filtered = request.filterValues { it != null }

    return try {
        JSONObject(filtered as Map<*, *>?).toString()
    } catch (e: JSONException) {
        null
    }
}
