package exchange.dydx.cartera.walletprovider.providers

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.iwebpp.crypto.TweetNaclFast
import exchange.dydx.cartera.CarteraErrorCode
import exchange.dydx.cartera.PhantomWalletConfig
import exchange.dydx.cartera.encodeToBase58String
import exchange.dydx.cartera.tag
import exchange.dydx.cartera.typeddata.WalletTypedDataProviderProtocol
import exchange.dydx.cartera.walletprovider.EthereumAddChainRequest
import exchange.dydx.cartera.walletprovider.WalletConnectCompletion
import exchange.dydx.cartera.walletprovider.WalletConnectedCompletion
import exchange.dydx.cartera.walletprovider.WalletError
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
import timber.log.Timber
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PhantomWalletProvider(
    private val phantomWalletConfig: PhantomWalletConfig,
    private val application: Application,
): WalletOperationProviderProtocol  {

    private enum class CallbackAction(val request: String) {
        ON_CONNECT("connect"),
        ON_DISCONNECT("disconnect"),
        ON_SIGN_MESSAGE("signMessage"),
        ON_SIGN_TRANSACTION("signTransaction"),
        ON_SEND_TRANSACTION("signAndSendTransaction")
    }

    private var _walletStatus = WalletStatusImp()
        set(value) {
            field = value
            walletStatusDelegate?.statusChanged(value)
        }
    override val walletStatus: WalletStatusProtocol
        get() = _walletStatus

    override var walletStatusDelegate: WalletStatusDelegate? = null
    override var userConsentDelegate: WalletUserConsentProtocol? = null

    private val baseUrlString = "https://phantom.app/ul/v1"

    private var publicKey: ByteArray? = null
    private var privateKey: ByteArray? = null
    private var phantomPublicKey: ByteArray? = null
    private var session: String? = null

    override fun handleResponse(uri: Uri): Boolean {
        return true
    }

    override fun connect(request: WalletRequest, completion: WalletConnectCompletion) {
        if (_walletStatus.state == WalletState.CONNECTED_TO_WALLET) {
            completion(walletStatus.connectedWallet, null)
            return
        }

        val result =  TweetNaclFast.Box.keyPair()
        if (result == null) {
            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, "Failed to generate key pair"))
            return
        }

        publicKey = result.publicKey
        privateKey = result.secretKey

        val publickKeyEncoded = publicKey?.encodeToBase58String()
        if (publickKeyEncoded == null) {
            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, "Failed to encode public key"))
            return
        }

        val url = "$baseUrlString/${CallbackAction.ON_CONNECT}"
        val cluster = request.chainId
        if (cluster == null) {
            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, "Failed to get chainId"))
            return
        }

        val appUrl = phantomWalletConfig.appUrl.urlEncoded()
        val redirectLink = "${phantomWalletConfig.callbackUrl}/${CallbackAction.ON_CONNECT}".urlEncoded()
        val urlQueryParams = mapOf(
            "dapp_encryption_public_key" to publickKeyEncoded,
            "cluster" to cluster,
            "app_url" to appUrl,
            "redirect_link" to redirectLink,
        )
            .map { "${it.key}=${it.value}" }.joinToString("&")

        val requestUrl = "$url?$urlQueryParams"
        if (openPeerDeeplink(requestUrl)) {
            // TODO
        } else {
            completion(null, WalletError(CarteraErrorCode.CONNECTION_FAILED, "Failed to open Phantom app"))
        }

    }

    override fun disconnect() {
        TODO("Not yet implemented")
    }

    override fun signMessage(
        request: WalletRequest,
        message: String,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    override fun sign(
        request: WalletRequest,
        typedDataProvider: WalletTypedDataProviderProtocol?,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
    }

    override fun send(
        request: WalletTransactionRequest,
        connected: WalletConnectedCompletion?,
        status: WalletOperationStatus?,
        completion: WalletOperationCompletion
    ) {
        TODO("Not yet implemented")
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

    private fun openPeerDeeplink(url: String): Boolean {
        val uri = url.toUri()
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            application.startActivity(intent)
            return true
        } catch (exception: ActivityNotFoundException) {
            Timber.tag(tag(this@PhantomWalletProvider)).d("There is no app to handle deep link")
            return false
        }
    }

}

fun String.urlEncoded(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
}