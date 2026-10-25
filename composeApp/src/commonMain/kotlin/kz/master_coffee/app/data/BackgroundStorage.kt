package kz.master_coffee.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kz.master_coffee.app.domain.CoffeeFailure
import kz.master_coffee.app.domain.FailureKind

/** One serialization boundary for legacy and durable storage. No platform I/O runs on Main. */
class BackgroundStorage(private val dispatcher: CoroutineDispatcher) {
    private val mutex = Mutex()
    suspend fun <T> run(block: () -> T): T = withContext(dispatcher) {
        mutex.withLock {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: CoffeeFailure) { throw e }
            catch (_: Exception) { throw CoffeeFailure(FailureKind.STORAGE, "storage_unavailable") }
        }
    }
}
