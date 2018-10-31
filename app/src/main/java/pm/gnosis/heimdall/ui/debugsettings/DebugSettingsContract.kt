package pm.gnosis.heimdall.ui.debugsettings

import androidx.lifecycle.ViewModel
import io.reactivex.Single
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result

abstract class DebugSettingsContract : ViewModel() {
    abstract fun forceSyncAuthentication()
    abstract fun pair(payload: String): Single<Solidity.Address>
    abstract fun sendTestSafeCreationPush(chromeExtensionAddress: String): Single<Result<Unit>>
}
