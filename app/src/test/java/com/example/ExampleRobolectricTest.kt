package com.zahidcodes.copynets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zahidcodes.copynets.data.ClipDatabase
import com.zahidcodes.copynets.ui.ClipboardViewModel
import com.zahidcodes.copynets.ui.SplashScreen
import com.zahidcodes.copynets.ui.theme.CopyNestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CopyNest", appName)
    }

    @Test
    fun `splash screen renders logo and CopyNest wordmark and completes timeline`() {
        var completed = false
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            CopyNestTheme(themeMode = "DARK") {
                SplashScreen(
                    onTimeout = { completed = true }
                )
            }
        }

        // Phase 1-2: Logo is present immediately on startup
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_logo_image").assertIsDisplayed()

        // Phase 4: CopyNest typography and underline reveal
        composeTestRule.mainClock.advanceTimeBy(1700L)
        composeTestRule.onNodeWithTag("splash_wordmark_text").assertTextEquals("CopyNest")
        composeTestRule.onNodeWithTag("splash_golden_underline").assertIsDisplayed()

        // Phase 5 completion -> transitions to home via onTimeout after 2700ms
        composeTestRule.mainClock.advanceTimeBy(800L)
        assertTrue("Expected splash screen animation to complete and invoke onTimeout", completed)
    }

    @Test
    fun `copying content saves only once and Clear All deletes every record without resurrecting clipboard`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        runBlocking { db.clipDao().deleteAllClips() }

        val viewModel = ClipboardViewModel(context)
        shadowOf(Looper.getMainLooper()).idle()

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Simulate copying text and firing 3 concurrent clipboard checks (ON_RESUME, focus, listener)
        val testClipText = "Unique Copied Text 12345"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("test", testClipText))

        viewModel.checkClipboardAndSave(context)
        viewModel.checkClipboardAndSave(context)
        viewModel.checkClipboardAndSave(context)

        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val clipsAfterCopy = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected copied content to be saved exactly once, not 3 times", 1, clipsAfterCopy.size)
        assertEquals(testClipText, clipsAfterCopy.first().text)

        // Add two more manual records so there are 3 records total
        viewModel.saveManualClip(title = "Second", text = "Second note record")
        viewModel.saveManualClip(title = "Third", text = "Third note record")
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals(3, runBlocking { db.clipDao().getAllClipsList().size })

        // Now trigger Clear All Records and simulate window focus returning right after dialog closes
        viewModel.deleteAllClips()
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        viewModel.checkClipboardAndSave(context)
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }

        val clipsAfterClearAll = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected Clear All Records to delete all records and leave 0 records", 0, clipsAfterClearAll.size)
    }
}
