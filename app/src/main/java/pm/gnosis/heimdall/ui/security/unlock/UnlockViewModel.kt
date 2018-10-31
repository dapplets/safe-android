package pm.gnosis.heimdall.ui.security.unlock

import android.content.Context
import android.os.SystemClock
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.di.ApplicationContext
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.edit
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.svalinn.security.FingerprintUnlockResult
import javax.inject.Inject

class UnlockViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val preferencesManager: PreferencesManager,
    private val pushServiceRepository: PushServiceRepository
) : UnlockContract() {

    override fun observeFingerprint(): Observable<Result<FingerprintUnlockResult>> =
        encryptionManager.observeFingerprintForUnlock()
            .mapToResult()

    override fun checkState(forceConfirmCredentials: Boolean) =
        encryptionManager.unlocked()
            .map { canSkipConfirmation(forceConfirmCredentials, it) }
            .flatMap {
                if (it) Single.just(State.UNLOCKED)
                else
                    encryptionManager.initialized().map {
                        if (it)
                            State.LOCKED
                        else
                            State.UNINITIALIZED
                    }
            }
            .subscribeOn(Schedulers.io())
            .toObservable().mapToResult()

    private fun canSkipConfirmation(forceConfirmCredentials: Boolean, unlocked: Boolean) =
        unlocked || (forceConfirmCredentials &&
                // Check if we still are in the time window to skip forced confirmation
                (preferencesManager.prefs.getLong(PREFS_LAST_UNLOCK, 0) + UNLOCK_TIMEOUT_MS) > SystemClock.elapsedRealtime())

    override fun unlock(password: String) =
        encryptionManager.unlockWithPassword(password.toByteArray())
            .map {
                SimpleLocalizedException.assert(it, context, R.string.error_wrong_credentials)
                preferencesManager.prefs.edit { putLong(PREFS_LAST_UNLOCK, SystemClock.elapsedRealtime()) }
                State.UNLOCKED
            }.toObservable().mapToResult()

    override fun syncPushAuthentication() = pushServiceRepository.syncAuthentication(true)

    companion object {
        private const val PREFS_LAST_UNLOCK = "prefs.long.last_unlock"
        private const val UNLOCK_TIMEOUT_MS = 5 * 60 * 1000
    }
}
