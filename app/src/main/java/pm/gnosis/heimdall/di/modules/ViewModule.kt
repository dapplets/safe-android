package pm.gnosis.heimdall.di.modules

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pm.gnosis.heimdall.di.ForView
import pm.gnosis.heimdall.di.ViewContext
import pm.gnosis.heimdall.ui.addressbook.AddressBookContract
import pm.gnosis.heimdall.ui.two_factor.ConnectAuthenticatorContract
import pm.gnosis.heimdall.ui.debugsettings.DebugSettingsContract
import pm.gnosis.heimdall.ui.deeplinks.DeeplinkContract
import pm.gnosis.heimdall.ui.dialogs.ens.EnsInputContract
import pm.gnosis.heimdall.ui.two_factor.keycard.KeycardCredentialsContract
import pm.gnosis.heimdall.ui.two_factor.keycard.KeycardInitializeContract
import pm.gnosis.heimdall.ui.two_factor.keycard.KeycardPairingContract
import pm.gnosis.heimdall.ui.two_factor.keycard.KeycardSigningContract
import pm.gnosis.heimdall.ui.messagesigning.ConfirmMessageContract
import pm.gnosis.heimdall.ui.modules.dapplet.DappletContract
import pm.gnosis.heimdall.ui.onboarding.fingerprint.FingerprintSetupContract
import pm.gnosis.heimdall.ui.onboarding.password.PasswordSetupContract
import pm.gnosis.heimdall.ui.recoveryphrase.ConfirmRecoveryPhraseContract
import pm.gnosis.heimdall.ui.recoveryphrase.SetupRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.pairing.remove.Remove2FaRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.create.CreateSafeConfirmRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.create.CreateSafePaymentTokenContract
import pm.gnosis.heimdall.ui.safe.details.SafeDetailsContract
import pm.gnosis.heimdall.ui.safe.details.transactions.SafeTransactionsContract
import pm.gnosis.heimdall.ui.safe.main.SafeMainContract
import pm.gnosis.heimdall.ui.safe.pairing.PairingStartContract
import pm.gnosis.heimdall.ui.safe.pairing.PairingSubmitContract
import pm.gnosis.heimdall.ui.safe.pending.DeploySafeProgressContract
import pm.gnosis.heimdall.ui.safe.pending.SafeCreationFundContract
import pm.gnosis.heimdall.ui.safe.pairing.replace.Replace2FaRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.recover.recoveryphrase.ConfirmNewRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.recover.recoveryphrase.ScanExtensionAddressContract
import pm.gnosis.heimdall.ui.safe.recover.recoveryphrase.SetupNewRecoveryPhraseIntroContract
import pm.gnosis.heimdall.ui.safe.recover.safe.CheckSafeContract
import pm.gnosis.heimdall.ui.safe.recover.safe.RecoverSafeRecoveryPhraseContract
import pm.gnosis.heimdall.ui.safe.recover.safe.submit.RecoveringSafeContract
import pm.gnosis.heimdall.ui.safe.upgrade.UpgradeMasterCopyContract
import pm.gnosis.heimdall.ui.security.unlock.UnlockContract
import pm.gnosis.heimdall.ui.settings.general.GeneralSettingsContract
import pm.gnosis.heimdall.ui.settings.general.changepassword.ChangePasswordContract
import pm.gnosis.heimdall.ui.splash.SplashContract
import pm.gnosis.heimdall.ui.tokens.balances.TokenBalancesContract
import pm.gnosis.heimdall.ui.tokens.manage.ManageTokensContract
import pm.gnosis.heimdall.ui.tokens.payment.PaymentTokensContract
import pm.gnosis.heimdall.ui.tokens.receive.ReceiveTokenContract
import pm.gnosis.heimdall.ui.transactions.create.CreateAssetTransferContract
import pm.gnosis.heimdall.ui.transactions.view.details.MultiSendDetailsContract
import pm.gnosis.heimdall.ui.transactions.view.confirm.ConfirmTransactionContract
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionContract
import pm.gnosis.heimdall.ui.transactions.view.status.TransactionStatusContract
import pm.gnosis.heimdall.ui.two_factor.authenticator.PairingAuthenticatorContract
import pm.gnosis.heimdall.ui.walletconnect.intro.WalletConnectIntroContract
import pm.gnosis.heimdall.ui.walletconnect.link.WalletConnectLinkContract
import pm.gnosis.heimdall.ui.walletconnect.sessions.WalletConnectSessionsContract

