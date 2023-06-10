package exchange.dydx.cartera.entities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import exchange.dydx.cartera.Utils
import exchange.dydx.cartera.WalletConnectionType

fun Wallet.installed(context: Context): Boolean {
    config?.androidPackage?.let { androidPackage ->
        return Utils.isInstalled(androidPackage, context.packageManager)
    }
    return false
}

fun Wallet.openPlayStore(context: Context): Unit {
    config?.androidPackage?.let { androidPackage ->
        try {
            startActivity(context,
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$androidPackage")),
                null)
        } catch (e: ActivityNotFoundException) {
            startActivity(context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$androidPackage")),
                null)
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
    connections?.firstOrNull()?.type?.let { type ->
        return WalletConnectionType.fromRawValue(type)
    }
    return WalletConnectionType.Unknown
}

fun WalletConfig.connections(type: WalletConnectionType): WalletConnections? {
    return connections?.firstOrNull { it.type == type.rawValue }
}
