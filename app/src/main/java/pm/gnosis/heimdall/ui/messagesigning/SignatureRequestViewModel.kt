package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.await
import pm.gnosis.eip712.*
import pm.gnosis.heimdall.data.repositories.AddressBookRepository
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.helpers.CryptoHelper
import pm.gnosis.heimdall.utils.shortChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.nullOnThrow
import javax.inject.Inject

class SignatureRequestViewModel @Inject constructor(
    private val cryptoHelper: CryptoHelper,
    private val eiP712JsonParser: EIP712JsonParser,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val addressBookRepository: AddressBookRepository
) : SignatureRequestContract() {

    data class ViewArguments(
        val payload: String,
        val safe: Solidity.Address,
        val signature: Signature,
        val domain: Struct712?,
        val message: Struct712?
    )

    private lateinit var viewArguments: ViewArguments

    override val state: MutableLiveData<ViewUpdate> = MutableLiveData()

    override fun setup(payload: String, safe: Solidity.Address, signature: Signature) {
        viewArguments = ViewArguments(payload = payload, safe = safe, signature = signature, domain = null, message = null)
    }


    suspend fun getSafeData() {
//        gnosisSafeRepository.loadSafe()
//
//        addressBookRepository.observeAddressBookEntry(safe.address())
//            .map { it.name to it.address.shortChecksumString() }
    }

    fun parse() {
        val payloadHash = nullOnThrow { eiP712JsonParser.parseMessage(viewArguments.payload) }
            ?.let {

                viewArguments = viewArguments.copy(domain = it.domain, message = it.message)
                state.value = SignatureRequestContract.ViewUpdate(viewArguments.payload, it.domain, it.message, false, null, false)

                typedDataHash(message = it.message, domain = it.domain)
            }
            ?: throw ConfirmMessageContract.InvalidPayload
    }

    override fun confirmPayload() {

        viewModelScope.launch(Dispatchers.IO) {


            async(Dispatchers.Main) {
                state.value = ViewUpdate(viewArguments.payload, null, null, true, null, false)
            }


            val payloadHash = nullOnThrow { eiP712JsonParser.parseMessage(viewArguments.payload) }
                ?.let {

                    viewArguments = viewArguments.copy(domain = it.domain, message = it.message)

                    async(Dispatchers.Main) {
                        state.value = ViewUpdate(viewArguments.payload, it.domain, it.message, true, null, false)
                    }


                    typedDataHash(message = it.message, domain = it.domain)
                }
                ?: throw ConfirmMessageContract.InvalidPayload

            val safeMessageStruct = Struct712(
                typeName = "SafeMessage",
                parameters = listOf(
                    Struct712Parameter(
                        name = "message",
                        type = Literal712(typeName = "bytes", value = Solidity.Bytes(payloadHash))
                    )
                )
            )

            val safeDomain = Struct712(
                typeName = "EIP712Domain",
                parameters = listOf(
                    Struct712Parameter(
                        name = "verifyingContract",
                        type = Literal712("address", viewArguments.safe)
                    )
                )
            )

            val safeMessageHash = typedDataHash(message = safeMessageStruct, domain = safeDomain)

            val requester = try {
                cryptoHelper.recover(safeMessageHash, viewArguments.signature)
            } catch (e: Exception) {
                throw ConfirmMessageContract.ErrorRecoveringSender
            }

            val signatureBytes = async {  gnosisSafeRepository.signSuspend(viewArguments.safe, safeMessageHash) }.await().toString().hexStringToByteArray()

            try {
                pushServiceRepository.sendTypedDataConfirmation(
                    hash = safeMessageHash,
                    safe = viewArguments.safe,
                    signature = signatureBytes,
                    targets = setOf(requester)
                )
                    .subscribeOn(Schedulers.io())
                    .await()
            } catch (e: Exception) {
                async(Dispatchers.Main) {
                    state.value = ViewUpdate(viewArguments.payload, viewArguments.domain, viewArguments.message, false, ErrorSendingPush, false)
                }

            }

            async(Dispatchers.Main) {
                state.value = ViewUpdate(viewArguments.payload, viewArguments.domain, viewArguments.message, false, null, true)
            }
        }
    }
}