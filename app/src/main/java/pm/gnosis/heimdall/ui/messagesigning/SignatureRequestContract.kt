package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature

abstract class SignatureRequestContract : ViewModel() {

    abstract val state: LiveData<ViewUpdate>

    abstract val viewData: ViewData

    abstract fun confirmPayload()
    abstract fun setup(payload: String, safe: Solidity.Address, signature: Signature)

    data class ViewData(
        val safeAddress: Solidity.Address,
        val safeName: String,
        val safeBalance: String,
        val dappName: String,
        val dappAddress: Solidity.Address,
        val domainPayload: String,
        val messagePayload: String
    )

    data class ViewUpdate(
        val viewData: ViewData,
        val isLoading: Boolean,
        val error: Throwable? = null,
        val finishProcess: Boolean = false
    )

    object InvalidPayload : Exception()
    object ErrorRecoveringSender : Exception()
    object ErrorSigningHash : Exception()
    object ErrorSendingPush : Exception()
}