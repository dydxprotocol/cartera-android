package exchange.dydx.cartera.walletprovider.userconsent

import exchange.dydx.cartera.walletprovider.WalletTransactionRequest
import exchange.dydx.cartera.walletprovider.WalletUserConsentCompletion
import exchange.dydx.cartera.walletprovider.WalletUserConsentProtocol
import exchange.dydx.cartera.walletprovider.WalletUserConsentStatus

class SkippedWalletUserConsent : WalletUserConsentProtocol {
    override fun showTransactionConsent(request: WalletTransactionRequest, completion: WalletUserConsentCompletion?) {
        completion?.invoke(WalletUserConsentStatus.CONSENTED)
    }
}