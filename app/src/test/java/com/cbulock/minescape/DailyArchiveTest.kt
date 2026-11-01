package com.cbulock.minescape

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cbulock.minescape.data.*
import com.cbulock.minescape.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DailyArchiveTest {
    @get:Rule val compose = createComposeRule()
    @Test @Config(qualifiers = "w360dp-h800dp-xhdpi") fun calendarSelectsPastDaysAndDisablesFuture() {
        var selected = ""
        compose.setContent { MinescapeTheme(ThemeMode.LIGHT) {
            DailyArchive("2026-11-15", emptyMap(), { selected = it }, {})
        } }
        compose.onNodeWithTag("archive-2026-11-16").assertIsNotEnabled()
        compose.onNodeWithTag("archive-2026-11-14").performClick()
        compose.onNodeWithText("Play as practice").performScrollTo().performClick()
        assertEquals("2026-11-14", selected)
        compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick()
        compose.onNodeWithTag("archive-2026-10-31").performClick()
        compose.onNodeWithText("Play as practice").performScrollTo().performClick()
        assertEquals("2026-10-31", selected)
    }
}
