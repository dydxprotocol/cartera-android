package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.walletconnect.android.CoreClient
import com.walletconnect.wcmodal.client.Modal
import com.walletconnect.wcmodal.client.WalletConnectModal
import com.walletconnect.wcmodal.ui.openWalletConnectModal
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.R
import exchange.dydx.cartera.WalletConnectModalConfig
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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.reflect.Type

class WalletConnectModalProvider(
    private val application: Application,
    private val config: WalletConnectModalConfig?,
) : WalletOperationProviderProtocol, WalletConnectModal.ModalDelegate {

    private var _walletStatus = WalletStatusImp()
        set(value) {
            field = value
            walletStatusDelegate?.statusChanged(value)
        }
    override val walletStatus: WalletStatusProtocol
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private var requestingWallet: WalletRequest? = null
    private var currentSession: Modal.Model.ApprovedSession? = null

    private val connectCompletions: MutableList<WalletConnectCompletion> = mutableListOf()
    private val operationCompletions: MutableMap<String, WalletOperationCompletion> = mutableMapOf()

    // expiry must be between current timestamp + MIN_INTERVAL and current timestamp + MAX_INTERVAL (MIN_INTERVAL: 300, MAX_INTERVAL: 604800)
    private val requestExpiry: Long
        get() = (System.currentTimeMillis() / 1000) + 400

    private val ethNamespace = "eip155" // only support eth for now

    var nav: NavHostController? = null

    init {
        val jsonData = application.getResources().openRawResource(R.raw.wc_modal_ids)
            .bufferedReader().use { it.readText() }
        val gson = Gson()
        val idListType: Type = object : TypeToken<List<String>?>() {}.type
        val wc_modal_ids: List<String>? = gson.fromJson(jsonData, idListType)
        val excludedIds = wc_modal_ids?.toMutableList() ?: mutableListOf()
        for (id in config?.walletIds ?: emptyList()) {
            if (excludedIds.contains(id)) {
                excludedIds.remove(id)
            }
        }
        WalletConnectModal.initialize(
            init = Modal.Params.Init(
                core = CoreClient,
           //     recommendedWalletsIds = config?.walletIds ?: emptyList(),
           //     excludedWalletIds = excludedIds,
            ),
            onSuccess = {
                // Callback will be called if initialization is successful
                Timber.tag(tag(this)).d("WalletConnectModal initialized.")
            },
            onError = { error ->
                // Error will be thrown if there's an issue during initialization
                Timber.tag(tag(this))
                    .e(error.throwable.stackTraceToString())
            },
        )
    }

    override fun handleResponse(uri: Uri): Boolean {
        return false
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        if (_walletStatus.state == WalletState.CONNECTED_TO_WALLET) {
            completion(walletStatus.connectedWallet, null)
        } else {
            requestingWallet = request

            val chain: String = if (request.chainId != null) {
                "$ethNamespace:${request.chainId}"
            } else {
                "$ethNamespace:1"
            }
            val chains: List<String> = listOf(chain)
            val methods: List<String> = listOf(
                "personal_sign",
                "eth_sendTransaction",
                "eth_signTypedData",
                //   "wallet_addEthereumChain",
                // "eth_sign"
            )
            val events: List<String> = listOf(
                "accountsChanged",
                "chainChanged",
            )
            val namespaces = mapOf(
                ethNamespace to Modal.Model.Namespace.Proposal(
                    chains = chains,
                    methods = methods,
                    events = events,
                ),
            )

            val sessionParams = Modal.Params.SessionParams(
                requiredNamespaces = namespaces,
                optionalNamespaces = null,
                properties = null,
            )

            WalletConnectModal.setSessionParams(sessionParams)

            WalletConnectModal.setDelegate(this)

            connectCompletions.add(completion)

            nav?.openWalletConnectModal()
        }
    }

    override fun disconnect() {
        val currentSession = this.currentSession
        if (currentSession != null) {
            WalletConnectModal.disconnect(Modal.Params.Disconnect(currentSession.topic), onSuccess = {
                Timber.tag(tag(this)).d("Disconnected from session: ${currentSession!!.topic}")
            }, onError = {
                Timber.tag(tag(this)).e(it.throwable.stackTraceToString())
            })
            this.currentSession = null
        }

        _walletStatus.state = WalletState.IDLE
        _walletStatus.connectedWallet = null
        _walletStatus.connectionDeeplink = null
        walletStatusDelegate?.statusChanged(_walletStatus)

        connectCompletions.clear()
        operationCompletions.clear()
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Modal.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = _walletStatus.connectedWallet?.address
            val chainId = if (request.chainId != null) {
                "$ethNamespace:${request.chainId}"
            } else {
                currentSession?.namespaces?.get(ethNamespace)?.chains?.firstOrNull()
            }
            return if (sessionTopic != null && account != null && chainId != null) {
                Modal.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "personal_sign",
                    params = "[\"${message}\", \"${account}\"]",
                    chainId = chainId,
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, status, completion)
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Modal.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = _walletStatus.connectedWallet?.address
            val chainId = if (request.chainId != null) {
                "$ethNamespace:${request.chainId}"
            } else {
                currentSession?.namespaces?.get(ethNamespace)?.chains?.firstOrNull()
            }
            val message = typedDataProvider?.typedDataAsString?.replace("\"", "\\\"")

            return if (sessionTopic != null && account != null && chainId != null && message != null) {
                Modal.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "eth_signTypedData",
                    params = "[\"${account}\", \"${message}\"]",
                    chainId = chainId,
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request, { requestParams() }, connected, status, completion)
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        fun requestParams(): Modal.Params.Request? {
            val sessionTopic = currentSession?.topic
            val account = _walletStatus.connectedWallet?.address
            val chainId = if (request.walletRequest.chainId != null) {
                "$ethNamespace:${request.walletRequest.chainId}"
            } else {
                currentSession?.namespaces?.get(ethNamespace)?.chains?.firstOrNull()
            }
            val message = request.ethereum?.toJsonRequest()

            return if (sessionTopic != null && account != null && chainId != null && message != null) {
                Modal.Params.Request(
                    sessionTopic = sessionTopic,
                    method = "eth_sendTransaction",
                    params = "[$message]",
                    chainId = chainId,
                    expiry = requestExpiry,
                )
            } else {
                null
            }
        }

        connectAndMakeRequest(request.walletRequest, { requestParams() }, connected, status, completion)
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

    private fun connectAndMakeRequest(
        request: WalletRequest,
        requestParams: (() -> Modal.Params.Request?),
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        connect(request) { info, error ->
            if (error != null) {
                completion(null, error)
            } else if (currentSession != null) {
                if (connected != null) {
                    connected(info)
                }

                Thread.sleep(1000)
                val params = requestParams()
                if (params != null) {
                    reallyMakeRequest(params, status) { result, requestError ->
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
        requestParams: Modal.Params.Request,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        WalletConnectModal.request(
            request = requestParams,
            onSuccess = { sendRequest ->
                /* callback that letting you know that you have successful request */
                Timber.d("Wallet request made.")
                operationCompletions[sendRequest.sessionTopic] = completion
            },
            onError = { error ->
                /* callback that letting you know that you have error */
                Timber.e(error.throwable.stackTraceToString())
                completion(
                    null,
                    WalletError(
                        code = CarteraErrorCode.CONNECTION_FAILED,
                        title = "WalletConnectModal.request error",
                        message = error.throwable.stackTraceToString(),
                    ),
                )
            },
        )

        openPeerDeeplink(status)
    }

    // MARK: WalletConnectModal.ModalDelegate

    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
        Timber.d("Connection state changed: $state")
    }

    override fun onError(error: Modal.Model.Error) {
        Timber.e("WalletConnectModal error: $error")
    }

    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
        Timber.d("Proposal expired: $proposal")
    }

    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
        Timber.d("Request expired: $request")
    }

    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
        Timber.d("Session approved: $approvedSession")

        CoroutineScope(Dispatchers.Main).launch {
            val approvedSsssion = approvedSession.namespaces[ethNamespace]

            val requestChainId = requestingWallet?.chainId
            val walletChainIds = approvedSsssion?.chains?.mapNotNull {
                val components = it.split(":")
                if (components.size > 1) components[1] else null
            } ?: emptyList()
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
            _walletStatus.connectedWallet =
                fromApprovedSession(approvedSession, requestingWallet?.wallet)
            _walletStatus.connectionDeeplink = approvedSession.metaData?.appLink ?: approvedSession.metaData?.redirect

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

    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
        Timber.d("Session deleted: $deletedSession")

        when (deletedSession) {
            is Modal.Model.DeletedSession.Success -> {
                if (currentSession?.topic == deletedSession.topic) {
                    currentSession = null

                    _walletStatus.state = WalletState.IDLE
                    _walletStatus.connectedWallet = null
                    _walletStatus.connectionDeeplink = null

                    walletStatusDelegate?.statusChanged(_walletStatus)
                }
            }
            is Modal.Model.DeletedSession.Error -> {
                Timber.e("Session delete error: ${deletedSession.error}")
            }
        }
    }

    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
        Timber.d("Session event: $sessionEvent")
    }

    override fun onSessionExtend(session: Modal.Model.Session) {
        Timber.d("Session extended: $session")
    }

    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
        Timber.d("Session rejected: $rejectedSession")

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

    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
        Timber.d("Session request response: $response")

        CoroutineScope(Dispatchers.Main).launch {
            val completion = operationCompletions[response.topic]
            if (completion != null) {
                when (response.result) {
                    is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                        val result =
                            response.result as Modal.Model.JsonRpcResponse.JsonRpcResult
                        completion.invoke(
                            result.result,
                            null,
                        )
                    }

                    is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                        val error =
                            response.result as Modal.Model.JsonRpcResponse.JsonRpcError
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

    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
        Timber.d("Session updated: $updatedSession")
    }

    private fun fromApprovedSession(session: Modal.Model.ApprovedSession, wallet: Wallet?): WalletInfo {
        val account = session.accounts.firstOrNull()
        var address: String? = null
        var chainId: String? = null
        if (account != null) {
            val comps = account.split(":")
            if (comps.size == 3) {
                address = comps[2]
                chainId = comps[1]
            }
        }
        return WalletInfo(
            address = address,
            chainId = chainId,
            wallet = wallet,
            peerName = session.metaData?.name,
            peerImageUrl = session.metaData?.icons?.firstOrNull(),
        )
    }

    private fun openPeerDeeplink(status: WalletOperationStatus?) {
        if (currentSession == null) {
            Timber.d("Current session is null")
            return
        }

        val deeplinkPairingUri = currentSession?.metaData?.redirect ?: currentSession?.metaData?.appLink
        if (deeplinkPairingUri != null) {
            try {
                val uri = Uri.parse(deeplinkPairingUri)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                Timber.e(exception)
            }
        } else {
            Timber.d("Invalid deeplink uri")
            status?.invoke(true) // tell the client user needs to manually switch to the wallet
        }
    }
}
