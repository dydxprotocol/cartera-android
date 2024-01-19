package exchange.dydx.cartera

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.math.BigInteger

object Utils {
    fun isInstalled(packageName: String, packageManager: PackageManager): Boolean {
        try {
            packageManager.getPackageInfoCompat(packageName, PackageManager.GET_ACTIVITIES)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }

    fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, flags)
        }
}

inline fun <reified T : Any> tag(currentClass: T): String {
    return ("Wallet" + currentClass::class.java.canonicalName!!.substringAfterLast(".")).take(23)
}

fun BigInteger.toHexString() = "0x" + toString(16)
