package pm.gnosis.heimdall.ui.modules.dapplet

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_transaction_dapplet.*
import okio.internal.commonAsUtf8ToByteArray
import org.json.JSONObject
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.heimdall.*
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.helpers.AddressHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionActivity
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import pm.gnosis.heimdall.data.repositories.impls.DappletRequest


class DappletFormatters {
    companion object {
        private fun bigNumberify(input: ByteArray): ByteArray {
            return input
        }

        private fun toUtf8Bytes(input: ByteArray): ByteArray {
            return input
        }

        private fun sha256(input: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(input)
        }

        fun formatChain(input: ByteArray, formatters: Array<String>): ByteArray {
            var output = input

            for (formatter in formatters) {
                output = when (formatter) {
                    "bigNumberify" -> bigNumberify(output)
                    "toUtf8Bytes" -> toUtf8Bytes(output)
                    "sha256" -> sha256(output)
                    else -> throw Error("Incompatible formatter")
                }
            }

            return output
        }
    }
}

class DappletViewModel @Inject constructor() : DappletContract() {
    private fun encode(type: String, value: ByteArray): String {
        val normalizedType = Solidity.aliases.get(type) ?: type

        val size = when (normalizedType) {
            "address" -> 40 // 160 bits
            "bool" -> 1
            "bytes" -> throw Error("Dynamic types are incompatible")
            "string" -> throw Error("Dynamic types are incompatible")
            else -> {
                if (normalizedType.contains("uint") || normalizedType.contains("int")) {
                    normalizedType.replace("uint", "").replace("int", "").toInt().div(4)
                } else if (normalizedType.contains("bytes")) {
                    normalizedType.replace("bytes", "").toInt()
                } else {
                    throw Error("Incompatible type")
                }
            }
        }

        return value.toHexString().padStart(size, '0')
    }

    override fun createSafeTransaction(dapplet: JSONObject, txMeta: JSONObject?): TransactionData {
        val transactions = dapplet.getJSONObject("transactions")

        // ToDo: Create state machine for running of multiple transactions. The first tx is running now.
        val txName = transactions.keys().next()
        val tx = transactions.getJSONObject(txName)

        val type = tx.optString("@type")
        if (type.length == 0) throw Error("Property \"@type\" is required in transaction.")

        return when (type) {
            "builder-tx-sol" -> buildSolidityTx(tx, txMeta)
            else -> throw Error("Incompatible transaction type.")
        }
    }

    private fun buildSolidityTx(tx: JSONObject, metadata: JSONObject?): TransactionData {
        val to = tx.optString("to")
        if (to.isEmpty()) throw Error("Property \"to\" is required in transaction.")

        val value = tx.optLong("value", 0)

        var data: String? = null

        val fn = tx.optString("function")
        if (fn.isNotEmpty()) {
            val inputTypes = fn.substring(fn.indexOf("(") + 1).replace(")", "").split(",").toTypedArray()
            val args = tx.optJSONArray("args")

            if (inputTypes.count() != args.length()) throw Error("The number of input parameters and arguments does not match.")

            data = "0x"

            // signature calculation
            val bytes = fn.commonAsUtf8ToByteArray()
            val hash = Sha3Utils.keccak(bytes).toHexString()
            val signature = hash.substring(0, 8)
            data += signature

            // params encoding
            val binaryTxMeta = jsonToBinaryMap(metadata)
            for (i in 0..(args.length() - 1) step 1) {
                val arg = args.getString(i)
                val prop = arg.split(":")[0]
                var value = binaryTxMeta.get(prop) ?: throw Error("Invalid value")
                val formattersChain = arg.split(":").drop(1).toTypedArray()
                val formattedValue = DappletFormatters.formatChain(value, formattersChain)

                val type = inputTypes[i]
                val encoded = encode(type, formattedValue)
                data += encoded
            }
        }

        return TransactionData.Generic(
            to.asEthereumAddress()!!,
            BigInteger.valueOf(value),
            data,
            TransactionExecutionRepository.Operation.DELEGATE_CALL
        )
    }

    private fun jsonToBinaryMap(json: JSONObject?): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        if (json != null) {
            val keys = json.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)

                val binary: ByteArray = when (value) {
                    is String -> value.commonAsUtf8ToByteArray()
                    is Int -> value.toBigInteger().toByteArray()
                    is Long -> value.toBigInteger().toByteArray()
                    else -> throw Error("Invalid type")
                }

                map.put(key, binary)
            }
        }

        return map
    }
}

abstract class DappletContract : ViewModel() {
    abstract fun createSafeTransaction(dapplet: JSONObject, txMeta: JSONObject?): TransactionData
}

class DappletActivity : ViewModelActivity<DappletContract>() {

    @Inject
    lateinit var addressHelper: AddressHelper

    override fun screenId() = ScreenId.TRANSACTION_DAPPLET

    override fun layout() = R.layout.layout_transaction_dapplet

    override fun inject(component: ViewComponent) = component.inject(this)

    private val safe: Solidity.Address by lazy { intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!! }

    val REQUEST_IMAGE_CAPTURE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dappletRequest = DappletRequest.fromJson(intent.getStringExtra(EXTRA_DAPPLET_REQUEST))
        transaction_dapplet_back_arrow.setOnClickListener { onBackPressed() }
        transaction_dapplet_submit.setOnClickListener {
            val firstFrame = dappletRequest.frames.first()
            val txData = viewModel.createSafeTransaction(firstFrame.dapplet!!, firstFrame.txMeta)
            val referenceId = intent.getLongExtra(EXTRA_REFERENCE_ID, 0)
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            startActivity(ReviewTransactionActivity.createIntent(this, safe, txData, referenceId, sessionId))
            finish()
        }
        transaction_dapplet_makephoto.setOnClickListener {
            // ToDo: open camera view
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }

        transaction_dapplet_view.setDappletRequest(dappletRequest)
    }

    override fun onStart() {
        super.onStart()
        addressHelper.populateAddressInfo(transaction_dapplet_safe_address, transaction_dapplet_safe_name, transaction_dapplet_safe_image, safe).forEach {
            disposables += it
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val imageBitmap = data!!.extras.get("data") as Bitmap
            transaction_dapplet_imageview.setImageBitmap(imageBitmap)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
        private const val EXTRA_DAPPLET_REQUEST = "extra.string.dapplet_request"
        private const val EXTRA_REFERENCE_ID = "extra.long.reference_id"
        private const val EXTRA_SESSION_ID = "extra.string.session_id"

        fun createIntent(
            context: Context,
            safe: Solidity.Address,
            dappletRequest: DappletRequest,
            referenceId: Long? = null,
            sessionId: String? = null
        ) =
            Intent(context, DappletActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safe.asEthereumAddressString())
                referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_DAPPLET_REQUEST, dappletRequest.toJson())
            }
    }
}