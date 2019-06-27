package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import pm.gnosis.eip712.Struct712
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature

abstract class SignatureRequestContract : ViewModel() {

    abstract val state: LiveData<ViewUpdate>

    abstract fun confirmPayload()
    abstract fun setup(payload: String, safe: Solidity.Address, signature: Signature)

    data class ViewUpdate(
        val payload: String,
        val domain: Struct712?,
        val message: Struct712?,
        val isLoading: Boolean,
        val error: Throwable? = null,
        val finishProcess: Boolean = false
    )

    object InvalidPayload : Exception()
    object ErrorRecoveringSender : Exception()
    object ErrorSigningHash : Exception()
    object ErrorSendingPush : Exception()
}