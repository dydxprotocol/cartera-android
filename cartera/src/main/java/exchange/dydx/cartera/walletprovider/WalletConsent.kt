package exchange.dydx.cartera.walletprovider

enum class WalletUserConsentStatus {
    CONSENTED,
    REJECTED
}

typealias WalletUserConsentCompletion = (consentStatus: WalletUserConsentStatus) -> Unit

interface WalletUserConsentProtocol {
    fun showTransactionConsent(request: WalletTransactionRequest, completion: WalletUserConsentCompletion?)
}
