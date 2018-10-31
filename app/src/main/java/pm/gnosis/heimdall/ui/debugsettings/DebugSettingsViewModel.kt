package pm.gnosis.heimdall.ui.debugsettings

import com.squareup.moshi.Moshi
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.heimdall.data.remote.models.push.PushServiceTemporaryAuthorization
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.ui.safe.main.SafeMainViewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.utils.asEthereumAddress
import java.math.BigInteger
import javax.inject.Inject

class DebugSettingsViewModel @Inject constructor(
    private val moshi: Moshi,
    private val pushServiceRepository: PushServiceRepository,
    private val preferencesManager: PreferencesManager
) : DebugSettingsContract() {
    override fun forceSyncAuthentication() = pushServiceRepository.syncAuthentication(true)

    override fun pair(payload: String): Single<Solidity.Address> =
        parseChromeExtensionPayload(payload)
            .flatMap { pushServiceRepository.pair(it) }

    private fun parseChromeExtensionPayload(payload: String): Single<PushServiceTemporaryAuthorization> =
        Single.fromCallable { moshi.adapter(PushServiceTemporaryAuthorization::class.java).fromJson(payload)!! }
            .subscribeOn(Schedulers.io())

    override fun sendTestSafeCreationPush(chromeExtensionAddress: String): Single<Result<Unit>> =
        Single.fromCallable {
            preferencesManager.prefs.getString(KEY_SELECTED_SAFE, null)?.asEthereumAddress()
        }.flatMapCompletable {
            pushServiceRepository.propagateSafeCreation(it, setOf(chromeExtensionAddress.asEthereumAddress()!!))
        }
            .mapToResult()

    companion object {
        private const val KEY_SELECTED_SAFE = "safe_main.string.selected_safe"
    }
}
