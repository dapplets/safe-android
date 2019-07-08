package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirst
import pm.gnosis.eip712.*
import pm.gnosis.heimdall.data.repositories.AddressBookRepository
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.helpers.CryptoHelper
import pm.gnosis.heimdall.utils.getValue
import pm.gnosis.heimdall.utils.shortChecksumString
import pm.gnosis.heimdall.utils.toJson
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.utils.hexStringToByteArray
import javax.inject.Inject

class SignatureRequestViewModel @Inject constructor(
    private val cryptoHelper: CryptoHelper,
    private val eiP712JsonParser: EIP712JsonParser,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val addressBookRepository: AddressBookRepository
) : SignatureRequestContract() {

    override val viewData: ViewData
        get() = _viewData
    private lateinit var _viewData: ViewData

    private lateinit var safe: Solidity.Address
    private lateinit var extensionSignature: Signature
    private lateinit var domain: Struct712
    private lateinit var message: Struct712

    override val state: MutableLiveData<ViewUpdate> = MutableLiveData()

    override fun setup(payload: String, safe: Solidity.Address, extensionSignature: Signature) {

        this.safe = safe
        this.extensionSignature = extensionSignature

        viewModelScope.launch(Dispatchers.IO) {

            val domainWithMessage = eiP712JsonParser.parseMessage(payload) ?: throw ConfirmMessageContract.InvalidPayload

            domain = domainWithMessage.domain
            message = domainWithMessage.message

            val (safeName, safeAddress) = async {
                addressBookRepository.observeAddressBookEntry(safe)
                    .map { it.name to it.address.shortChecksumString() }
                    .awaitFirst()
            }.await()


            val safeInfo = async {
                gnosisSafeRepository.loadInfo(safe).awaitFirst()
            }.await()

            val safeBalance = ERC20Token.ETHER_TOKEN.displayString(safeInfo.balance.value)

            val dappName = domain?.parameters?.find { it.name == "name" }?.getValue() as String
            val dappAddress = domain?.parameters?.find { it.name == "verifyingContract" }?.getValue() as Solidity.Address

            _viewData = ViewData(
                safeAddress = safe,
                safeName = safeName,
                safeBalance = safeBalance,
                domainPayload = domain.toJson("root"),
                messagePayload = message.toJson("root"),
                dappAddress = dappAddress,
                dappName = dappName
            )

            async(Dispatchers.Main) {
                state.value = ViewUpdate(
                    _viewData,
                    false,
                    null,
                    false
                )
            }
        }
    }

    override fun confirmPayload() {

        viewModelScope.launch(Dispatchers.IO) {

            async(Dispatchers.Main) {

                state.value = ViewUpdate(
                    _viewData,
                    true,
                    null,
                    false
                )
            }

            val payloadHash = typedDataHash(message = message, domain = domain)

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
                        type = Literal712("address", safe)
                    )
                )
            )

            val safeMessageHash = typedDataHash(message = safeMessageStruct, domain = safeDomain)

            val requester = try {
                cryptoHelper.recover(safeMessageHash, extensionSignature)
            } catch (e: Exception) {
                throw ConfirmMessageContract.ErrorRecoveringSender
            }

            val signatureBytes =
                async { gnosisSafeRepository.sign(safe, safeMessageHash).await() }.await().toString()
                    .hexStringToByteArray()

            try {
                pushServiceRepository.sendTypedDataConfirmation(
                    hash = safeMessageHash,
                    safe = safe,
                    signature = signatureBytes,
                    targets = setOf(requester)
                )
                    .subscribeOn(Schedulers.io())
                    .await()
            } catch (e: Exception) {

                async(Dispatchers.Main) {

                    state.value = ViewUpdate(
                        _viewData,
                        false,
                        ErrorSendingPush,
                        false
                    )
                }
            }

            async(Dispatchers.Main) {

                state.value = ViewUpdate(
                    _viewData,
                    false,
                    null,
                    true
                )
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class DomainMessage(
        @Json(name = "domain") val domain: Map<String, Any>,
        @Json(name = "message") val message: Map<String, Any>
    )
}


