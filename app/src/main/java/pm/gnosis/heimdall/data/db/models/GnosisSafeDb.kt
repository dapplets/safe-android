package pm.gnosis.heimdall.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository.Operation.Companion.fromInt
import pm.gnosis.heimdall.data.repositories.models.PendingSafe
import pm.gnosis.heimdall.data.repositories.models.RecoveringSafe
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.data.repositories.toInt
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.security.db.EncryptedByteArray
import java.math.BigInteger

@Entity(tableName = GnosisSafeDb.TABLE_NAME)
data class GnosisSafeDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address
) {
    companion object {
        const val TABLE_NAME = "gnosis_safes"
        const val COL_ADDRESS = "address"
    }
}

fun Safe.toDb() = GnosisSafeDb(address)
fun GnosisSafeDb.fromDb() = Safe(address)

@Entity(
    tableName = GnosisSafeInfoDb.TABLE_NAME
)
data class GnosisSafeInfoDb(
    @PrimaryKey
    @ColumnInfo(name = COL_SAFE_ADDRESS)
    val safeAddress: Solidity.Address,

    @ColumnInfo(name = COL_OWNER_ADDRESS)
    val ownerAddress: Solidity.Address,

    @ColumnInfo(name = COL_OWNER_PRIVATE_KEY)
    var ownerPrivateKey: EncryptedByteArray,

    @ColumnInfo(name = COL_PAYMENT_TOKEN_ADDRESS)
    var paymentTokenAddress: Solidity.Address,

    @ColumnInfo(name = COL_PAYMENT_TOKEN_SYMBOL)
    var paymentTokenSymbol: String,

    @ColumnInfo(name = COL_PAYMENT_TOKEN_NAME)
    var paymentTokenName: String,

    @ColumnInfo(name = COL_PAYMENT_TOKEN_DECIMALS)
    var paymentTokenDecimals: Int,

    @ColumnInfo(name = COL_PAYMENT_TOKEN_ICON)
    var paymentTokenIcon: String?

) {
    companion object {
        const val TABLE_NAME = "gnosis_safe_info"
        const val COL_SAFE_ADDRESS = "safe_address"
        const val COL_OWNER_ADDRESS = "owner_address"
        const val COL_OWNER_PRIVATE_KEY = "owner_private_key"
        const val COL_PAYMENT_TOKEN_ADDRESS = "payment_token_address"
        const val COL_PAYMENT_TOKEN_SYMBOL = "payment_token_symbol"
        const val COL_PAYMENT_TOKEN_NAME = "payment_token_name"
        const val COL_PAYMENT_TOKEN_DECIMALS = "payment_token_decimals"
        const val COL_PAYMENT_TOKEN_ICON = "payment_token_icon"
    }
}

@Entity(tableName = PendingGnosisSafeDb.TABLE_NAME)
data class PendingGnosisSafeDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_TX_HASH)
    var transactionHash: BigInteger,

    @ColumnInfo(name = COL_PAYMENT_TOKEN)
    var paymentToken: Solidity.Address,

    @ColumnInfo(name = COL_PAYMENT_AMOUNT)
    var paymentAmount: BigInteger,

    @ColumnInfo(name = COL_IS_FUNDED)
    var isFunded: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "gnosis_pending_safes"
        const val COL_ADDRESS = "address"
        const val COL_TX_HASH = "transactionHash"
        const val COL_PAYMENT_AMOUNT = "paymentAmount"
        const val COL_PAYMENT_TOKEN = "paymentToken"
        const val COL_IS_FUNDED = "funded"
    }
}

fun PendingSafe.toDb() = PendingGnosisSafeDb(address, BigInteger.ZERO, paymentToken, paymentAmount, isFunded)
fun PendingGnosisSafeDb.fromDb() = PendingSafe(address, paymentToken, paymentAmount, isFunded)

@Entity(tableName = RecoveringGnosisSafeDb.TABLE_NAME)
data class RecoveringGnosisSafeDb(

    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_TX_HASH)
    var transactionHash: BigInteger?,

    @ColumnInfo(name = COL_RECOVERER)
    var recoverer: Solidity.Address,

    @ColumnInfo(name = COL_DATA)
    var data: String,

    @ColumnInfo(name = COL_NONCE)
    var nonce: BigInteger,

    @ColumnInfo(name = COL_TX_GAS)
    var txGas: BigInteger,

    @ColumnInfo(name = COL_DATA_GAS)
    var dataGas: BigInteger,

    @ColumnInfo(name = COL_OPERATIONAL_GAS)
    var operationalGas: BigInteger,

    @ColumnInfo(name = COL_GAS_TOKEN)
    var gasToken: Solidity.Address,

    @ColumnInfo(name = COL_GAS_PRICE)
    var gasPrice: BigInteger,

    @ColumnInfo(name = COL_OPERATION)
    var operation: Int,

    @ColumnInfo(name = COL_SIGNATURES)
    var signatures: String
) {
    companion object {
        const val TABLE_NAME = "gnosis_recovering_safes"
        const val COL_ADDRESS = "address"
        const val COL_TX_HASH = "transactionHash"
        const val COL_RECOVERER = "recoverer"
        const val COL_DATA = "data"
        const val COL_TX_GAS = "txGas"
        const val COL_DATA_GAS = "dataGas"
        const val COL_OPERATIONAL_GAS = "operationalGas"
        const val COL_GAS_TOKEN = "gasToken"
        const val COL_GAS_PRICE = "gasPrice"
        const val COL_NONCE = "nonce"
        const val COL_OPERATION = "operation"
        const val COL_SIGNATURES = "signatures"
    }
}

fun RecoveringSafe.toDb() =
    RecoveringGnosisSafeDb(
        address,
        transactionHash,
        recoverer,
        data,
        nonce,
        txGas,
        dataGas,
        operationalGas,
        gasToken,
        gasPrice,
        operation.toInt(),
        signatures.joinToString(",") { it.toString() })

fun RecoveringGnosisSafeDb.fromDb() =
    RecoveringSafe(
        address,
        transactionHash,
        recoverer,
        data,
        txGas,
        dataGas,
        operationalGas,
        gasToken,
        gasPrice,
        nonce,
        fromInt(operation),
        signatures.split(",").map { Signature.from(it.trim()) })
