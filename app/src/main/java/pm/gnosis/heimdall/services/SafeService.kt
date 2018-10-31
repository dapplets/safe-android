package pm.gnosis.heimdall.services

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteCallbackList
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.ISafeService
import pm.gnosis.heimdall.ISafeServiceCallback
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.TransactionInfoRepository
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionActivity
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.hexAsBigInteger
import java.util.*
import javax.inject.Inject


class SafeService : Service() {

    private val callbacks = RemoteCallbackList<ISafeServiceCallback>()

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var safeRepository: GnosisSafeRepository

    private val binder = object : ISafeService.Stub(), SafeServiceBinder {

        override fun sendTransaction(to: String?, value: String?, data: String?): String {
            val internalId = UUID.randomUUID().toString()
            val safe = currentSafe?.asEthereumAddress()!!
            val tx = SafeTransaction(
                Transaction(
                    address = to?.asEthereumAddress()!!,
                    value = value?.hexAsBigInteger()?.let { Wei(it) },
                    data = data
                ),
                TransactionExecutionRepository.Operation.CALL
            )
            startActivity(
                ProxyActivity.createIntent(
                    applicationContext,
                    internalId,
                    safe,
                    tx
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return internalId
        }

        override fun registerCallback(callback: ISafeServiceCallback) {
            callbacks.register(callback)
        }

        override fun unregisterCallback(callback: ISafeServiceCallback) {
            callbacks.unregister(callback)
        }

        override fun getCurrentSafe(): String? =
            preferencesManager.prefs.getString("safe_main.string.selected_safe", null)

        override fun getService(): SafeService = this@SafeService
    }

    fun broadcastTransactionSubmitted(id: String, txHash: String) {
        callbacks.apply {
            val count = beginBroadcast()
            for (i in 0 until count) {
                getBroadcastItem(i).transactionSubmitted(id, txHash)
            }
            finishBroadcast()
        }
    }

    fun broadcastTransactionRejected(id: String) {
        callbacks.apply {
            val count = beginBroadcast()
            for (i in 0 until count) {
                getBroadcastItem(i).transactionRejected(id)
            }
            finishBroadcast()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        HeimdallApplication[this].inject(this)
    }

    override fun onDestroy() {
        callbacks.kill()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    interface SafeServiceBinder {
        fun getService(): SafeService
    }

    class ProxyActivity : AppCompatActivity() {

        @Inject
        lateinit var transactionInfoRepository: TransactionInfoRepository

        private val disposables = CompositeDisposable()

        private var hasResult: Boolean = false
        private var txHash: String? = null

        private var service: SafeService? = null

        private val connection = object : ServiceConnection {
            override fun onServiceDisconnected(cn: ComponentName?) {
                service = null
            }

            override fun onServiceConnected(cn: ComponentName?, binder: IBinder?) {
                service = (binder as SafeServiceBinder).getService()
                if (hasResult) propagateResult()
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_CODE) {
                hasResult = true
                if (resultCode == Activity.RESULT_OK) {
                    txHash = data?.getStringExtra("pm.gnosis.heimdall.CHAIN_HASH")
                }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            HeimdallApplication[this].inject(this)
        }

        override fun onStart() {
            super.onStart()
            bindService(Intent(this, SafeService::class.java), connection, BIND_AUTO_CREATE)
            val safe = intent.getStringExtra(EXTRA_SAFE_ADDRESS)?.asEthereumAddress()!!
            val tx = intent.getParcelableExtra<SafeTransaction>(EXTRA_SAFE_TX)
            if (!hasResult) {
                disposables += transactionInfoRepository.parseTransactionData(tx)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy {
                        startActivityForResult(
                            ReviewTransactionActivity.createIntent(this, safe, it).apply {
                                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            },
                            REQUEST_CODE
                        )
                    }
            } else {
                propagateResult()
            }
        }

        private fun propagateResult() {
            service?.apply {
                val id = intent.getStringExtra(EXTRA_ID)
                txHash?.let {
                    broadcastTransactionSubmitted(id, it)
                } ?: run {
                    broadcastTransactionRejected(id)
                }
                finish()
            }
        }

        override fun onStop() {
            disposables.clear()
            unbindService(connection)
            service = null
            super.onStop()
        }

        companion object {
            private const val REQUEST_CODE = 8743
            private const val EXTRA_ID = "extra.string.transaction_id"
            private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
            private const val EXTRA_SAFE_TX = "extra.string.safe_tx"
            fun createIntent(context: Context, id: String, safe: Solidity.Address, tx: SafeTransaction) =
                Intent(context, ProxyActivity::class.java).apply {
                    putExtra(EXTRA_ID, id)
                    putExtra(EXTRA_SAFE_ADDRESS, safe.asEthereumAddressString())
                    putExtra(EXTRA_SAFE_TX, tx)
                }
        }
    }
}
