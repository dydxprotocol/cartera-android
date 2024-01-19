package exchange.dydx.cartera

enum class CarteraErrorCode(val rawValue: Int) {
    USER_CANCELED(0),
    NETWORK_MISMATCH(1),
    WALLET_MISMATCH(2),
    WALLET_CONTAINS_NO_ACCOUNT(3),
    SIGNING_MESSAGE_FAILED(4),
    UNEXPECTED_RESPONSE(5),
    SIGNING_TRANSACTION_FAILED(6),
    CONNECTION_FAILED(7),
    REFUSED_BY_WALLET(8),
    LINK_OPEN_FAILED(9),
    INVALID_SESSION(10),
    INVALID_INPUT(11),
    ADD_CHAIN_FAILED(12);

    val message: String
        get() {
            return when (this) {
                USER_CANCELED -> "User canceled"
                NETWORK_MISMATCH -> "Network mismatch"
                WALLET_MISMATCH -> "Wallet mismatch"
                WALLET_CONTAINS_NO_ACCOUNT -> "Unable to obtain account"
                SIGNING_MESSAGE_FAILED -> "Signing message failed"
                UNEXPECTED_RESPONSE -> "Unexpected response"
                SIGNING_TRANSACTION_FAILED -> "Signing transaction failed"
                CONNECTION_FAILED -> "Connection failed"
                REFUSED_BY_WALLET -> "Refused by wallet"
                LINK_OPEN_FAILED -> "Unable to open link"
                INVALID_SESSION -> "Invalid session"
                INVALID_INPUT -> "Invalid input"
                ADD_CHAIN_FAILED -> "Add or switch chain failed"
            }
        }
}
