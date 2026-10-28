package pt.hexa.empresarial.domain.persist

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Observable in-memory value persisted through a [StateStore]. Changes are visible immediately;
 * disk writes are debounced, happen off the main thread and are skipped when nothing changed.
 */
class DebouncedState<T>(
    private val store: StateStore,
    private val initial: T,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val debounceMs: Long = 500,
) {
    private val _value = MutableStateFlow(initial)
    val flow: StateFlow<T> = _value.asStateFlow()
    val value: T get() = _value.value

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Set when the main file was unreadable and the backup (or defaults) had to be used. */
    var recovered: Boolean = false
        private set
    var hadSavedState: Boolean = false
        private set

    private val lock = Mutex()
    private var written: String? = null
    private var pending: Job? = null

    @Volatile
    var writes: Int = 0
        private set

    suspend fun load() {
        val loaded = withContext(io) { store.load(decode) }
        recovered = loaded.recoveredFromCorruption
        hadSavedState = loaded.value != null
        loaded.value?.let {
            _value.value = it
            written = withContext(io) { encode(it) }
        }
        _ready.value = true
    }

    fun update(transform: (T) -> T) {
        val before = _value.value
        val after = transform(before)
        if (after == before) return
        _value.value = after
        pending?.cancel()
        pending = scope.launch {
            delay(debounceMs)
            persist()
        }
    }

    fun set(value: T) = update { value }

    suspend fun flush() {
        pending?.cancel()
        pending = null
        persist()
    }

    private suspend fun persist() {
        val snapshot = _value.value
        lock.withLock {
            withContext(io) {
                val text = encode(snapshot)
                if (text != written) {
                    store.write(text)
                    written = text
                    writes++
                }
            }
        }
    }
}
