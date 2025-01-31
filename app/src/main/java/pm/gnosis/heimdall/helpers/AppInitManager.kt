package pm.gnosis.heimdall.helpers

import pm.gnosis.heimdall.data.repositories.BridgeRepository
import pm.gnosis.heimdall.data.repositories.ShortcutRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitManager @Inject constructor(
    private val bridgeRepository: BridgeRepository,
    private val shortcutRepository: ShortcutRepository,
    private val transactionTriggerManager: TransactionTriggerManager
) {
    fun init() {
        bridgeRepository.init()
        shortcutRepository.init()
        transactionTriggerManager.init()
    }
}