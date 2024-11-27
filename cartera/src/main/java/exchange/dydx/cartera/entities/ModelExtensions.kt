package exchange.dydx.cartera.entities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import exchange.dydx.cartera.Utils
import exchange.dydx.cartera.WalletConnectionType
import exchange.dydx.cartera.toHexString
import exchange.dydx.cartera.walletprovider.EthereumTransactionRequest
import okhttp3.internal.toHexString
import org.json.JSONException
import org.json.JSONObject

fun Wallet.installed(context: Context): Boolean {
    config?.androidPackage?.let { androidPackage ->
        return Utils.isInstalled(androidPackage, context.packageManager)
    }
    return false
}

fun Wallet.openPlayStore(context: Context) {
    config?.androidPackage?.let { androidPackage ->
        try {
            startActivity(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$androidPackage")),
                null,
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$androidPackage")),
                null,
            )
        }
    }
}

val WalletConfig.iosEnabled: Boolean
    get() {
        iosMinVersion?.let { iosMinVersion ->
//            return Bundle.main.versionCompare(otherVersion = iosMinVersion).rawValue >= 0
        }
        return false
    }

fun WalletConfig.connectionType(context: Context): WalletConnectionType {
    connections.firstOrNull()?.type?.let { type ->
        return WalletConnectionType.fromRawValue(type)
    }
    return WalletConnectionType.Unknown
}

fun WalletConfig.connections(type: WalletConnectionType): WalletConnections? {
    return connections?.firstOrNull { it.type == type.rawValue }
}

fun EthereumTransactionRequest.toJsonRequest(): String? {
    val request: MutableMap<String, Any?> = mutableMapOf()

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
        JSONObject(filtered).toString()
    } catch (e: JSONException) {
        null
    }
}
