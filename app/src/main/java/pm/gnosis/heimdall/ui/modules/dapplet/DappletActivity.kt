package pm.gnosis.heimdall.ui.modules.dapplet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Tasks.await
import io.reactivex.Single
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_transaction_dapplet.*
import kotlinx.coroutines.rx2.await
import okio.internal.commonAsUtf8ToByteArray
import org.json.JSONArray
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
import pm.gnosis.model.SolidityBase
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject

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

        val hex = value.toHexString().padStart(size, '0')

        return hex
    }

    override fun createSafeTransaction(dapplet: JSONObject, txMeta: JSONObject): TransactionData {
        val txs = dapplet.getJSONObject("transactions")
        val txName = txs.keys().next()
        val tx = txs.getJSONObject(txName)
        val txType = tx.getString("@type")
        val to = tx.getString("to")
        val args = tx.getJSONArray("args")
        val fn = tx.getString("function")
        val types = fn.substring(fn.indexOf("(") + 1).replace(")", "").split(",").toTypedArray()

        var data = "0x"

        // signature calculation
        val bytes = fn.commonAsUtf8ToByteArray()
        val hash = Sha3Utils.keccak(bytes).toHexString()
        val signature = hash.substring(0, 8)
        data += signature

        // params encoding
        val binaryTxMeta = jsonToBinaryMap(txMeta)
        for (i in 0..(args.length() - 1) step 1) {
            val arg = args.getString(i)
            val prop = arg.split(":")[0]
            var value = binaryTxMeta.get(prop) ?: throw Error("Invalid value")
            val formattersChain = arg.split(":").drop(1).toTypedArray()
            val formattedValue = DappletFormatters.formatChain(value, formattersChain)

            val type = types[i]
            val encoded = encode(type, formattedValue)
            data += encoded
        }

        return TransactionData.Generic(
            to.asEthereumAddress()!!,
            BigInteger.ZERO,
            data,
            TransactionExecutionRepository.Operation.DELEGATE_CALL
        )
    }

    private fun jsonToBinaryMap(json: JSONObject): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
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

        return map
    }

    override fun renderView(dapplet: JSONObject, txMeta: JSONObject): String? {
        val views = dapplet.getJSONArray("views")

        for (i in 0..(views.length() - 1) step 1) {
            val view = views.getJSONObject(i)
            if (view.getString("@type") != "view-plain-mustache") continue

            var tpl = view.getString("template")
            txMeta.keys().forEach {
                tpl = tpl.replace("{{" + it + "}}", txMeta.get(it).toString())
            }
            return tpl
        }

        return null
    }
}

abstract class DappletContract : ViewModel() {
    abstract fun createSafeTransaction(dapplet: JSONObject, txMeta: JSONObject): TransactionData
    abstract fun renderView(dapplet: JSONObject, txMeta: JSONObject): String?
}

class DappletActivity : ViewModelActivity<DappletContract>() {

    @Inject
    lateinit var addressHelper: AddressHelper

    override fun screenId() = ScreenId.TRANSACTION_DAPPLET

    override fun layout() = R.layout.layout_transaction_dapplet

    override fun inject(component: ViewComponent) = component.inject(this)

    private val safe: Solidity.Address by lazy { intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dapplet = JSONObject(intent.getStringExtra(EXTRA_DAPPLET))
        val txMeta = JSONObject(intent.getStringExtra(EXTRA_TX_META))
        transaction_dapplet_back_arrow.setOnClickListener { onBackPressed() }
        transaction_dapplet_submit.setOnClickListener {
            val txData = viewModel.createSafeTransaction(dapplet, txMeta)
            val referenceId = intent.getLongExtra(EXTRA_REFERENCE_ID, 0)
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            startActivity(ReviewTransactionActivity.createIntent(this, safe, txData, referenceId, sessionId))
            finish()
        }
        transaction_dapplet_description.setText(viewModel.renderView(dapplet, txMeta) ?: "Can not render the dapplet view")
    }

    override fun onStart() {
        super.onStart()
        addressHelper.populateAddressInfo(transaction_dapplet_safe_address, transaction_dapplet_safe_name, transaction_dapplet_safe_image, safe).forEach {
            disposables += it
        }
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
        private const val EXTRA_DAPPLET = "extra.string.dapplet"
        private const val EXTRA_TX_META = "extra.string.transaction_metadata"
        private const val EXTRA_REFERENCE_ID = "extra.long.reference_id"
        private const val EXTRA_SESSION_ID = "extra.string.session_id"

        fun createIntent(
            context: Context,
            safe: Solidity.Address,
            dapplet: String,
            txMeta: String,
            referenceId: Long? = null,
            sessionId: String? = null
        ) =
            Intent(context, DappletActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safe.asEthereumAddressString())
                putExtra(EXTRA_DAPPLET, dapplet)
                putExtra(EXTRA_TX_META, txMeta)
                referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
    }
}