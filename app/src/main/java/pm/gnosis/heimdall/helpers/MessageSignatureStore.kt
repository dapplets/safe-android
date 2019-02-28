package pm.gnosis.heimdall.helpers

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import pm.gnosis.svalinn.accounts.base.models.Signature
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject

interface MessageSignatureStore {
    fun store(signature: Signature)
    fun observe(): Observable<Set<Signature>>
}

class DefaultMessageSignatureStore @Inject constructor() : ObservableOnSubscribe<Set<Signature>>, MessageSignatureStore {
    private val emitters = CopyOnWriteArraySet<ObservableEmitter<Set<Signature>>>()

    private val signatures = HashSet<Signature>()

    private val observable = Observable.create(this)

    override fun store(signature: Signature) {
        signatures.add(signature)
        emitters.forEach { it.onNext(signatures) }
    }

    override fun observe(): Observable<Set<Signature>> = observable.startWith(signatures)

    override fun subscribe(emitter: ObservableEmitter<Set<Signature>>) {
        emitters.add(emitter)
        emitter.setCancellable { emitters.remove(emitter) }
        emitter.onNext(signatures)
    }
}
