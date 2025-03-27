
<h1 align="center">Cartera-Android</h1>

<div align="center">
  <a href='https://github.com/dydxprotocol/cartera-android/blob/main/LICENSE'>
    <img src='https://img.shields.io/badge/License-AGPL_v3-blue.svg' alt='License' />
  </a>
</div>


Cartera is a mobile web3 wallet integrator. It acts as an abtraction layer over various wallet SDKs to provide a shared interface for common wallet operations. Cartera has the built-in support of the following SDKs:

WalletConnect V1
WalletConnect V2
CoinbaseWallet SDK

## Installation

The repo is currently private.  To access it, create a personal access token on Github and add it to the repo [link](https://docs.github.com/en/enterprise-server@3.4/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens).

Add your github user name and token as shell env vars:
```
export github_username=[GITHUB_USERNAME]
export github_token=[GITHUB_TOKEN]
```

Add the following to the project's gradle repositories settings:
```
    repositories {
        ....
        maven {
            val github_username = System.getenv("github_username")
            val github_token = System.getenv("github_token")
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/dydxprotocol/cartera-android")
            credentials {
                username = github_username
                password = github_token
            }
        }
    }
```

And then add the dependency: 
```
dependencies {
    ...
    implementation "dydxprotocol:cartera-android:0.0.1"
}
```

### [OPTIONAL] 

To build from a local copy of the library, update cartera/build.gradle to the following
```
apply from: 'publishLocal.gradle'
//apply from: 'publishRemote.gradle'
```
Build and push the code to Maven Local repo with:
```
./gradlew publishToMavenLocal
```
Then add "-local-debug" to the library import from the main app's build.gradle
```
dependencies {
    ...
    implementation "dydxprotocol:cartera-android:0.0.1-local-debug"
}
```

## SDK Configuration
To enable the built-in SDK support, create a configuration object for each of the SDKs as follows:
```Kotlin
object WalletProvidersConfigUtil {
    fun getWalletProvidersConfig(): WalletProvidersConfig {
        val walletConnectV1Config = WalletConnectV1Config(
            "dYdX",
            "dYdX Trading App",
            "https://media.dydx.exchange/logos/dydx-x.png",
            "dydx:",
            "https://trade.dydx.exchange/",
            "wss://api.stage.dydx.exchange/wc/"
        )

        val walletConnectV2Config = WalletConnectV2Config(
            "<WalletConnectV2 Token>",
            "dYdX",
            "dYdX Trading App",
            "https://trade.dydx.exchange/",
            listOf<String>("https://media.dydx.exchange/logos/dydx-x.png"),
        )

        val walletSegueConfig = WalletSegueConfig(
            "https://trade.stage.dydx.exchange/walletsegueCarteraExample"
        )

        return WalletProvidersConfig(
            walletConnectV1Config,
            walletConnectV2Config,
            walletSegueConfig
        )
    }
}
```
Then set the configuration on your activity `onCreate()`:
```Kotlin
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            CarteraConfig.handleResponse(uri)
        }

        CarteraConfig.shared = CarteraConfig(
            walletProvidersConfig = WalletProvidersConfigUtil.getWalletProvidersConfig(),
            application = applicationContext as Application,
            launcher = launcher
        )
        CarteraConfig.shared?.registerWallets(context = applicationContext)
       ....
   }
}
```
Coinbase SDK requires deeplinks to wallet apps, so add the following to AndroidManifest.xml to access the wallet apps:
```
   <queries>
        <!-- Explicit apps you know in advance about: -->
        <package android:name="io.metamask"/>
        <package android:name="org.toshi"/>
        <package android:name="im.token.app"/>
        <package android:name="com.wallet.crypto.trustapp"/>
        <package android:name="io.zerion.android"/>
        <package android:name="vip.mytokenpocket"/>
        <package android:name="me.rainbow"/>
        <package android:name="io.oneinch.android"/>
        <package android:name="pro.huobi"/>
    </queries>
```

## Wallet Configuration

There is a list of supported wallets specified in [wallets_config.json](cartera/src/main/res/raw/wallets_config.json).  Call the following to register those wallets with Cartera:
```Kotlin
CarteraConfig.shared.registerWallets(context)
```
Alternatively, you can specify a path of your own wallet config JSON data as follows:
```Kotlin
CarteraConfig.shared.registerWallets(context, <config_json>")
```

## Wallet Operations

Once configured, you can obtain a list of supported wallets, along with their status (e.g., installed or not) with
```Kotlin
    val wallets = CarteraConfig.shared.wallets
```
To operate on a wallet, create a CarteraProvider and, optionally, set its walletStatusDelegate:
```Kotlin
    val provider: CarteraProvider by lazy {
        val provider = CarteraProvider(context)
        provider.walletStatusDelegate = this
        provider
    }
```
The following code would ask the selected wallet to sign a personal message:
```Kotlin
    val request = WalletRequest(wallet = wallet, address = null, chainId = 5)
    provider.signMessage(request = request,
        message = "Test Message",
        connected = { info ->
           // connected
        },
        completion = { signature, error ->
            if (error != null) {
                // error
            } else {
                // success
            }
        }
    )
```

## Examples

For reference, there is a [sample app](app) in the repo.
