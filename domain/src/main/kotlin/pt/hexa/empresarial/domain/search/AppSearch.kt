package pt.hexa.empresarial.domain.search

import pt.hexa.empresarial.domain.apps.AppEntry
import java.text.Normalizer

/** Case- and accent-insensitive form used for matching ("Câmara" -> "camara"). */
fun foldForSearch(text: String): String {
    val nfd = Normalizer.normalize(text, Normalizer.Form.NFD)
    val out = StringBuilder(nfd.length)
    var space = true
    for (c in nfd) {
        if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
        if (c.isLetterOrDigit()) {
            out.append(c.lowercaseChar())
            space = false
        } else if (!space) {
            out.append(' ')
            space = true
        }
    }
    return out.toString().trimEnd()
}

/**
 * In-memory search over app labels. Labels are folded once when the index is built; every
 * keystroke only compares strings. Ranking: exact > prefix > word prefix > initials > contains >
 * letters in order.
 */
class AppSearch(apps: List<AppEntry>) {

    private class Entry(val app: AppEntry, val folded: String, val words: List<String>, val initials: String, val compact: String)

    private val entries = apps.map { a ->
        val f = foldForSearch(a.label)
        val words = f.split(' ').filter { it.isNotEmpty() }
        Entry(a, f, words, words.joinToString("") { it.take(1) }, f.replace(" ", ""))
    }

    fun search(query: String, limit: Int = 50): List<AppEntry> {
        val q = foldForSearch(query)
        if (q.isEmpty()) return emptyList()
        val qc = q.replace(" ", "")
        return entries.asSequence()
            .map { it to score(it, q, qc) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Entry, Int>> { it.second }.thenBy { it.first.folded })
            .take(limit)
            .map { it.first.app }
            .toList()
    }

    private fun score(e: Entry, q: String, qc: String): Int = when {
        e.folded == q -> 1000
        e.folded.startsWith(q) -> 900
        e.words.any { it.startsWith(q) } -> 800
        qc.length >= 2 && e.initials.startsWith(qc) -> 700
        e.folded.contains(q) -> 600
        qc.length >= 2 && e.compact.contains(qc) -> 550
        qc.length >= 3 && isSubsequence(qc, e.compact) -> 300
        else -> 0
    }

    private fun isSubsequence(q: String, text: String): Boolean {
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return i == q.length
    }
}
