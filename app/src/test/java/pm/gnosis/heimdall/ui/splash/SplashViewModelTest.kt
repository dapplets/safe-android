package pm.gnosis.heimdall.ui.splash

import io.reactivex.Single
import io.reactivex.observers.TestObserver
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.tests.utils.ImmediateSchedulersRule

@RunWith(MockitoJUnitRunner::class)
class SplashViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var encryptionManagerMock: EncryptionManager

    private lateinit var viewModel: SplashViewModel

    @Before
    fun setup() {
        viewModel = SplashViewModel(encryptionManagerMock)
    }

    @Test
    fun initialSetupWithPasswordAndAccountLocked() {
        given(encryptionManagerMock.initialized()).willReturn(Single.just(true))
        given(encryptionManagerMock.unlocked()).willReturn(Single.just(false))
        val observer = TestObserver.create<ViewAction>()

        viewModel.initialSetup().subscribe(observer)

        then(encryptionManagerMock).should().initialized()
        then(encryptionManagerMock).should().unlocked()
        then(encryptionManagerMock).shouldHaveNoMoreInteractions()
        observer.assertNoErrors().assertTerminated()
            .assertValueCount(1).assertValue { it is StartUnlock }
    }

    @Test
    fun initialSetupWithPasswordAndAccountUnlocked() {
        given(encryptionManagerMock.initialized()).willReturn(Single.just(true))
        given(encryptionManagerMock.unlocked()).willReturn(Single.just(true))
        val observer = TestObserver.create<ViewAction>()

        viewModel.initialSetup().subscribe(observer)

        then(encryptionManagerMock).should().initialized()
        then(encryptionManagerMock).should().unlocked()
        then(encryptionManagerMock).shouldHaveNoMoreInteractions()
        observer.assertNoErrors().assertTerminated()
            .assertValueCount(1).assertValue { it is StartMain }
    }

    @Test
    fun initialSetuUnlockError() {
        val observerNoSuchElement = TestObserver.create<ViewAction>()
        given(encryptionManagerMock.initialized()).willReturn(Single.just(true))
        given(encryptionManagerMock.unlocked()).willReturn(Single.error(IllegalStateException()))

        viewModel.initialSetup().subscribe(observerNoSuchElement)

        then(encryptionManagerMock).should().initialized()
        then(encryptionManagerMock).should().unlocked()
        then(encryptionManagerMock).shouldHaveNoMoreInteractions()
        observerNoSuchElement.assertNoErrors().assertTerminated()
            .assertValueCount(1).assertValue { it is StartUnlock }
    }

    @Test
    fun initialSetupNotInitialized() {
        given(encryptionManagerMock.initialized()).willReturn(Single.just(false))
        val observer = TestObserver.create<ViewAction>()

        viewModel.initialSetup().subscribe(observer)

        then(encryptionManagerMock).should().initialized()
        then(encryptionManagerMock).shouldHaveNoMoreInteractions()
        observer.assertNoErrors().assertTerminated()
            .assertValueCount(1).assertValue { it is StartPasswordSetup }
    }
}
