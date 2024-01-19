package exchange.dydx.cartera.walletprovider.providers

import android.content.Context
import exchange.dydx.cartera.WalletConnectionType
import exchange.dydx.cartera.entities.Wallet
import exchange.dydx.cartera.entities.appLink
import exchange.dydx.cartera.entities.connections
import exchange.dydx.cartera.entities.installed
import exchange.dydx.cartera.entities.native
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WalletConnectUtils {
    fun createUrl(wallet: Wallet, deeplink: String?, type: WalletConnectionType, context: Context): URL? {
        if (deeplink != null) {
            val url: URL? = if (wallet.installed(context)) {
                build(deeplink, wallet, type)
            } else if (wallet.appLink != null) {
                URL(wallet.appLink)
            } else {
                URL(deeplink)
            }
            return url
        } else {
            if (wallet.native != null && wallet.installed(context)) {
                val deeplink = "${wallet.native}///"
                return URL(deeplink)
            }
        }
        return null
    }

    private fun build(deeplink: String, wallet: Wallet, type: WalletConnectionType): URL? {
        val config = wallet.config ?: return null
        val encoding = config.encoding

        val universal = config.connections(type)?.universal?.trim()
        val native = config.connections(type)?.native?.trim()

        val useUniversal = universal?.isNotEmpty() == true
        val useNative = native?.isNotEmpty() == true

        var url: URL? = null
        if (universal != null && useUniversal) {
            url = createUniversallink(universal, deeplink, encoding)
        }
        if (native != null && useNative && url == null) {
            url = createDeeplink(native, deeplink, encoding)
        }
        if (url == null) {
            try {
                url = URL(deeplink)
            } catch (e: Exception) {
                return null
            }
        }
        return url
    }

    private fun createUniversallink(universal: String, deeplink: String, encoding: String?): URL? {
        try {
            val encoded = encodeUri(deeplink, encoding)
            val link = "$universal/wc?uri=$encoded"
            return URL(link)
        } catch (e: Exception) {
            return null
        }
    }

    private fun createDeeplink(native: String, deeplink: String, encoding: String?): URL? {
        try {
            val encoded = encodeUri(deeplink, encoding)
            val link = "$native//wc?uri=$encoded"
            return URL(link)
        } catch (e: Exception) {
            return null
        }
    }

    private fun encodeUri(deeplink: String, encoding: String?): String {
        if (encoding != null) {
            val allowedSet = encoding.toCharArray().toSet().toTypedArray()
            val encodedUri = URLEncoder.encode(deeplink, StandardCharsets.UTF_8.displayName())
            return encodedUri.replace("+", "%20").replace("%2F", "/")
        } else {
            return deeplink
        }
    }
}
