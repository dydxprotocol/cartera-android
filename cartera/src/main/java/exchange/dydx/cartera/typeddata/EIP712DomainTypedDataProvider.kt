package exchange.dydx.cartera.typeddata

class EIP712DomainTypedDataProvider(
    private val name: String,
    private val chainId: Int,
    private val version: String = "1.0.0"
) : WalletTypedDataProviderProtocol {
    var eip712: EIP712TypedData? = null
    var message: WalletTypedData? = null

    init {
        eip712 = EIP712TypedData(name, chainId, version)
    }

    override fun typedData(): Map<String, Any>? {
        val eip712 = eip712
        val message = message
        if (isValid && eip712 != null && message != null) {
            val types = mutableMapOf<String, Any>()
            eip712.definitions?.let { definitions ->
                types[eip712.typeName] = definitions
            }
            message.definitions?.let { definitions ->
                types[message.typeName] = definitions
            }

            val typedData = mutableMapOf<String, Any>()
            typedData["types"] = types
            typedData["primaryType"] = message.typeName
            eip712.data?.let { data ->
                typedData["domain"] = data
            }
            message.data?.let { data ->
                typedData["message"] = data
            }

            return typedData
        } else {
            return null
        }
    }

    private val isValid: Boolean
        get() = (eip712?.isValid == true) && (message?.isValid == true)
}