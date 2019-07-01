package pm.gnosis.heimdall.ui.messagesigning

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_root as root
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_barrier_bottom_panel as bottomPanel
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_safe_address as safeAddressLabel
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_safe_name as safeNameLabel
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_safe_image as safeImage
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_safe_balance as safeBalance

import kotlinx.android.synthetic.main.screen_signature_request.signature_request_dapp_name as dappNameLabel
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_dapp_contract_address as dappAddressLabel
import kotlinx.android.synthetic.main.screen_signature_request.signature_request_dapp_contract_img as dappImage

import kotlinx.android.synthetic.main.screen_signature_request.signature_request_show_message as showMessageBtn
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.model.Solidity
import pm.gnosis.model.SolidityBase
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.nullOnThrow

class SignatureRequestActivity : ViewModelActivity<SignatureRequestContract>() {

    override fun screenId() = ScreenId.SIGNATURE_REQUEST_REVIEW

    override fun layout() = R.layout.screen_signature_request

    override fun inject(component: ViewComponent) =
        component.inject(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val payload = intent.getStringExtra(PAYLOAD_EXTRA) ?: run { finish(); return }
        val safe = intent.getStringExtra(SAFE_EXTRA)?.asEthereumAddress() ?: run { finish(); return }
        val extensionSignature = intent.getStringExtra(EXTENSION_SIGNATURE_EXTRA)?.let { Signature.from(it) } ?: run { finish(); return }


        viewModel.setup(payload, safe, extensionSignature)
        viewModel.state.observe(this, Observer {
            onViewUpdate(it)
        })


        bottomPanel.disabled = false
        safeAddressLabel.text = safe.asEthereumAddressChecksumString()
        safeImage.setAddress(safe)


        (viewModel as SignatureRequestViewModel).parse()


        bottomPanel.forwardClickAction = {

            viewModel.confirmPayload()
        }

        showMessageBtn.setOnClickListener {
            startActivity(SignatureRequestDetailsActivity.createIntent(this, payload))
        }


    }

    private fun onViewUpdate(viewUpdate: SignatureRequestContract.ViewUpdate) {

        viewUpdate.error?.let { error ->
            snackbar(
                root, when (error) {
                    is SignatureRequestContract.InvalidPayload -> R.string.invalid_eip712_message
                    is SignatureRequestContract.ErrorRecoveringSender -> R.string.error_recovering_message_sender
                    is SignatureRequestContract.ErrorSigningHash -> R.string.error_signing_message
                    is SignatureRequestContract.ErrorSendingPush -> R.string.message_requester_not_paired
                    else -> R.string.unknown_error
                }
            )
        }

        if (viewUpdate.finishProcess) {
            toast(R.string.confirmation_sent)
            finish()
        }
        bottomPanel.disabled = viewUpdate.isLoading

    }
    companion object {

        private const val PAYLOAD_EXTRA = "extra.string.payload"
        private const val SAFE_EXTRA = "extra.string.safe"
        private const val EXTENSION_SIGNATURE_EXTRA = "extra.string.extension_signature"

        fun createIntent(context: Context, payload: String, safe: Solidity.Address, extensionSignature: Signature) =
            Intent(context, SignatureRequestActivity::class.java).apply {
                putExtra(PAYLOAD_EXTRA, payload)
                putExtra(SAFE_EXTRA, safe.asEthereumAddressString())
                putExtra(EXTENSION_SIGNATURE_EXTRA, extensionSignature.toString())
            }
    }
}