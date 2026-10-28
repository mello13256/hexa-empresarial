package pt.hexa.empresarial.domain.apps

/**
 * Stable text id of a launchable activity: `package/class`, plus `@serial` for other user
 * profiles (work profile).
 */
object AppKey {
    fun of(packageName: String, className: String, userSerial: Long = 0): String =
        if (userSerial == 0L) "$packageName/$className" else "$packageName/$className@$userSerial"

    fun packageName(key: String): String = key.substringBefore('/')

    fun className(key: String): String = key.substringAfter('/').substringBefore('@')

    fun userSerial(key: String): Long = key.substringAfter('@', "0").toLongOrNull() ?: 0L
}

/** Minimal app description shared with the domain layer. */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val isWorkProfile: Boolean = false,
)
