package exchange.dydx.carteraexample.solana
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.BlockhashResponse
import com.solana.rpc.SolanaRpcClient
import com.solana.rpccore.Rpc20Response
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import exchange.dydx.carteraexample.solana.web3.KtorNetworkDriver

class SolanaInteractor(
    private val client: SolanaRpcClient
) {
    companion object {
        val mainnetClient = SolanaRpcClient(
            "https://api.mainnet-beta.solana.com",
            KtorNetworkDriver()
        )
        val devnetClient = SolanaRpcClient("https://api.devnet.solana.com", KtorNetworkDriver())
    }

    private val programId =  SolanaPublicKey.from("11111111111111111111111111111111")

    suspend fun getRecentBlockhash(): Rpc20Response<BlockhashResponse?> {
        return client.getLatestBlockhash()
    }

    fun buildTestMemoTransaction(address: SolanaPublicKey, memo: String) =
        TransactionInstruction(
            programId = programId,
            accounts = listOf(AccountMeta(publicKey = address, isSigner = true, isWritable = true)),
            data = memo.encodeToByteArray()
        )
}