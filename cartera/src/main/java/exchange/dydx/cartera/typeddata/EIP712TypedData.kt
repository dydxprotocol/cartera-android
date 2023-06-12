package exchange.dydx.cartera.typeddata

class EIP712TypedData(name: String, chainId: Int, version: String?) : WalletTypedData("EIP712Domain") {
    init {
        val definitions = mutableListOf<Map<String, String>>()
        val data = mutableMapOf<String, Any>()

        definitions.add(type(name = "name", type = "string"))
        data["name"] = name

        version?.let {
            definitions.add(type(name = "version", type = "string"))
            data["version"] = version
        }

        definitions.add(type(name = "chainId", type = "uint256"))
        data["chainId"] = chainId

        this.definitions = definitions
        this.data = data
    }
}