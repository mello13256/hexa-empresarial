package pt.hexa.empresarial

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * The launcher is written in European Portuguese and shows it regardless of the system language
 * (no other language is implemented). Every context the UI uses is wrapped with a pt-PT
 * configuration, which also makes dates, weekdays and numbers follow Portuguese conventions.
 */
object LocaleOverride {
    val PT_PT: Locale = Locale.forLanguageTag("pt-PT")

    fun wrap(base: Context): Context = base.createConfigurationContext(configuration(base.resources.configuration))

    /** A copy of [base] with European Portuguese as the only locale. */
    fun configuration(base: Configuration): Configuration = Configuration(base).apply { setLocales(LocaleList(PT_PT)) }
}
