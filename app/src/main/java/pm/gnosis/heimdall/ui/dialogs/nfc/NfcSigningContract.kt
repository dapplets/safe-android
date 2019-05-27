package pm.gnosis.heimdall.ui.dialogs.nfc

import android.nfc.NfcAdapter
import android.util.Log
import androidx.lifecycle.ViewModel
import im.status.keycard.android.NFCCardManager
import im.status.keycard.applet.*
import im.status.keycard.io.APDUCommand
import im.status.keycard.io.CardChannel
import im.status.keycard.io.CardListener
import io.reactivex.Observable
import pm.gnosis.ethereum.EthereumRepository
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.toHexString
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject


abstract class NfcSigningContract : ViewModel() {
    abstract fun setup(hash: String)

    abstract fun observeNfc(): Observable<Result<Unit>>

    abstract fun callback(): NfcAdapter.ReaderCallback
}

class NfcSigningViewModel @Inject constructor() : NfcSigningContract(), CardListener {

    private val listeners: MutableList<CardListener> = CopyOnWriteArrayList()

    override fun onConnected(channel: CardChannel?) {
        listeners.forEach { it.onConnected(channel) }
    }

    override fun onDisconnected() {
        listeners.forEach { it.onDisconnected() }
    }

    private fun addListener(listener: CardListener) =
        listeners.add(listener)

    private fun removeListener(listener: CardListener) =
        listeners.remove(listener)

    private lateinit var hash: String

    override fun setup(hash: String) {
        this.hash = hash
    }

    private val manager = NFCCardManager().apply {
        setCardListener(this@NfcSigningViewModel)
        start()
    }

    override fun callback(): NfcAdapter.ReaderCallback = manager

    override fun observeNfc(): Observable<Result<Unit>> = Observable.create { emitter ->
        val listener = object: CardListener {
            override fun onConnected(channel: CardChannel?) {
                channel ?: return
                try {

                    val cmdSet = object: KeycardCommandSet(channel) {
                        public override fun setSecureChannel(secureChannel: SecureChannelSession?) {
                            super.setSecureChannel(secureChannel)
                        }
                    }
                    val secureChannel = SecureChannelSession()
                    cmdSet.setSecureChannel(secureChannel)
                    var info = channel.send(APDUCommand(0x00, 0xA4, 4, 0, "53746174757357616C6C6574417070".hexToByteArray())).let {
                        if (it.sw != 0x9000) {
                            throw IllegalStateException("Unexpected response: " + it.sw)
                        }
                        val info = ApplicationInfo(it.data)
                        if (info.hasSecureChannelCapability()) {
                            secureChannel.generateSecret(info.secureChannelPubKey)
                            secureChannel.reset()
                        }
                        info
                    }

                    if (!info.isInitializedCard) {
                        Log.d("######", "Initializing card with test secrets")
                        cmdSet.init("000000", "123456789012", "KeycardTest").checkOK()
                        info = ApplicationInfo(cmdSet.select().checkOK().data)
                    }
                    cmdSet.verifyPIN("000000")
                    if (info.hasSecureChannelCapability()) {
                        cmdSet.autoPair("KeycardTest")
                    }
                    // We retrieve the wallet public key

                    val walletPublicKey = BIP32KeyPair.fromTLV(cmdSet.exportCurrentKey(true).checkOK().data)

                    Log.d("######", "Wallet address: " + walletPublicKey.toEthereumAddress().toHexString())


                    val hashBytes = hash.hexStringToByteArray()

                    val signature = RecoverableSignature(hashBytes, cmdSet.sign(hashBytes).checkOK().data)

                    Log.d("######", "Signed hash: " + hashBytes.toHexString())
                    Log.d("######", "Recovery ID: " + signature.recId)
                    Log.d("######", "R: " + signature.r.toHexString())
                    Log.d("######", "S: " + signature.s.toHexString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onDisconnected() {
                Log.d("######", "Card disconnected.")
            }

        }
        addListener(listener)
        emitter.setCancellable { removeListener(listener) }
    }
}
