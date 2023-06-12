package exchange.dydx.cartera.typeddata

open class WalletTypedData(typeName: String) {
    var typeName: String = typeName
    var definitions: List<Map<String, String>>? = null
    var data: Map<String, Any>? = null

    val isValid: Boolean
        get() {
            if (definitions != null && data != null) {
                val firstNonExisting = definitions?.firstOrNull { definition ->
                    val key = definition["name"]
                    key != null && data?.containsKey(key) != true
                }
                return firstNonExisting == null
            } else {
                return false
            }
        }

    fun type(name: String, type: String): Map<String, String> {
        return mapOf("name" to name, "type" to type)
    }
}