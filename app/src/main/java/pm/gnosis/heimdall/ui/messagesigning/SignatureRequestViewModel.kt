package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirst
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.eip712.*
import pm.gnosis.heimdall.data.remote.models.push.PushMessage
import pm.gnosis.heimdall.data.repositories.AccountsRepository
import pm.gnosis.heimdall.data.repositories.AddressBookRepository
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.helpers.CryptoHelper
import pm.gnosis.heimdall.helpers.SignatureStore
import pm.gnosis.heimdall.utils.getValue
import pm.gnosis.heimdall.utils.shortChecksumString
import pm.gnosis.heimdall.utils.toJson
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.toHex
import pm.gnosis.utils.toHexString
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

class SignatureRequestViewModel @Inject constructor(
    private val cryptoHelper: CryptoHelper,
    private val eiP712JsonParser: EIP712JsonParser,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val gnosisAccountRepository: AccountsRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val addressBookRepository: AddressBookRepository
    //private val messageSignatureStore: SignatureStore
    ) : SignatureRequestContract() {

    override val viewData: ViewData
        get() = _viewData
    private lateinit var _viewData: ViewData

    private lateinit var safe: Solidity.Address
    private lateinit var safeOwners: Set<Solidity.Address>
    private lateinit var extensionSignature: Signature
    private lateinit var domain: Struct712
    private lateinit var message: Struct712
    private lateinit var payload: String


    private lateinit var payloadHash: ByteArray


    private lateinit var deviceSignature: Signature
    private lateinit var safeMessageHash: ByteArray

    override val state: MutableLiveData<ViewUpdate> = MutableLiveData()


    private val signatures: MutableMap<Solidity.Address, Signature> = HashMap<Solidity.Address, Signature>()


    val confirmationChannel = Channel<PushMessage.SignTypedDataConfirmation>()
    val rejectionChannel = Channel<PushMessage.RejectSignTypedData>()



    private fun onTypedDataConfirmation() {

        viewModelScope.launch {

        }

    }

    init {

        pushServiceRepository.observeTypedDataConfirmationPushes()
            .observeOn(Schedulers.io())
            .map {
                val signature = Signature.from(it.signature.toHexString())
                val payloadHash = it.hash
                signature to payloadHash

            }
            .map { (signature, payloadHash) ->
                val address = cryptoHelper.recover(payloadHash, signature)

                Timber.d("adding signature from ${address.asEthereumAddressChecksumString()}")
                signatures.put(address, signature)
                signature
            }

            .subscribe({

            Timber.d(it.toString())

        }, { Timber.e(it) })
    }


    private val storeSignatureTransformer = ObservableTransformer<Signature, Unit> { signedMessageEvents ->
        signedMessageEvents
            .flatMapSingle { signature ->
                Timber.d(signature.toString())
                Single.just(

                    cryptoHelper.recover(payloadHash, signature)
                )
                    .filter { recoveredAddress -> safeOwners.contains(recoveredAddress) }
                    .map {


                        //signatures.put(Pair(it, signature))

                    }
                    .toSingle()
            }
    }

    override fun setup(payload: String, safe: Solidity.Address, extensionSignature: Signature?) {

        this.safe = safe
        this.payload = payload

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


            payloadHash = typedDataHash(message = message, domain = domain)

            if (extensionSignature == null) {

                val safeMessageStruct = Struct712(
                    typeName = "SafeMessage",
                    parameters = listOf(
                        Struct712Parameter(
                            name = "message",
                            type = Literal712(typeName = "bytes", value = Solidity.Bytes(message.hashStruct()))
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

                safeMessageHash = typedDataHash(message = safeMessageStruct, domain = safeDomain)

                deviceSignature =
                    async { gnosisSafeRepository.sign(safe, safeMessageHash).await() }.await()
                signatures.put(safe, deviceSignature)

                val deviceOwner = async { gnosisAccountRepository.signingOwner(safe).await() }.await()


                _viewData = ViewData(
                    safeAddress = safe,
                    safeName = safeName,
                    safeBalance = safeBalance,
                    domainPayload = domain.toJson("root"),
                    messagePayload = message.toJson("root"),
                    dappAddress = dappAddress,
                    dappName = dappName,
                    status = Status.AUTHORIZATION_REQUIRED
                )

                Timber.d("safe owners ${safeInfo.owners.map { it.asEthereumAddressChecksumString() }}")


                safeOwners = safeInfo.owners.toSet().minus(deviceOwner.address)

                Timber.d("device owner" + deviceOwner.address.asEthereumAddressChecksumString())

                try {
                    pushServiceRepository.requestTypedDataConfirmations(
                        payload,
                        deviceSignature,
                        safe,
                        safeOwners
                    )
                        .subscribeOn(Schedulers.io())
                        .await()
                } catch (e: Exception) {

                    Timber.e(e)

                    liveData<ViewUpdate> {
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
                        false
                    )
                }


            } else {


                this@SignatureRequestViewModel.extensionSignature = extensionSignature!!

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
    }

    override fun resend() {
        viewModelScope.launch {


            //val signatures = async { messageSignatureStore.load().await()}.await()

            val finalSignature = signatures.map {
                it.value.toString().hexStringToByteArray()
            }
                .sortedBy { BigInteger(it) }
                .reduce { acc, bytes ->
                    acc + bytes
                }

            Timber.d("final signature: ${finalSignature.toHexString()}")

//            Timber.d(payload)
//
//            Timber.d(safeOwners.toString())
//
//            try {
//                pushServiceRepository.requestTypedDataConfirmations(
//                    payload,
//                    deviceSignature,
//                    safe,
//                    safeOwners
//                )
//                    .subscribeOn(Schedulers.io())
//                    .await()
//            } catch (e: Exception) {
//
//                Timber.e(e)
//
//                liveData<ViewUpdate> {
//                    state.value = ViewUpdate(
//                        _viewData,
//                        false,
//                        ErrorSendingPush,
//                        false
//                    )
//                }
//            }
//
//
//            liveData<ViewUpdate> {
//                state.value = ViewUpdate(
//                    _viewData,
//                    false,
//                    null,
//                    false
//                )
//            }
        }
    }

    override fun sign() {

        viewModelScope.launch(Dispatchers.IO) {

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

            //val payloadHash = typedDataHash(message = message, domain = domain)

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

    override fun onCleared() {
        super.onCleared()
        confirmationChannel.close()
        rejectionChannel.close()
    }
}





