package pm.gnosis.heimdall.ui.walletconnect

import android.content.Intent
import android.os.Bundle
import android.util.Log
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.BaseActivity

class WalletConnectLinkActivity: BaseActivity() {
    override fun screenId(): ScreenId = ScreenId.WALLET_CONNECT_SESSIONS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                Log.d("#####", it.toString())
                startActivity(WalletConnectSessionsActivity.createIntent(this,it.toString()))
            }
        }
        finish()
    }

}