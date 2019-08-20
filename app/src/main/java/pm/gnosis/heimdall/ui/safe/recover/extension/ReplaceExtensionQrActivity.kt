package pm.gnosis.heimdall.ui.safe.recover.extension

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import androidx.lifecycle.Observer
import io.reactivex.rxkotlin.plusAssign
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.heimdall.ui.qrscan.QRCodeScanActivity
import pm.gnosis.heimdall.ui.safe.pairing.PairingAuthenticatorContract
import pm.gnosis.heimdall.utils.handleQrCodeActivityResult
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.shareExternalText
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import kotlinx.android.synthetic.main.screen_replace_extension_qr.replace_extension_back_arrow as backArrow
import kotlinx.android.synthetic.main.screen_replace_extension_qr.replace_extension_bottom_panel as bottomPanel
import kotlinx.android.synthetic.main.screen_replace_extension_qr.replace_extension_coordinator as coordinator
import kotlinx.android.synthetic.main.screen_replace_extension_qr.replace_extension_extension_link as extensionLink
import kotlinx.android.synthetic.main.screen_replace_extension_qr.replace_extension_progress_bar as progressBar

class ReplaceExtensionQrActivity : ViewModelActivity<PairingAuthenticatorContract>() {

    override fun layout() = R.layout.screen_replace_extension_qr

    override fun inject(component: ViewComponent) = viewComponent().inject(this)

    override fun screenId() = ScreenId.REPLACE_BROWSER_EXTENSION_QR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val safeAddress = intent.extras.getString(EXTRA_SAFE_ADDRESS)?.asEthereumAddress()
        safeAddress?.let {
            viewModel.setup(it)
        } ?: run {
            finish()
            return
        }

        viewModel.observableState.observe(this, Observer {

            onLoading(it.isLoading)

            when(it) {

                is PairingAuthenticatorContract.ViewUpdate.Pairing -> {

                    it.pairingResult?.let {

                        when(it) {

                            is PairingAuthenticatorContract.PairingResult.PairingSuccess -> {
                                toast(R.string.devices_paired_successfuly)
                                startActivity(ReplaceExtensionRecoveryPhraseActivity.createIntent(this, safeAddress!!, it.extension))
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

        extensionLink.apply {
            this.text = SpannableStringBuilder(Html.fromHtml(getString(R.string.pairing_first_step)))
            setOnClickListener { shareExternalText(BuildConfig.CHROME_EXTENSION_URL, "Browser Extension URL") }
        }
    }

    override fun onStart() {
        super.onStart()

        backArrow.setOnClickListener {
            finish()
        }

        disposables += bottomPanel.forwardClicks.subscribe {
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

        fun createIntent(context: Context, safeAddress: Solidity.Address) = Intent(context, ReplaceExtensionQrActivity::class.java).apply {
            putExtra(EXTRA_SAFE_ADDRESS, safeAddress.asEthereumAddressString())
        }
    }
}
