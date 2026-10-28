package pt.hexa.empresarial.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import pt.hexa.empresarial.domain.model.LauncherState
import pt.hexa.empresarial.domain.persist.DebouncedState
import pt.hexa.empresarial.domain.persist.StateCodec
import pt.hexa.empresarial.domain.persist.StateStore
import java.io.File

/** The launcher configuration: in memory for the UI, persisted atomically in the background. */
class StateRepository(file: File, scope: CoroutineScope, io: CoroutineDispatcher) {

    private val store = DebouncedState(StateStore(file), LauncherState(), StateCodec::encode, StateCodec::decode, scope, io, 400)

    val flow: StateFlow<LauncherState> get() = store.flow
    val value: LauncherState get() = store.value
    val ready: StateFlow<Boolean> get() = store.ready

    /** True when the saved file was damaged and a backup copy (or defaults) was used instead. */
    val recovered: Boolean get() = store.recovered

    suspend fun load() = store.load()

    fun update(transform: (LauncherState) -> LauncherState) = store.update(transform)

    fun set(value: LauncherState) = store.set(value)

    suspend fun flush() = store.flush()
}

@Serializable
data class UsageCounts(val counts: Map<String, Int> = emptyMap())

/** Launch counts for the "most used" ordering. Kept on the device only. */
class UsageRepository(file: File, scope: CoroutineScope, io: CoroutineDispatcher) {

    private val store = DebouncedState(
        StateStore(file),
        UsageCounts(),
        { StateCodec.json.encodeToString(UsageCounts.serializer(), it) },
        { StateCodec.json.decodeFromString(UsageCounts.serializer(), it) },
        scope, io, 3_000,
    )

    val flow: StateFlow<UsageCounts> get() = store.flow

    suspend fun load() = store.load()

    fun record(key: String) = store.update { u -> UsageCounts(u.counts + (key to ((u.counts[key] ?: 0) + 1))) }

    fun clear() = store.set(UsageCounts())

    suspend fun flush() = store.flush()
}
