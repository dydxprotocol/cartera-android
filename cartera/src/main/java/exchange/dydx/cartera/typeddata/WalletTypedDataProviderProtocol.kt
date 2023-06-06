package exchange.dydx.cartera.typeddata

interface WalletTypedDataProviderProtocol {
    fun typedData(): Map<String, Any>?
}

fun WalletTypedDataProviderProtocol.type(name: String, type: String): Map<String, String> {
    return mapOf("name" to name, "type" to type)
}

/*
val WalletTypedDataProviderProtocol.typedDataAsString: String?
    get() {
        typedData()?.let { typedData ->
            val data = try {
                JSONObject(typedData).toString()
            } catch (e: JSONException) {
                null
            }
            return data
        }
        return null
    }

 */