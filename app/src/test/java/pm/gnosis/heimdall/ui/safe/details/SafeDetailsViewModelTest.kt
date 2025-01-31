package pm.gnosis.heimdall.ui.safe.details

import android.content.Context
import android.graphics.Bitmap
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.subscribers.TestSubscriber
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.*
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.QrCodeGenerator
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.tests.utils.ImmediateSchedulersRule
import pm.gnosis.tests.utils.MockUtils
import java.math.BigInteger

@RunWith(MockitoJUnitRunner::class)
class SafeDetailsViewModelTest {
    @JvmField
    @Rule
    val rule = ImmediateSchedulersRule()

    @Mock
    private lateinit var contextMock: Context

    @Mock
    private lateinit var safeRepository: GnosisSafeRepository

    @Mock
    private lateinit var qrCodeGeneratorMock: QrCodeGenerator

    private lateinit var viewModel: SafeDetailsViewModel

    private var testAddress = Solidity.Address(BigInteger.ZERO)

    @Before
    fun setup() {
        viewModel = SafeDetailsViewModel(contextMock, safeRepository, qrCodeGeneratorMock)
    }

    @Test
    fun testSetup() {
        given(safeRepository.observeSafe(MockUtils.any())).willReturn(Flowable.just(Safe(testAddress)))

        viewModel.setup(testAddress)
        viewModel.observeSafe().subscribe(TestSubscriber())

        then(safeRepository).should().observeSafe(testAddress)
    }

    @Test
    fun observeSafe() {
        val testSubscriber = TestSubscriber<Safe>()
        val safe = Safe(testAddress)
        given(safeRepository.observeSafe(MockUtils.any())).willReturn(Flowable.just(safe))
        viewModel.setup(testAddress)

        viewModel.observeSafe().subscribe(testSubscriber)

        testSubscriber.assertValue(safe).assertNoErrors()
    }

    @Test
    fun observeSafeError() {
        val testSubscriber = TestSubscriber<Safe>()
        val exception = Exception()
        given(safeRepository.observeSafe(MockUtils.any())).willReturn(Flowable.error(exception))
        viewModel.setup(testAddress)

        viewModel.observeSafe().subscribe(testSubscriber)

        testSubscriber.assertError(exception)
    }

    @Test
    fun loadQrCode() {
        val bitmapMock = mock(Bitmap::class.java)
        val contents = "contents"
        val testObserver = TestObserver.create<Result<Bitmap>>()
        given(qrCodeGeneratorMock.generateQrCode(anyString(), anyInt(), anyInt(), anyInt())).willReturn(Single.just(bitmapMock))

        viewModel.loadQrCode(contents).subscribe(testObserver)

        then(qrCodeGeneratorMock).should().generateQrCode(contents)
        then(qrCodeGeneratorMock).shouldHaveNoMoreInteractions()
        testObserver.assertValue(DataResult(bitmapMock)).assertNoErrors()
    }

    @Test
    fun loadQrCodeError() {
        val exception = Exception()
        val contents = "contents"
        val testObserver = TestObserver.create<Result<Bitmap>>()
        given(qrCodeGeneratorMock.generateQrCode(anyString(), anyInt(), anyInt(), anyInt())).willReturn(Single.error(exception))

        viewModel.loadQrCode(contents).subscribe(testObserver)

        then(qrCodeGeneratorMock).should().generateQrCode(contents)
        then(qrCodeGeneratorMock).shouldHaveNoMoreInteractions()
        testObserver.assertValue(ErrorResult(exception)).assertNoErrors()
    }
}
