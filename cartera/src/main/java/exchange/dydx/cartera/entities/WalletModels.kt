package exchange.dydx.cartera.entities

import com.google.gson.annotations.SerializedName

data class Wallet (
    @SerializedName("id"          ) var id          : String?           = null,
    @SerializedName("name"        ) var name        : String?           = null,
    @SerializedName("description" ) var description : String?           = null,
    @SerializedName("homepage"    ) var homepage    : String?           = null,
    @SerializedName("chains"      ) var chains      : ArrayList<String> = arrayListOf(),
    @SerializedName("versions"    ) var versions    : ArrayList<String> = arrayListOf(),
    @SerializedName("app"         ) var app         : WalletApp?        = WalletApp(),
    @SerializedName("mobile"      ) var mobile      : WalletMobile?     = WalletMobile(),
    @SerializedName("desktop"     ) var desktop     : WalletDesktop?    = WalletDesktop(),
    @SerializedName("metadata"    ) var metadata    : WalletMetadata?   = WalletMetadata(),
    @SerializedName("config"      ) var config      : WalletConfig?     = WalletConfig()
)

data class WalletApp (
    @SerializedName("browser" ) var browser : String? = null,
    @SerializedName("ios"     ) var ios     : String? = null,
    @SerializedName("android" ) var android : String? = null,
    @SerializedName("mac"     ) var mac     : String? = null,
    @SerializedName("windows" ) var windows : String? = null,
    @SerializedName("linux"   ) var linux   : String? = null
)

data class WalletMobile (
    @SerializedName("native"    ) var native    : String? = null,
    @SerializedName("universal" ) var universal : String? = null
)

data class WalletDesktop (
    @SerializedName("native"    ) var native    : String? = null,
    @SerializedName("universal" ) var universal : String? = null
)

data class WalletColors (
    @SerializedName("primary"   ) var primary   : String? = null,
    @SerializedName("secondary" ) var secondary : String? = null
)

data class WalletMetadata (
    @SerializedName("shortName" ) var shortName : String? = null,
    @SerializedName("colors"    ) var colors    : WalletColors? = WalletColors()
)

data class WalletConnections (
    @SerializedName("type"      ) var type      : String? = null,
    @SerializedName("native"    ) var native    : String? = null,
    @SerializedName("universal" ) var universal : String? = null,
)

data class WalletConfig (
    @SerializedName("comment"       ) var comment       : String?                = null,
    @SerializedName("iosMinVersion" ) var iosMinVersion : String?                = null,
    @SerializedName("encoding"      ) var encoding      : String?                = null,
    @SerializedName("androidPackage"   ) var androidPackage   : String?          = null,
    @SerializedName("imageUrl"      ) var imageUrl      : String?                = null,
    @SerializedName("connections"   ) var connections   : ArrayList<WalletConnections> = arrayListOf()
)

val Wallet.universal: String?
    get() = mobile?.universal

val Wallet.native: String?
    get() = mobile?.native?.let { "$it" }

val Wallet.appLink: String?
    get() = app?.ios