@Module
class ViewModule(val context: Context, val viewModelProvider: Any? = null) {
    @Provides
    @ForView
    @ViewContext
    fun providesContext() = context

    @Provides
    @ForView
    fun providesLinearLayoutManager() = LinearLayoutManager(context)

    @Provides
    @ForView
    fun providesAddressBookContract(provider: ViewModelProvider) = provider[AddressBookContract::class.java]

    @Provides
    @ForView
    fun providesChangePasswordContract(provider: ViewModelProvider) = provider[ChangePasswordContract::class.java]

    @Provides
    @ForView
    fun providesCheckSafeContract(provider: ViewModelProvider) = provider[CheckSafeContract::class.java]

    @Provides
    @ForView
    fun providesConfirmMessageContract(provider: ViewModelProvider) = provider[ConfirmMessageContract::class.java]

    @Provides
    @ForView
    fun providesConfirmSafeRecoveryPhraseContract(provider: ViewModelProvider) = provider[ConfirmRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesConfirmTransactionContract(provider: ViewModelProvider) = provider[ConfirmTransactionContract::class.java]

    @Provides
    @ForView
    fun providesConnectAuthenticatorContract(provider: ViewModelProvider) = provider[ConnectAuthenticatorContract::class.java]

    @Provides
    @ForView
    fun providesCreateAssetTransferContract(provider: ViewModelProvider) = provider[CreateAssetTransferContract::class.java]

    @Provides
    @ForView
    fun providesCreateSafeConfirmSetupRecoveryPhraseContract(provider: ViewModelProvider) =
        provider[CreateSafeConfirmRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesCreateSafePaymentTokenContract(provider: ViewModelProvider) = provider[CreateSafePaymentTokenContract::class.java]

    @Provides
    @ForView
    fun providesDebugSettingsContract(provider: ViewModelProvider) = provider[DebugSettingsContract::class.java]

    @Provides
    @ForView
    fun providesDeeplinkContract(provider: ViewModelProvider) = provider[DeeplinkContract::class.java]

    @Provides
    @ForView
    fun providesDeploySafeProgressContract(provider: ViewModelProvider) = provider[DeploySafeProgressContract::class.java]

    @Provides
    @ForView
    fun providesEnsInputContract(provider: ViewModelProvider) = provider[EnsInputContract::class.java]

    @Provides
    @ForView
    fun providesFingerprintSetupContract(provider: ViewModelProvider) = provider[FingerprintSetupContract::class.java]

    @Provides
    @ForView
    fun providesGeneralSettingsContract(provider: ViewModelProvider) = provider[GeneralSettingsContract::class.java]

    // DPL10 Again boilerplate... x_x
    @Provides
    @ForView
    fun providesDappletContract(provider: ViewModelProvider) = provider[DappletContract::class.java]

    @Provides
    @ForView
    fun providesKeycardCredentialsContract(provider: ViewModelProvider) = provider[KeycardCredentialsContract::class.java]

    @Provides
    @ForView
    fun providesKeycardInitializeContract(provider: ViewModelProvider) = provider[KeycardInitializeContract::class.java]

    @Provides
    @ForView
    fun providesKeycardPairingContract(provider: ViewModelProvider) = provider[KeycardPairingContract::class.java]

    @Provides
    @ForView
    fun providesKeycardSigningContract(provider: ViewModelProvider) = provider[KeycardSigningContract::class.java]

    @Provides
    @ForView
    fun providesManageTokensContract(provider: ViewModelProvider) = provider[ManageTokensContract::class.java]

    @Provides
    @ForView
    fun providesMultiSendDetailsContract(provider: ViewModelProvider) = provider[MultiSendDetailsContract::class.java]

    @Provides
    @ForView
    fun providesNewConfirmRecoveryPhraseContract(provider: ViewModelProvider) = provider[ConfirmNewRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesPasswordSetupContract(provider: ViewModelProvider) = provider[PasswordSetupContract::class.java]

    @Provides
    @ForView
    fun providesPaymentTokensContract(provider: ViewModelProvider) = provider[PaymentTokensContract::class.java]

    @Provides
    @ForView
    fun providesReceiveTokenContract(provider: ViewModelProvider) = provider[ReceiveTokenContract::class.java]

    @Provides
    @ForView
    fun providesPairingAuthenticatorContract(provider: ViewModelProvider) = provider[PairingAuthenticatorContract::class.java]

    @Provides
    @ForView
    fun providesPairingStartContract(provider: ViewModelProvider) = provider[PairingStartContract::class.java]

    @Provides
    @ForView
    fun providesPairingSubmitContract(provider: ViewModelProvider) = provider[PairingSubmitContract::class.java]

    @Provides
    @ForView
    fun providesReplaceExtensionRecoveryPhraseContract(provider: ViewModelProvider) =
        provider[Replace2FaRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesRemoveExtensionRecoveryPhraseContract(provider: ViewModelProvider) =
        provider[Remove2FaRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesRecoveringSafeContract(provider: ViewModelProvider) = provider[RecoveringSafeContract::class.java]

    @Provides
    @ForView
    fun providesRecoverSafeRecoveryPhraseContract(provider: ViewModelProvider) = provider[RecoverSafeRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesReviewTransactionContract(provider: ViewModelProvider) = provider[ReviewTransactionContract::class.java]

    @Provides
    @ForView
    fun providesSafeCreationFundContract(provider: ViewModelProvider) = provider[SafeCreationFundContract::class.java]

    @Provides
    @ForView
    fun providesSafeDetailsContract(provider: ViewModelProvider) = provider[SafeDetailsContract::class.java]

    @Provides
    @ForView
    fun providesSafeMainContract(provider: ViewModelProvider) = provider[SafeMainContract::class.java]

    @Provides
    @ForView
    fun providesSafeTransactionsContract(provider: ViewModelProvider) = provider[SafeTransactionsContract::class.java]

    @Provides
    @ForView
    fun providesScanExtensionAddressContract(provider: ViewModelProvider) = provider[ScanExtensionAddressContract::class.java]

    @Provides
    @ForView
    fun providesSetupRecoveryPhraseContract(provider: ViewModelProvider) = provider[SetupRecoveryPhraseContract::class.java]

    @Provides
    @ForView
    fun providesSetupNewRecoveryPhraseIntroContract(provider: ViewModelProvider) = provider[SetupNewRecoveryPhraseIntroContract::class.java]

    @Provides
    @ForView
    fun providesSplashContract(provider: ViewModelProvider) = provider[SplashContract::class.java]

    @Provides
    @ForView
    fun providesTokenBalancesContract(provider: ViewModelProvider) = provider[TokenBalancesContract::class.java]

    @Provides
    @ForView
    fun providesTransactionStatusContract(provider: ViewModelProvider) = provider[TransactionStatusContract::class.java]

    @Provides
    @ForView
    fun providesUnlockContract(provider: ViewModelProvider) = provider[UnlockContract::class.java]

    @Provides
    @ForView
    fun provideUpgradeMasterCopyContract(provider: ViewModelProvider) = provider[UpgradeMasterCopyContract::class.java]

    @Provides
    @ForView
    fun providesWalletConnectIntroContract(provider: ViewModelProvider) = provider[WalletConnectIntroContract::class.java]

    @Provides
    @ForView
    fun providesWalletConnectLinkContract(provider: ViewModelProvider) = provider[WalletConnectLinkContract::class.java]

    @Provides
    @ForView
    fun providesWalletConnectSessionsContract(provider: ViewModelProvider) = provider[WalletConnectSessionsContract::class.java]

    @Provides
    @ForView
    fun providesViewModelProvider(factory: ViewModelProvider.Factory): ViewModelProvider {
        return when (val provider = viewModelProvider ?: context) {
            is Fragment -> ViewModelProviders.of(provider, factory)
            is FragmentActivity -> ViewModelProviders.of(provider, factory)
            else -> throw IllegalArgumentException("Unsupported context $provider")
        }
    }
}
