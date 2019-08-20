package pm.gnosis.heimdall.ui.safe.connect

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.screen_connect_extension_start.*
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.qrscan.QRCodeScanActivity
import pm.gnosis.heimdall.ui.safe.pairing.PairingAuthenticatorContract
import pm.gnosis.heimdall.ui.tokens.payment.PaymentTokensActivity
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionActivity
import pm.gnosis.heimdall.utils.InfoTipDialogBuilder
import pm.gnosis.heimdall.utils.handleQrCodeActivityResult
import pm.gnosis.heimdall.utils.underline
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.shareExternalText
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_error as feesError
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_info as feesInfo
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_fees_value as feesValue
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_safe_balance_after_value as balanceAfterValue
import kotlinx.android.synthetic.main.include_transfer_summary.transfer_data_safe_balance_before_value as balanceBeforeValue
import kotlinx.android.synthetic.main.screen_connect_extension_start.replace_extension_back_arrow as backArrow
import kotlinx.android.synthetic.main.screen_connect_extension_start.replace_extension_coordinator as coordinator
import kotlinx.android.synthetic.main.screen_connect_extension_start.replace_extension_bottom_panel as bottomPanel
import kotlinx.android.synthetic.main.screen_connect_extension_start.replace_extension_info_2 as authenticatorLink
import kotlinx.android.synthetic.main.screen_connect_extension_start.replace_extension_progress_bar as progressBar

class ConnectExtensionStartActivity : ViewModelActivity<PairingAuthenticatorContract>() {

    override fun layout() = R.layout.screen_connect_extension_start

    override fun inject(component: ViewComponent) = viewComponent().inject(this)

    override fun screenId() = ScreenId.CONNECT_BROWSER_EXTENSION

    private lateinit var safe: Solidity.Address

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        safe = intent.getStringExtra(EXTRA_SAFE_ADDRESS)?.asEthereumAddress() ?: run {
            finish()
            return
        }

        bottomPanel.disabled = true

        authenticatorLink.apply {
            setOnClickListener { shareExternalText(BuildConfig.CHROME_EXTENSION_URL, "Browser Extension URL") }
        }

        viewModel.setup(safe)

        viewModel.observableState.observe(this, Observer {

            onLoading(it.isLoading)

            when(it) {

                is PairingAuthenticatorContract.ViewUpdate.Balance -> {

                    if (it.sufficient) {
                        bottomPanel.disabled = false
                        feesError.visibility = View.GONE
                    } else {
                        bottomPanel.disabled = true
                        feesError.text = getString(R.string.insufficient_funds_please_add, it.tokenSymbol)
                        feesError.visibility = View.VISIBLE
                    }
                    progressBar.visibility = View.GONE
                    balanceBeforeValue.text = it.balanceBefore
                    feesValue.text = it.fee.underline()
                    balanceAfterValue.text = it.balanceAfter
                }

                is PairingAuthenticatorContract.ViewUpdate.Pairing -> {
                    it.pairingResult?.let {
                        when (it) {
                            is PairingAuthenticatorContract.PairingResult.PairingSuccess -> {
                                toast(R.string.devices_paired_successfuly)
                                startActivity(
                                    ReviewTransactionActivity.createIntent(
                                        context = this,
                                        safe = intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!!,
                                        txData = TransactionData.ConnectExtension(it.extension)
                                    )
                                )
                            }

                            is PairingAuthenticatorContract.PairingResult.PairingError -> {
                                snackbar(coordinator, R.string.error_pairing_devices)
                                Timber.e(it.error)
                            }
                        }
                    }
                }

            }
        })

        lifecycleScope.launchWhenStarted {
            viewModel.estimate()
        }
    }

    override fun onStart() {
        super.onStart()

        backArrow.setOnClickListener {
            finish()
        }

        feesValue.setOnClickListener {
            startActivity(PaymentTokensActivity.createIntent(this, safe))
        }

        disposables += feesInfo.clicks()
            .subscribeBy {
                InfoTipDialogBuilder.build(this, R.layout.dialog_network_fee, R.string.ok).show()
            }

        disposables += bottomPanel.forwardClicks.subscribeBy {
            QRCodeScanActivity.startForResult(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        handleQrCodeActivityResult(requestCode, resultCode, data, onQrCodeResult = { viewModel.pair(it) })
    }

    private fun onLoading(isLoading: Boolean) {
        bottomPanel.disabled = isLoading
        progressBar.visible(isLoading)
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"

        fun createIntent(context: Context, safeAddress: Solidity.Address) = Intent(context, ConnectExtensionStartActivity::class.java).apply {
            putExtra(EXTRA_SAFE_ADDRESS, safeAddress.asEthereumAddressString())
        }
    }
}
