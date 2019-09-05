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
import org.json.JSONObject
import pm.gnosis.heimdall.*
import pm.gnosis.heimdall.data.remote.DappletServiceApi
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
import pm.gnosis.utils.hexStringToByteArray
import java.math.BigInteger
import javax.inject.Inject

class DappletViewModel @Inject constructor(
    private val dappletServiceApi: DappletServiceApi
) : DappletContract() {

    private val dapplet: JSONObject? = null

    override fun createSafeTransaction(): TransactionData {
        val from = "0xf8808c9777c7fdcaeee6d9fa354eae41b5e1d13e"
        val to = "0xccf7930d9b1fa67d101e3de18de5416dc66bd852"
        val value = "0x00"
        val data = "0xc8d8a70e000000000000000000000000000000000000000000000000102084c61d16b0026c10c20ee6d68dd900f8e4affeb2d6af65de82d0ccab957205a3f26411573b57"

//        val initData = SimpleRecoveryModule.Setup.encode(
//                recoverer,
//                Solidity.UInt256(delay.toBigInteger())
//        )
//        val proxyData = ProxyFactory.CreateProxy.encode(
//                BuildConfig.SIMPLE_RECOVERY_MODULE_MASTER_COPY_ADDRESS.asEthereumAddress()!!,
//                Solidity.Bytes(initData.hexStringToByteArray())
//        )
//        val setupData = Solidity.Bytes(proxyData.hexStringToByteArray()).encode()
//        val data = CreateAndAddModules.CreateAndAddModules.encode(
//                BuildConfig.PROXY_FACTORY_ADDRESS.asEthereumAddress()!!,
//                Solidity.Bytes(setupData.hexStringToByteArray())
//        )
        return TransactionData.Generic(
            to.asEthereumAddress()!!,
            BigInteger.ZERO,
            data,
            TransactionExecutionRepository.Operation.DELEGATE_CALL
        )
    }

    override fun getDapplet(id: String): Single<JSONObject> {
        return Single.create({source ->
            if (dapplet == null) {
                dappletServiceApi.getDapplet(id).subscribe({ response ->
                    source.onSuccess(JSONObject(response.string()))
                })
            } else {
                source.onSuccess(dapplet)
            }
        })
    }

    override fun renderView(dappletId: String, txMeta: JSONObject): Single<String> {
        return getDapplet(dappletId).map({
            dapplet ->
            val views = dapplet.getJSONArray("views")
            var tpl = "Can not render the dapplet view"

            for (i in 0..(views.length() - 1) step 1) {
                val view = views.getJSONObject(i)
                if (view.getString("@type") != "view-plain-mustache") continue

                tpl = view.getString("template")
                txMeta.keys().forEach {
                    tpl = tpl.replace("{{" + it + "}}", txMeta.getString(it))
                }
            }

            tpl
        })
    }
}

abstract class DappletContract : ViewModel() {
    abstract fun createSafeTransaction(): TransactionData
    abstract fun getDapplet(id: String): Single<JSONObject>
    abstract fun renderView(dappletId: String, txMeta: JSONObject): Single<String>
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
        transaction_dapplet_back_arrow.setOnClickListener { onBackPressed() }
        transaction_dapplet_submit.setOnClickListener {
            val txData = viewModel.createSafeTransaction()
            val referenceId = intent.getLongExtra(EXTRA_REFERENCE_ID, 0)
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            startActivity(ReviewTransactionActivity.createIntent(this, safe, txData, referenceId, sessionId))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        addressHelper.populateAddressInfo(transaction_dapplet_safe_address, transaction_dapplet_safe_name, transaction_dapplet_safe_image, safe).forEach {
            disposables += it
        }
    }

    override fun onResume() {
        super.onResume()
        val dappletId = intent.getStringExtra(EXTRA_DAPPLET_ID)
        val txMeta = JSONObject(intent.getStringExtra(EXTRA_TX_META))
        viewModel.renderView(dappletId, txMeta).subscribe({
            view -> runOnUiThread {
                transaction_dapplet_description.setText(view)
            }
        })
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
        private const val EXTRA_DAPPLET_ID = "extra.string.dapplet_id"
        private const val EXTRA_TX_META = "extra.string.transaction_metadata"
        private const val EXTRA_REFERENCE_ID = "extra.long.reference_id"
        private const val EXTRA_SESSION_ID = "extra.string.session_id"

        fun createIntent(
            context: Context,
            safe: Solidity.Address,
            dappletId: String,
            txMeta: String,
            referenceId: Long? = null,
            sessionId: String? = null
        ) =
            Intent(context, DappletActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safe.asEthereumAddressString())
                putExtra(EXTRA_DAPPLET_ID, dappletId)
                putExtra(EXTRA_TX_META, txMeta)
                referenceId?.let { putExtra(EXTRA_REFERENCE_ID, it) }
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
    }
}