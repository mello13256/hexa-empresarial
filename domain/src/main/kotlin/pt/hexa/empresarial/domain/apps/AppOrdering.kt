package pt.hexa.empresarial.domain.apps

import pt.hexa.empresarial.domain.model.AppPreferences
import pt.hexa.empresarial.domain.model.AppSort
import java.text.Collator
import java.util.Locale

/**
 * Order of apps in the drawers: favourites first (inner orbits), then priority apps, then the rest
 * by the chosen sort. Hidden apps are left out (they remain searchable when [includeHidden]).
 */
object AppOrdering {

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-PT")).apply { strength = Collator.PRIMARY }

    fun order(
        apps: List<AppEntry>,
        prefs: AppPreferences,
        sort: AppSort,
        usage: Map<String, Int> = emptyMap(),
        includeHidden: Boolean = false,
    ): List<AppEntry> {
        val hidden = prefs.hidden.toSet()
        val visible = if (includeHidden) apps else apps.filter { it.key !in hidden }
        val byKey = visible.associateBy { it.key }
        val favorites = prefs.favorites.mapNotNull { byKey[it] }
        val priority = prefs.priority.filter { it !in prefs.favorites }.mapNotNull { byKey[it] }
        val taken = (favorites + priority).map { it.key }.toSet()
        val rest = visible.filter { it.key !in taken }
        val sorted = when (sort) {
            AppSort.ALPHABETICAL -> rest.sortedWith { a, b -> collator.compare(a.label, b.label) }
            AppSort.MOST_USED -> rest.sortedWith(
                compareByDescending<AppEntry> { usage[it.key] ?: 0 }.thenComparator { a, b -> collator.compare(a.label, b.label) },
            )
            AppSort.MANUAL -> {
                val position = prefs.manualOrder.withIndex().associate { it.value to it.index }
                rest.sortedWith(
                    compareBy<AppEntry> { position[it.key] ?: Int.MAX_VALUE }.thenComparator { a, b -> collator.compare(a.label, b.label) },
                )
            }
        }
        return favorites + priority + sorted
    }

    fun alphabetical(apps: List<AppEntry>): List<AppEntry> = apps.sortedWith { a, b -> collator.compare(a.label, b.label) }

    /** Moves [key] one position up (-1) or down (+1) in the manual order (initialised from [current]). */
    fun moveInManualOrder(prefs: AppPreferences, current: List<String>, key: String, delta: Int): AppPreferences {
        val order = (prefs.manualOrder.filter { it in current } + current.filter { it !in prefs.manualOrder }).toMutableList()
        val i = order.indexOf(key)
        if (i < 0) return prefs
        val j = (i + delta).coerceIn(0, order.lastIndex)
        if (i == j) return prefs.copy(manualOrder = order)
        order.removeAt(i)
        order.add(j, key)
        return prefs.copy(manualOrder = order)
    }

    fun toggle(list: List<String>, key: String): List<String> = if (key in list) list - key else list + key
}
