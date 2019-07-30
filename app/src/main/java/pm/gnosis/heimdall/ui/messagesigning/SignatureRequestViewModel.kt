package pm.gnosis.heimdall.ui.messagesigning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.awaitFirst
import kotlinx.coroutines.rx2.openSubscription
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.eip712.*
import pm.gnosis.heimdall.data.remote.models.push.PushMessage
import pm.gnosis.heimdall.data.repositories.AccountsRepository
import pm.gnosis.heimdall.data.repositories.AddressBookRepository
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.impls.RpcProxyApi
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.helpers.CryptoHelper
import pm.gnosis.heimdall.utils.getValue
import pm.gnosis.heimdall.utils.shortChecksumString
import pm.gnosis.heimdall.utils.toJson
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.toBinaryString
import pm.gnosis.utils.toHexString
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

class SignatureRequestViewModel @Inject constructor(
    private val cryptoHelper: CryptoHelper,
    private val eiP712JsonParser: EIP712JsonParser,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val encryptionManager: EncryptionManager,
    private val gnosisAccountRepository: AccountsRepository,
    private val pushServiceRepository: PushServiceRepository,
    private val addressBookRepository: AddressBookRepository,
    private val rpcProxyApi: RpcProxyApi
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


    private lateinit var deviceSignature: Signature
    private lateinit var safeMessageHash: ByteArray

    override val state: MutableLiveData<ViewUpdate> = MutableLiveData()

    private val signatures: MutableMap<Solidity.Address, Signature> = HashMap<Solidity.Address, Signature>()

    val pushChannel: ReceiveChannel<PushMessage>

    private val errorHandler = CoroutineExceptionHandler { _, e ->
        viewModelScope.launch {

        }
    }

    suspend fun handlePushMessages() = coroutineScope {

        val message = pushChannel.receive()
        when (message) {
            is PushMessage.SignTypedDataConfirmation -> {
                val signature = Signature.from(message.signature.toHexString())
                val payloadHash = message.hash

                val address = cryptoHelper.recover(payloadHash, signature)
                if (safeOwners.contains(address)) {
                    Timber.d("adding signature from ${address.asEthereumAddressChecksumString()}")
                    signatures.put(address, signature)
                }

                _viewData = _viewData.copy(
                    status = Status.AUTHORIZATION_APPROVED
                )

                state.postValue(
                    ViewUpdate(
                        _viewData,
                        false,
                        null,
                        false
                    )
                )

            }
            is PushMessage.RejectSignTypedData -> {

                _viewData = _viewData.copy(
                    status = Status.AUTHORIZATION_REJECTED
                )

                state.postValue(
                    ViewUpdate(
                        _viewData,
                        false,
                        null,
                        false
                    )
                )

            }
            else -> {
                // handling only sign typed data pushes
            }
        }
    }

    init {
        pushChannel = pushServiceRepository.observeTypedDataPushes().openSubscription()
        viewModelScope.launch {
            handlePushMessages()
        }
    }


    override fun setup(payload: String, safe: Solidity.Address, extensionSignature: Signature?) {

        viewModelScope.launch(Dispatchers.IO) {

            this@SignatureRequestViewModel.safe = safe
            this@SignatureRequestViewModel.payload = payload

            val domainWithMessage = eiP712JsonParser.parseMessage(payload) ?: throw InvalidPayload

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

            val deviceOwner = async { gnosisAccountRepository.signingOwner(safe).await() }.await()
            safeOwners = safeInfo.owners.toSet().minus(deviceOwner.address)

            val safeBalance = ERC20Token.ETHER_TOKEN.displayString(safeInfo.balance.value)

            val dappName = domain?.parameters?.find { it.name == "name" }?.getValue() as String
            val dappAddress = domain?.parameters?.find { it.name == "verifyingContract" }?.getValue() as Solidity.Address


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


            safeMessageHash = typedDataHash(message = safeMessageStruct, domain = safeDomain)

            deviceSignature = cryptoHelper.sign(deviceOwner.privateKey.value(encryptionManager), safeMessageHash)
               // async { gnosisSafeRepository.sign(safe, safeMessageHash).await() }.await()
            signatures.put(deviceOwner.address, deviceSignature)



            Timber.d("safe owners ${safeInfo.owners.map { it.asEthereumAddressChecksumString() }}")


            Timber.d("device owner" + deviceOwner.address.asEthereumAddressChecksumString())

            if (extensionSignature == null) {

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

                    state.postValue(
                        ViewUpdate(
                            _viewData,
                            false,
                            ErrorSendingPush,
                            false
                        )
                    )

                }

                state.postValue(
                    ViewUpdate(
                        _viewData,
                        false,
                        null,
                        false
                    )
                )

            } else {


                this@SignatureRequestViewModel.extensionSignature = extensionSignature

                _viewData = ViewData(
                    safeAddress = safe,
                    safeName = safeName,
                    safeBalance = safeBalance,
                    domainPayload = domain.toJson("root"),
                    messagePayload = message.toJson("root"),
                    dappAddress = dappAddress,
                    dappName = dappName
                )

                state.postValue(
                    ViewUpdate(
                        _viewData,
                        false,
                        null,
                        false
                    )
                )

            }
        }
    }

    override fun resend() {
        viewModelScope.launch(Dispatchers.IO) {


            val method = Sha3Utils.keccak("isValidSignature(bytes,bytes)".toByteArray()).toHexString()
            Timber.d(method)


            Timber.d(safeMessageHash.toBinaryString())

            //safeMessageHash.toHexString().length / 2


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
        }
    }

    override fun sign() {

        viewModelScope.launch(Dispatchers.IO) {


            val finalSignature = signatures.map {
                it.value.toString().hexStringToByteArray()
            }
                .sortedBy { BigInteger(it) }
                .reduce { acc, bytes ->
                    acc + bytes
                }

            Timber.d("final signature: ${finalSignature.toHexString()}")
            Timber.d("safe message hash: ${safeMessageHash.toHexString()}")

            val response = mapOf("hash" to safeMessageHash.toHexString(), "signature" to finalSignature.toHexString())

            try {
                rpcProxyApi.proxy(RpcProxyApi.ProxiedRequest("eth_call", response.toList(), 1))
                    .subscribeBy(onError = { t ->

                    }) { result ->
                        Timber.d(result.error?.message)
                    }
            } catch (e: Exception) {
                Timber.e(e)
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

            val requester = try {
                cryptoHelper.recover(safeMessageHash, extensionSignature)
            } catch (e: Exception) {
                throw ErrorRecoveringSender
            }

            try {
                pushServiceRepository.sendTypedDataConfirmation(
                    hash = safeMessageHash,
                    safe = safe,
                    signature = deviceSignature.toString().hexStringToByteArray(),
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
        pushChannel.cancel()
    }
}





