package pt.hexa.empresarial.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.hexa.empresarial.domain.apps.AppEntry
import pt.hexa.empresarial.domain.apps.AppKey
import pt.hexa.empresarial.domain.apps.AppOrdering
import pt.hexa.empresarial.domain.model.AppPreferences
import pt.hexa.empresarial.domain.model.AppSort
import pt.hexa.empresarial.domain.search.AppSearch
import pt.hexa.empresarial.domain.search.foldForSearch

class AppsAndSearchTest {

    private val apps = listOf("Câmara", "Calendário", "Definições", "Gmail", "Google Maps", "Ábaco", "WhatsApp", "Zoom", "Telefone")
        .mapIndexed { i, l -> AppEntry("p$i/.A", l, "p$i") }

    @Test
    fun keysRoundTrip() {
        val k = AppKey.of("com.x", "com.x.Main", 10)
        assertEquals("com.x", AppKey.packageName(k))
        assertEquals("com.x.Main", AppKey.className(k))
        assertEquals(10L, AppKey.userSerial(k))
        assertEquals(0L, AppKey.userSerial(AppKey.of("a", "b")))
    }

    @Test
    fun searchIsCaseAndAccentInsensitive() {
        val s = AppSearch(apps)
        assertEquals("Câmara", s.search("CAMARA").first().label)
        assertEquals("Definições", s.search("definicoes").first().label)
        assertEquals("Google Maps", s.search("maps").first().label)
        assertEquals("Google Maps", s.search("gm").first { it.label != "Gmail" }.label)
        assertTrue(s.search("   ").isEmpty())
        assertTrue(s.search("xyzw").isEmpty())
    }

    @Test
    fun prefixBeatsContains() {
        val r = AppSearch(apps).search("ca")
        assertEquals(setOf("Câmara", "Calendário"), r.take(2).map { it.label }.toSet())
    }

    @Test
    fun foldNormalises() {
        assertEquals("acao rapida", foldForSearch("  Ação   Rápida! "))
    }

    @Test
    fun alphabeticalUsesPortugueseCollation() {
        val r = AppOrdering.order(apps, AppPreferences(), AppSort.ALPHABETICAL).map { it.label }
        assertEquals("Ábaco", r.first()) // not pushed after "Z" by its accent
        assertEquals("Zoom", r.last())
    }

    @Test
    fun favoritesPriorityHiddenAndManualOrder() {
        val prefs = AppPreferences(
            favorites = listOf("p7/.A"), // Zoom
            priority = listOf("p8/.A"), // Telefone
            hidden = listOf("p3/.A"), // Gmail
        )
        val r = AppOrdering.order(apps, prefs, AppSort.ALPHABETICAL).map { it.label }
        assertEquals(listOf("Zoom", "Telefone"), r.take(2))
        assertTrue("Gmail" !in r)
        assertTrue("Gmail" in AppOrdering.order(apps, prefs, AppSort.ALPHABETICAL, includeHidden = true).map { it.label })

        val keys = apps.map { it.key }
        val moved = AppOrdering.moveInManualOrder(AppPreferences(), keys, "p8/.A", -1)
        val manual = AppOrdering.order(apps, moved, AppSort.MANUAL).map { it.key }
        assertEquals(listOf("p0/.A", "p1/.A", "p2/.A", "p3/.A", "p4/.A", "p5/.A", "p6/.A", "p8/.A", "p7/.A"), manual)
    }

    @Test
    fun mostUsedSort() {
        val r = AppOrdering.order(apps, AppPreferences(), AppSort.MOST_USED, usage = mapOf("p6/.A" to 9, "p2/.A" to 3))
        assertEquals(listOf("WhatsApp", "Definições"), r.take(2).map { it.label })
    }
}
