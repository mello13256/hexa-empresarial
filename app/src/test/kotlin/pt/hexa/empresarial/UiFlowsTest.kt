package pt.hexa.empresarial

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.hexa.empresarial.domain.model.DrawerView
import pt.hexa.empresarial.domain.model.SearchBarItem
import pt.hexa.empresarial.home.HomeActivity
import pt.hexa.empresarial.settings.SettingsActivity

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SettingsFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun listsTheTwelveCategoriesInPortuguese() {
        listOf(
            "Ecrã inicial", "Ícones", "Dock", "Gaveta de aplicações", "Interface orbital", "Grupos",
            "Widgets", "Gestos", "Aparência", "Animações", "Cópias de segurança", "Definições avançadas",
        ).forEach { rule.onNode(hasText(it)).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun backupsScreenExplainsLimitations() {
        rule.onNode(hasText("Cópias de segurança")).performScrollTo().performClick()
        rule.onNodeWithText("Exportar configuração").assertIsDisplayed()
        rule.onNodeWithText("Importar configuração").performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("widgets não podem ser transferidos", substring = true)).performScrollTo().assertIsDisplayed()
        rule.onNode(hasText("imagens próprias", substring = true)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun changingASettingUpdatesTheState() {
        rule.onNode(hasText("Gaveta de aplicações")).performScrollTo().performClick()
        rule.onNodeWithText("Grelha").performClick()
        rule.waitForIdle()
        assertEquals(DrawerView.GRID, rule.activity.container.state.value.drawer.view)
        rule.onNodeWithText("Orbital").performClick()
        rule.waitForIdle()
        assertEquals(DrawerView.ORBITAL, rule.activity.container.state.value.drawer.view)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<HomeActivity>()

    private fun waitForHome() {
        rule.waitUntil(15_000) { rule.activity.container.state.value.initialized }
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Pesquisar aplicações")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun firstRunCreatesTheDefaultHome() {
        waitForHome()
        val items = rule.activity.container.state.value.home.pages.flatMap { it.items }
        assertTrue(items.any { it is SearchBarItem })
        assertTrue(rule.activity.container.state.value.home.pages.size >= 1)
    }

    @Test
    fun squareButtonSwitchesTheDrawerToTheGridAndIsRemembered() {
        waitForHome()
        rule.onAllNodes(hasText("Pesquisar aplicações")).onFirst().performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Grelha")).performClick()
        rule.waitForIdle()
        assertEquals(DrawerView.GRID, rule.activity.container.state.value.drawer.view)
        rule.onNode(hasContentDescription("Orbital")).performClick()
        rule.waitForIdle()
        assertEquals(DrawerView.ORBITAL, rule.activity.container.state.value.drawer.view)
        rule.onNodeWithContentDescription("Fechar a gaveta").performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Grelha")).assertCountEquals(0)
    }

    private fun hasSearchBar() = rule.activity.container.state.value.home.pages.any { p -> p.items.any { it is SearchBarItem } }

    @Test
    fun editModeRemovesWithUndoAndEndsCleanly() {
        waitForHome()
        // Long press on the search bar, then "Editar ecrã inicial" from its menu.
        rule.onAllNodes(hasText("Pesquisar aplicações")).onFirst().performTouchInput { longClick() }
        rule.onNodeWithText("Editar ecrã inicial").performClick()
        rule.waitForIdle()
        // Edit tools with labels at the bottom, page controls at the top.
        listOf("Adicionar widget", "Novo grupo", "Adicionar página", "Concluir").forEach {
            rule.onNode(hasContentDescription(it)).assertIsDisplayed()
        }
        rule.onNodeWithText("Página 1 de 2").assertIsDisplayed()

        // The badge removes the element; "Anular" brings it back.
        rule.onNode(hasContentDescription("Remover Barra de pesquisa")).performClick()
        rule.waitForIdle()
        assertFalse(hasSearchBar())
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Anular")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Anular").performClick()
        rule.waitForIdle()
        assertTrue(hasSearchBar())

        rule.onNode(hasContentDescription("Concluir")).performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasContentDescription("Adicionar widget")).assertCountEquals(0)
        rule.onAllNodes(hasContentDescription("Remover Barra de pesquisa")).assertCountEquals(0)
    }
}
