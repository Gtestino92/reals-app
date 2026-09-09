package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reals.app.domain.model.CountryReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountrySelectorDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closedSelectorShowsCurrentLabel() {
        setSelector(countries = smallCountries(), selectedCode = "AR")

        composeRule.onNodeWithTag(CountrySelectorButtonTag)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertIsEnabled()
        composeRule.onNodeWithText("Argentina").assertIsDisplayed()
    }

    @Test
    fun closedSelectorFallsBackToUnknownCode() {
        setSelector(countries = smallCountries(), selectedCode = "ZZ")

        composeRule.onNodeWithText("ZZ").assertIsDisplayed()
    }

    @Test
    fun closedSelectorIsDisabledWhileLoading() {
        setSelector(countries = smallCountries(), selectedCode = "AR", loading = true)

        composeRule.onNodeWithText("Cargando países...").assertIsDisplayed()
        composeRule.onNodeWithTag(CountrySelectorButtonTag).assertIsNotEnabled()
    }

    @Test
    fun closedSelectorIsDisabledForEmptyCatalog() {
        setSelector(countries = emptyList(), selectedCode = "")

        composeRule.onNodeWithTag(CountrySelectorButtonTag).assertIsNotEnabled()
    }

    @Test
    fun openCancelAndReopenStartsWithBlankSearch() {
        setSelector(countries = smallCountries(), selectedCode = "AR")

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()
        composeRule.onNodeWithTag(CountrySelectorDialogTag).assertIsDisplayed()
        composeRule.onNodeWithText("Seleccionar país").assertIsDisplayed()
        composeRule.onNodeWithTag(CountrySelectorSearchTag).assertIsDisplayed()
        composeRule.onNodeWithTag(CountrySelectorListTag).assertIsDisplayed()

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("mexico")
        composeRule.onNodeWithText("México").assertIsDisplayed()
        composeRule.onNodeWithTag(CountrySelectorCancelTag).performClick()
        composeRule.onAllNodesWithTag(CountrySelectorDialogTag).assertCountEquals(0)

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()
        composeRule.onNodeWithTag(countrySelectorRowTag("AR")).assertIsDisplayed()
        composeRule.onNodeWithTag(countrySelectorRowTag("MX")).assertIsDisplayed()
    }

    @Test
    fun largeCatalogIsLazyButSearchCanRevealLateCountries() {
        setSelector(countries = largeCountries(), selectedCode = "AR")

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()

        composeRule.onNodeWithTag(countrySelectorRowTag("AR")).assertIsDisplayed()
        composeRule.onAllNodesWithTag(countrySelectorRowTag("C249")).assertCountEquals(0)

        composeRule.onNodeWithTag(CountrySelectorListTag)
            .performScrollToNode(hasTestTag(countrySelectorRowTag("C249")))
        composeRule.onNodeWithTag(countrySelectorRowTag("C249")).assertIsDisplayed()

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("pais 249")
        composeRule.onNodeWithTag(countrySelectorRowTag("C249")).assertIsDisplayed()
    }

    @Test
    fun searchMatchesAccentsCodesAndEmptyState() {
        setSelector(countries = smallCountries(), selectedCode = "AR")

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("mexico")
        composeRule.onNodeWithText("México").assertIsDisplayed()
        composeRule.onAllNodesWithTag(countrySelectorRowTag("ES")).assertCountEquals(0)

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextClearance()
        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("espana")
        composeRule.onNodeWithText("España").assertIsDisplayed()

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextClearance()
        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("UY")
        composeRule.onNodeWithText("Uruguay").assertIsDisplayed()

        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextClearance()
        composeRule.onNodeWithTag(countrySelectorRowTag("AR")).assertIsDisplayed()
    }

    @Test
    fun unmatchedSearchDisplaysEmptyState() {
        setSelector(countries = smallCountries(), selectedCode = "AR")

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()
        composeRule.onNodeWithTag(CountrySelectorSearchTag).performTextInput("sin-resultados")

        composeRule.onNodeWithTag(CountrySelectorEmptyTag).assertIsDisplayed()
    }

    @Test
    fun selectionInvokesCallbackOnceAndMarksSelectedRow() {
        var selectedCode by mutableStateOf("AR")
        val events = mutableListOf<String>()
        setSelector(
            countries = smallCountries(),
            selectedCodeProvider = { selectedCode },
            onSelected = {
                events += it
                selectedCode = it
            },
        )

        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()
        composeRule.onNodeWithTag(countrySelectorRowTag("AR")).assertIsSelected()
        composeRule.onAllNodesWithTag(countrySelectorSelectedTag("AR"), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNodeWithTag(countrySelectorRowTag("MX")).performClick()

        assertEquals(listOf("MX"), events)
        composeRule.onAllNodesWithTag(CountrySelectorDialogTag).assertCountEquals(0)
        composeRule.onNodeWithText("México").assertIsDisplayed()
    }

    @Test
    fun normalFontDialogKeepsCriticalContentReachable() {
        assertDialogBounds(width = 360.dp, height = 720.dp, fontScale = 1f)
    }

    @Test
    fun largeFontDialogKeepsCriticalContentReachable() {
        assertDialogBounds(width = 320.dp, height = 640.dp, fontScale = 1.5f)
    }

    @Test
    fun veryLargeFontReducedHeightDialogKeepsCriticalContentReachable() {
        assertDialogBounds(width = 320.dp, height = 560.dp, fontScale = 2f)
    }

    private fun assertDialogBounds(width: Dp, height: Dp, fontScale: Float) {
        setSelector(width = width, height = height, fontScale = fontScale, countries = smallCountries(), selectedCode = "AR")
        composeRule.onNodeWithTag(CountrySelectorButtonTag).performClick()

        val dialog = composeRule.onNodeWithTag(CountrySelectorDialogTag).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Seleccionar país").assertIsDisplayed().getUnclippedBoundsInRoot()
        val search = composeRule.onNodeWithTag(CountrySelectorSearchTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        val list = composeRule.onNodeWithTag(CountrySelectorListTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        val cancel = composeRule.onNodeWithTag(CountrySelectorCancelTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithTag(countrySelectorRowTag("AR")).assertIsDisplayed().getUnclippedBoundsInRoot()

        assertWithin(dialog, title)
        assertWithin(dialog, search)
        assertWithin(dialog, list)
        assertWithin(dialog, cancel)
        assertTrue(list.bottom - list.top > 0.dp)
        assertTrue(row.bottom - row.top >= 48.dp)
        assertTrue(search.top >= title.bottom)
        assertTrue(list.top >= search.bottom)
        assertTrue(cancel.top >= list.bottom)
    }

    private fun setSelector(
        width: Dp = 360.dp,
        height: Dp = 720.dp,
        fontScale: Float = 1f,
        countries: List<CountryReference>,
        selectedCode: String = "",
        loading: Boolean = false,
        enabled: Boolean = true,
        onSelected: (String) -> Unit = {},
    ) {
        setSelector(
            width = width,
            height = height,
            fontScale = fontScale,
            countries = countries,
            selectedCodeProvider = { selectedCode },
            loading = loading,
            enabled = enabled,
            onSelected = onSelected,
        )
    }

    private fun setSelector(
        width: Dp = 360.dp,
        height: Dp = 720.dp,
        fontScale: Float = 1f,
        countries: List<CountryReference>,
        selectedCodeProvider: () -> String,
        loading: Boolean = false,
        enabled: Boolean = true,
        onSelected: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(width)
                            .requiredHeight(height),
                    ) {
                        CountrySelector(
                            countries = countries,
                            selectedCountryCode = selectedCodeProvider(),
                            loading = loading,
                            enabled = enabled,
                            onCountrySelected = onSelected,
                        )
                    }
                }
            }
        }
    }

    private fun smallCountries(): List<CountryReference> = listOf(
        CountryReference("UY", "Uruguay"),
        CountryReference("AR", "Argentina"),
        CountryReference("MX", "México"),
        CountryReference("ES", "España"),
    )

    private fun largeCountries(): List<CountryReference> =
        listOf(CountryReference("AR", "Argentina")) +
            (1..249).map { index ->
                CountryReference("C$index", "País $index")
            }

    private fun assertWithin(container: DpRect, child: DpRect) {
        assertTrue(child.left >= container.left)
        assertTrue(child.top >= container.top)
        assertTrue(child.right <= container.right)
        assertTrue(child.bottom <= container.bottom)
    }
}
