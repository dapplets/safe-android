package pm.gnosis.heimdall.ui.dialogs.nfc

import android.app.Dialog
import android.content.DialogInterface
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.jakewharton.rxbinding2.widget.textChanges
import im.status.keycard.android.NFCCardManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.dialog_change_password.view.*
import kotlinx.android.synthetic.main.dialog_ens_input.view.*
import kotlinx.android.synthetic.main.dialog_nfc_signing.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.helpers.AddressHelper
import pm.gnosis.heimdall.reporting.Event
import pm.gnosis.heimdall.reporting.EventTracker
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.dialogs.base.BaseDialog
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.heimdall.utils.CustomAlertDialogBuilder
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.*
import pm.gnosis.svalinn.security.EncryptionManager
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NfcSigningDialog : BaseDialog() {
    @Inject
    lateinit var encryptionManager: EncryptionManager

    @Inject
    lateinit var eventTracker: EventTracker

    @Inject
    lateinit var viewModel: NfcSigningContract

    private lateinit var dialogView: View
    private lateinit var alertDialog: AlertDialog

    private var adapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setStyle(STYLE_NO_FRAME, 0)
        super.onCreate(savedInstanceState)
        inject()

        val hash = arguments?.getString(ARG_TX_HASH) ?: run {
            dismiss()
            return
        }
        viewModel.setup(hash)
        adapter = context?.let { NfcAdapter.getDefaultAdapter(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_nfc_signing, null)
        alertDialog = CustomAlertDialogBuilder.build(context!!, "Sign with NFC", dialogView, 0, null)
        return alertDialog
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onStart() {
        super.onStart()
        eventTracker.submit(Event.ScreenView(ScreenId.INPUT_SAFE_ADDRESS)) // TODO change

        val a = activity ?: run {
            dismiss()
            return
        }
        adapter?.let {
            it.enableReaderMode(a, viewModel.callback(), NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
            disposables += viewModel.observeNfc().subscribeForResult(onNext = {
                dismiss()
            }, onError = { error ->
                dialog_nfc_signing_error.text = when (error) {
                    is SimpleLocalizedException -> error.localizedMessage()
                    else -> getString(R.string.unknown_error)
                }
            })
        } ?: run {
            dismiss()
        }
    }

    override fun onStop() {
        activity?.let { adapter?.disableReaderMode(it) }
        super.onStop()
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .viewModule(ViewModule(context!!))
            .applicationComponent(HeimdallApplication[context!!])
            .build()
            .inject(this)
    }

    companion object {
        private const val ARG_TX_HASH = "arg.string.tx_hash"
        fun create(hash: String) = NfcSigningDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_TX_HASH, hash)
            }
        }
    }
}
