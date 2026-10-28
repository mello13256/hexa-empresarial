package pt.hexa.empresarial.domain

import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private fun assertTrue(condition: Boolean, message: String = "") = Assert.assertTrue(message, condition)

/**
 * Linguistic checks of the interface text: European Portuguese only, no Brazilian vocabulary, no
 * English placeholders, and no text hardcoded in the Kotlin sources.
 */
class LanguageTest {

    private val root = File(System.getProperty("hexa.projectDir") ?: "..")
    private val stringsFile = File(root, "app/src/main/res/values/strings.xml")

    private data class Entry(val name: String, val text: String, val translatable: Boolean)

    private val entries: List<Entry> by lazy {
        assertTrue(stringsFile.isFile, "strings.xml não encontrado em ${stringsFile.absolutePath}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile)
        val out = mutableListOf<Entry>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            out += Entry(e.getAttribute("name"), e.textContent, e.getAttribute("translatable") != "false")
        }
        val items = doc.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            val parent = e.parentNode as Element
            out += Entry(parent.getAttribute("name") + ":" + e.getAttribute("quantity"), e.textContent, true)
        }
        out
    }

    /** Brazilian Portuguese words and spellings, with the European form expected instead. */
    private val brazilian = mapOf(
        "tela" to "ecrã",
        "telas" to "ecrãs",
        "aplicativo" to "aplicação",
        "aplicativos" to "aplicações",
        "app" to "aplicação",
        "apps" to "aplicações",
        "celular" to "telemóvel",
        "usuário" to "utilizador",
        "usuários" to "utilizadores",
        "salvar" to "guardar",
        "salvo" to "guardado",
        "deletar" to "eliminar",
        "excluir" to "eliminar",
        "arquivo" to "ficheiro",
        "arquivos" to "ficheiros",
        "senha" to "palavra-passe",
        "customizar" to "personalizar",
        "baixar" to "transferir",
        "gerenciar" to "gerir",
        "habilitar" to "ativar",
        "desabilitar" to "desativar",
        "registro" to "registo",
        "contato" to "contacto",
        "seção" to "secção",
        "você" to "forma impessoal",
        "ônibus" to "autocarro",
        "equipe" to "equipa",
        "planilha" to "folha de cálculo",
        "time" to "equipa",
        "mouse" to "rato",
        "tchau" to "adeus",
        "perspectiva" to "perspetiva",
        "atualizando" to "a atualizar",
        "carregando" to "a carregar",
        "editando" to "a editar",
        "pesquisando" to "a pesquisar",
    )

    /** English words that would indicate an untranslated placeholder. */
    private val english = listOf(
        "todo", "fixme", "lorem", "ipsum", "placeholder", "settings", "cancel", "delete", "search", "loading",
        "the", "and", "add", "open", "close", "save", "home", "drawer", "error", "apps", "page",
    )

    private fun words(text: String) = Regex("[\\p{L}]+(?:-[\\p{L}]+)*").findAll(text.lowercase()).map { it.value }.toList()

    @Test
    fun noBrazilianPortuguese() {
        val problems = entries.filter { it.translatable }.flatMap { e ->
            words(e.text).flatMap { w -> w.split('-') + w }.distinct()
                .filter { it in brazilian }
                .map { "${e.name}: «$it» → use «${brazilian[it]}»" }
        }
        if (problems.isNotEmpty()) fail("Termos do português do Brasil:\n" + problems.joinToString("\n"))
    }

    @Test
    fun noEnglishPlaceholders() {
        // Loanwords used in European Portuguese interfaces.
        val allowed = setOf("widget", "widgets", "dock", "ok", "cache")
        val problems = entries.filter { it.translatable }.flatMap { e ->
            words(e.text).filter { it in english && it !in allowed }.map { "${e.name}: «$it»" }
        }
        if (problems.isNotEmpty()) fail("Texto em inglês ou provisório:\n" + problems.joinToString("\n"))
    }

    @Test
    fun onlyBrandNamesAreUntranslatable() {
        val untranslatable = entries.filter { !it.translatable }
        untranslatable.forEach { e ->
            assertTrue(e.name.startsWith("brand_") || e.name == "app_name", "${e.name} não deve ser marcada como não traduzível")
        }
    }

    @Test
    fun everyStringHasText() {
        entries.forEach { e -> assertTrue(e.text.isNotBlank(), "${e.name} está vazia") }
    }

    @Test
    fun pluralsHaveOneAndOther() {
        val plurals = entries.filter { ':' in it.name }.groupBy { it.name.substringBefore(':') }
        assertTrue(plurals.isNotEmpty())
        plurals.forEach { (name, items) ->
            val q = items.map { it.name.substringAfter(':') }.toSet()
            assertTrue("one" in q && "other" in q, "O plural $name precisa de «one» e «other»")
        }
    }

    @Test
    fun noHardcodedTextInKotlinSources() {
        val sources = File(root, "app/src/main/kotlin")
        assertTrue(sources.isDirectory)
        val pattern = Regex("""\b(Text|contentDescription\s*=|placeholder\s*=|label\s*=|showSnackbar|makeText)\s*\(?\s*"((?:[^"\\]|\\.)*)"""")
        val problems = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().flatMap { (i, line) ->
                pattern.findAll(line).mapNotNull { m ->
                    val literal = m.groupValues[2].replace(Regex("""\$\{[^}]*}"""), "").replace(Regex("""\$[A-Za-z_][A-Za-z0-9_.]*"""), "")
                    if (Regex("\\p{L}").containsMatchIn(literal)) "${f.name}:${i + 1}: \"${m.groupValues[2]}\"" else null
                }.toList()
            }
        }.toList()
        if (problems.isNotEmpty()) fail("Texto da interface fora do strings.xml:\n" + problems.joinToString("\n"))
    }

    @Test
    fun noOtherLocaleFolders() {
        val res = File(root, "app/src/main/res")
        val others = res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values-") && it.name != "values-night" && Regex("values-[a-z]{2}(-r[A-Z]{2})?").matches(it.name) }
        assertTrue(others.isEmpty(), "Não deve haver traduções para outros idiomas: $others")
    }
}
