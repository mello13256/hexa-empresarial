package pt.hexa.empresarial.domain.persist

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Crash-safe storage of one text document.
 *
 * Writes go to a temporary file that is synced and atomically renamed over the real one; the
 * previous version is kept as `.bak`. Reading falls back to the backup when the main file is
 * missing or unreadable, so an interrupted write or a corrupted file never loses the layout.
 * Blocking: call from an IO thread.
 */
class StateStore(private val file: File) {

    private val backup = File(file.parentFile, file.name + ".bak")

    enum class Source { MAIN, BACKUP, NONE }

    data class Loaded<T>(val value: T?, val source: Source, val recoveredFromCorruption: Boolean)

    fun <T> load(decode: (String) -> T): Loaded<T> {
        var corrupted = false
        for ((f, source) in listOf(file to Source.MAIN, backup to Source.BACKUP)) {
            if (!f.exists()) continue
            try {
                return Loaded(decode(f.readText()), source, corrupted)
            } catch (e: Exception) {
                corrupted = true
                f.renameTo(File(f.parentFile, f.name + ".corrompido-" + System.currentTimeMillis()))
            }
        }
        return Loaded(null, Source.NONE, corrupted)
    }

    fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (file.exists()) {
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: Exception) {
                // The backup is best effort; the atomic replace below is what matters.
            }
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
