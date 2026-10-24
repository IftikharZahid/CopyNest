package com.zahidcodes.copynest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zahidcodes.copynest.data.ClipDatabase
import com.zahidcodes.copynest.data.LinkMetadataEntity
import com.zahidcodes.copynest.data.LinkMetadataManager
import com.zahidcodes.copynest.ui.ClipboardViewModel
import com.zahidcodes.copynest.ui.SplashScreen
import com.zahidcodes.copynest.ui.theme.CopyNestTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun `link metadata persists in Room database and state store without re-fetching and scrollToTop triggers on resume`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        val testUrl = "https://github.com/zahidcodes/copynest"

        runBlocking {
            db.clipDao().deleteAllClips()
            db.clipDao().insertCachedLinkMetadata(
                LinkMetadataEntity(
                    normalizedUrl = testUrl,
                    domain = "github.com",
                    siteName = "GitHub",
                    title = "CopyNest Smart Clipboard Repository",
                    description = "Saved link metadata in SQLite Room database",
                    faviconBase64 = "",
                    isLoaded = true
                )
            )
        }

        val viewModel = ClipboardViewModel(context)
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }

        val cached = LinkMetadataManager.getLoadedMetadata(testUrl)
        assertNotNull("Expected link metadata to be hydrated into memory state from Room database", cached)
        assertTrue(cached!!.isLoaded)
        assertFalse(cached.isLoading)
        assertEquals("CopyNest Smart Clipboard Repository", cached.title)

        // Saving a clip with this URL should immediately attach cached metadata in Room DB without re-fetching
        val initialScrollVersion = viewModel.scrollToTopVersion.value
        viewModel.saveManualClip(title = "", text = "Check out $testUrl")
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }

        val savedClip = runBlocking { db.clipDao().getMostRecentClip() }
        assertNotNull(savedClip)
        assertTrue("Expected clip row in DB to have linkMetadataLoaded = true immediately", savedClip!!.linkMetadataLoaded)
        assertEquals("CopyNest Smart Clipboard Repository", savedClip.linkTitle)
        assertTrue("Expected scrollToTopVersion to increment when clip is saved", viewModel.scrollToTopVersion.value > initialScrollVersion)

        val beforeResumeScrollVersion = viewModel.scrollToTopVersion.value
        viewModel.onAppForegrounded(context)
        assertTrue("Expected scrollToTopVersion to increment when app is reopened/foregrounded", viewModel.scrollToTopVersion.value > beforeResumeScrollVersion)
    }

    @Test
    fun `deleted clipboard entry is not re-imported after app restart or foregrounding, but legitimate new copy is saved`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        runBlocking {
            db.clipDao().deleteAllClips()
            db.clipDao().deleteAllDeletedClips()
        }

        // 1. First session: app opens and imports the clipboard content
        val viewModel1 = ClipboardViewModel(context)
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40)
        }

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val deletedText = "Secret API token to delete 998877"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("test", deletedText))

        viewModel1.checkClipboardAndSave(context)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        shadowOf(Looper.getMainLooper()).idle()

        val initialClips = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected initial clip to be imported", 1, initialClips.size)
        val savedClip = initialClips.first()
        assertEquals(deletedText, savedClip.text)

        // 2. User deletes the clip from the app while the text remains on the system clipboard
        viewModel1.deleteClip(savedClip.id)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("Expected clip to be deleted from DB", 0, runBlocking { db.clipDao().getAllClipsList().size })
        val tombstone = runBlocking {
            db.clipDao().getDeletedClip(ClipboardViewModel.computeContentFingerprint(deletedText))
        }
        assertNotNull("Expected persistent deletion tombstone in Room DB", tombstone)

        // 3. Simulate lifecycle callbacks (resume / home screen shown / repeated checks)
        viewModel1.onAppForegrounded(context)
        viewModel1.onHomeScreenShown(context)
        viewModel1.checkClipboardAndSave(context)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals(
            "Deleted clip must not be re-imported on foreground/home callbacks",
            0,
            runBlocking { db.clipDao().getAllClipsList().size }
        )

        // 4. Simulate full app close and reopen (new ViewModel instance with fresh in-memory state)
        val viewModel2 = ClipboardViewModel(context)
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40)
        }
        viewModel2.onHomeScreenShown(context)
        viewModel2.onAppForegrounded(context)
        viewModel2.checkClipboardAndSave(context)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals(
            "Deleted clip must not be re-imported after app restart",
            0,
            runBlocking { db.clipDao().getAllClipsList().size }
        )

        // 5. Copying genuinely new content works normally
        val newContent = "Brand new copied note 554433"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("test", newContent))
        viewModel2.checkClipboardAndSave(context)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        val clipsAfterNewCopy = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected new content to be saved", 1, clipsAfterNewCopy.size)
        assertEquals(newContent, clipsAfterNewCopy.first().text)

        // 6. Intentionally copying the previously deleted text again as a fresh copy event saves it
        Thread.sleep(20)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("test", deletedText))
        viewModel2.checkClipboardAndSave(context, isFromListener = true)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        val clipsAfterReCopy = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected intentionally re-copied text to be saved again", 2, clipsAfterReCopy.size)
        assertTrue(clipsAfterReCopy.any { it.text == deletedText })
    }

    @Test
    fun `splash screen enforces mandatory force update and blocks transition until update completes`() {
        var navigatedToMain = false
        var retried = false
        composeTestRule.mainClock.autoAdvance = false

        val uiStateHolder = androidx.compose.runtime.mutableStateOf<com.zahidcodes.copynest.update.SplashUpdateUiState>(
            com.zahidcodes.copynest.update.SplashUpdateUiState.Checking()
        )

        composeTestRule.setContent {
            CopyNestTheme(themeMode = "DARK") {
                SplashScreen(
                    onTimeout = { navigatedToMain = true },
                    updateUiState = uiStateHolder.value,
                    onRetryUpdate = { retried = true }
                )
            }
        }

        // During Checking state, "Checking for updates..." status indicator must not be shown
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_update_status").assertDoesNotExist()

        // Transition to blocked mandatory update state
        uiStateHolder.value = com.zahidcodes.copynest.update.SplashUpdateUiState.UpdateRequiredBlocked(
            reason = com.zahidcodes.copynest.update.UpdateBlockReason.USER_CANCELLED,
            title = "Mandatory Update Required",
            message = "A mandatory update is required to continue using CopyNest.",
            availableVersionCode = 99,
            canRetryInApp = true,
            showOpenPlayStore = true
        )

        // Advance past the full splash animation timeline across frames
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.mainClock.advanceTimeBy(1700L)
        composeTestRule.mainClock.advanceTimeBy(800L)

        // Splash screen must remain visible and NOT call onTimeout while mandatory update is pending
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_force_update_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_retry_update_button").assertIsDisplayed()
        assertFalse("Main app navigation must be blocked when mandatory update is required", navigatedToMain)

        // Once update check allows proceeding, splash screen immediately triggers onTimeout to redirect to HomeScreen
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.runOnIdle {
            uiStateHolder.value = com.zahidcodes.copynest.update.SplashUpdateUiState.AllowedToProceed
        }
        composeTestRule.waitForIdle()
        assertTrue("Splash screen must redirect to HomeScreen once AllowedToProceed is reached", navigatedToMain)
    }

    @Test
    fun `home screen renders CopyNest brand header, Your clips heading, filter pills, no preadded template notes, and adds notes smoothly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        val prefs = com.zahidcodes.copynest.data.UserPreferences(context)
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("empty", ""))
        runBlocking {
            db.clipDao().deleteAllClips()
            db.clipDao().deleteAllDeletedClips()
            prefs.setHasSeededDefaultClips(false)
        }

        val viewModel = ClipboardViewModel(context, seedDefaultClipsIfFirstLaunch = false)
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(60)
        }

        composeTestRule.setContent {
            CopyNestTheme(themeMode = "LIGHT") {
                com.zahidcodes.copynest.ui.HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDetail = {},
                    onNavigateToAdd = {},
                    onNavigateToSettings = {}
                )
            }
        }

        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.mainClock.advanceTimeBy(100L)
            Thread.sleep(60)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_app_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("your_clips_heading").assertTextEquals("Your clips")
        composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_all").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_pinned").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_url").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_recent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_clip_fab").assertIsDisplayed()

        val initialClips = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected 0 pre-added template notes on launch", 0, initialClips.size)

        // Adding a new note works smoothly and immediately persists
        viewModel.saveManualClip(
            title = "Smooth Custom Note",
            text = "Custom note added smoothly by user",
            category = "Work"
        )
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        val clipsAfterAdd = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected newly added note to be saved smoothly", 1, clipsAfterAdd.size)
        assertEquals("Smooth Custom Note", clipsAfterAdd.first().title)
        assertEquals("Custom note added smoothly by user", clipsAfterAdd.first().text)
    }

    @Test
    fun `add note screen and update note screen render in compact HomeScreen format`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        runBlocking {
            db.clipDao().deleteAllClips()
            db.clipDao().deleteAllDeletedClips()
        }

        val viewModel = ClipboardViewModel(context)
        viewModel.saveManualClip(
            title = "Study Notes",
            text = "• Review chapter 3\n• Practice problems",
            category = "Study"
        )
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        val savedClip = runBlocking { db.clipDao().getAllClipsList().first() }

        val showDetail = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            CopyNestTheme(themeMode = "LIGHT") {
                if (!showDetail.value) {
                    com.zahidcodes.copynest.ui.AddClipScreen(
                        viewModel = viewModel,
                        onBack = {}
                    )
                } else {
                    com.zahidcodes.copynest.ui.DetailScreen(
                        clipId = savedClip.id,
                        viewModel = viewModel,
                        onBack = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_dropdown_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_clip_content_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_add_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_new_clip_button").assertIsDisplayed()

        // Category items are only inside the dropdown list until opened
        composeTestRule.onNodeWithTag("category_chip_study").assertDoesNotExist()
        composeTestRule.onNodeWithTag("category_dropdown_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("category_chip_study").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_chip_study").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            showDetail.value = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_clip_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_category_dropdown_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_clip_text_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_edit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_edit_button").assertIsDisplayed()

        // Category items on Edit Note screen are only inside the dropdown list until opened
        composeTestRule.onNodeWithTag("edit_category_chip_study").assertDoesNotExist()
        composeTestRule.onNodeWithTag("edit_category_dropdown_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_category_chip_study").assertIsDisplayed()
    }

    @Test
    fun `two-way Google Drive sync creates or reuses Copy Nest folder, syncs existing and new clips both ways without duplicates, and handles deletions and network issues`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ClipDatabase.getDatabase(context)
        val prefs = com.zahidcodes.copynest.data.UserPreferences(context)
        runBlocking {
            db.clipDao().deleteAllClips()
            db.clipDao().deleteAllDeletedClips()
            prefs.setDriveSyncConnection(enabled = false, accountEmail = "", folderId = "")
        }

        // Fake in-memory Google Drive backend to verify folder creation, uploads, downloads, deduplication, and deletions
        var isOnline = true
        val remoteFolders = mutableMapOf<String, String>() // id -> name
        val remoteFiles = mutableMapOf<String, Pair<com.zahidcodes.copynest.data.DriveRemoteFile, String>>() // id -> (metadata, content)
        var nextId = 100

        // Seed an existing text file in Google Drive inside the "Copy Nest" folder before connecting
        val existingFolderId = "folder_copynest_1"
        remoteFolders[existingFolderId] = com.zahidcodes.copynest.data.DriveSyncManager.COPY_NEST_FOLDER_NAME
        val preExistingDriveText = "Remote study note from Google Drive https://developer.android.com"
        val preExistingRemoteFile = com.zahidcodes.copynest.data.DriveRemoteFile(
            id = "drive_file_1",
            name = "Remote_Note.txt",
            mimeType = "text/plain",
            modifiedTime = System.currentTimeMillis() - 10_000L,
            appProperties = emptyMap()
        )
        remoteFiles[preExistingRemoteFile.id] = preExistingRemoteFile to preExistingDriveText

        val fakeDriveApiClient = object : com.zahidcodes.copynest.data.DriveApiClient {
            override suspend fun isFolderValid(accessToken: String, folderId: String): Boolean {
                return remoteFolders.containsKey(folderId)
            }

            override suspend fun findFolderByName(accessToken: String, folderName: String): String? {
                return remoteFolders.entries.firstOrNull { it.value == folderName }?.key
            }

            override suspend fun createFolder(accessToken: String, folderName: String): String {
                val id = "folder_${++nextId}"
                remoteFolders[id] = folderName
                return id
            }

            override suspend fun listFilesInFolder(
                accessToken: String,
                folderId: String
            ): List<com.zahidcodes.copynest.data.DriveRemoteFile> {
                return remoteFiles.values.map { it.first }
            }

            override suspend fun downloadFileText(
                accessToken: String,
                file: com.zahidcodes.copynest.data.DriveRemoteFile
            ): String {
                return remoteFiles[file.id]?.second ?: ""
            }

            override suspend fun uploadTextFile(
                accessToken: String,
                folderId: String,
                fileName: String,
                content: String,
                appProperties: Map<String, String>
            ): com.zahidcodes.copynest.data.DriveRemoteFile {
                val id = "drive_file_${++nextId}"
                val file = com.zahidcodes.copynest.data.DriveRemoteFile(
                    id = id,
                    name = fileName,
                    mimeType = "text/plain",
                    modifiedTime = System.currentTimeMillis(),
                    appProperties = appProperties
                )
                remoteFiles[id] = file to content
                return file
            }

            override suspend fun updateTextFile(
                accessToken: String,
                fileId: String,
                fileName: String,
                content: String,
                appProperties: Map<String, String>
            ): com.zahidcodes.copynest.data.DriveRemoteFile {
                val file = com.zahidcodes.copynest.data.DriveRemoteFile(
                    id = fileId,
                    name = fileName,
                    mimeType = "text/plain",
                    modifiedTime = System.currentTimeMillis(),
                    appProperties = appProperties
                )
                remoteFiles[fileId] = file to content
                return file
            }

            override suspend fun deleteFile(accessToken: String, fileId: String) {
                remoteFiles.remove(fileId)
            }
        }

        val repository = com.zahidcodes.copynest.data.ClipRepository(db.clipDao())
        val driveSyncManager = com.zahidcodes.copynest.data.DriveSyncManager(
            context = context,
            repository = repository,
            userPreferences = prefs,
            driveApiClient = fakeDriveApiClient,
            networkChecker = { isOnline },
            tokenProvider = { "valid_oauth_access_token" }
        )

        val viewModel = ClipboardViewModel(
            context = context,
            seedDefaultClipsIfFirstLaunch = false,
            customDriveSyncManager = driveSyncManager
        )

        // 1. Before connecting Google Drive, local clipboard items are saved locally ONLY and NOT uploaded
        viewModel.saveManualClip(title = "Local Note", text = "Local clipboard note before Drive connection")
        for (i in 0 until 5) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40)
        }
        assertEquals("Before connecting Drive, remote files should remain 1", 1, remoteFiles.size)

        // 2. Verify Settings Screen renders "Sync with Google Drive" card and asks user to connect before uploading
        composeTestRule.setContent {
            CopyNestTheme(themeMode = "LIGHT") {
                com.zahidcodes.copynest.ui.SettingsScreen(
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_with_google_drive_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_with_google_drive_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_connect_prompt_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_with_google_drive_button").assertIsDisplayed()

        // Tap "Sync with Google Drive" button -> shows confirmation dialog asking to connect Google Drive
        composeTestRule.onNodeWithTag("sync_with_google_drive_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connect_drive_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_connect_drive_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Connect Google Drive and run initial two-way sync:
        //    - Reuses "Copy Nest" folder
        //    - Downloads pre-existing Drive text file into local DB
        //    - Uploads missing local record to Drive
        viewModel.onGoogleDriveConnected(
            accountEmail = "xahidcodes@gmail.com",
            accessToken = "valid_oauth_access_token"
        )
        for (i in 0 until 8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        composeTestRule.waitForIdle()

        assertEquals(existingFolderId, runBlocking { prefs.getDriveFolderId() })
        val clipsAfterInitialSync = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected 2 clips in local DB (1 local + 1 downloaded from Drive)", 2, clipsAfterInitialSync.size)
        assertTrue(clipsAfterInitialSync.any { it.text == preExistingDriveText })
        assertTrue(clipsAfterInitialSync.any { it.text == "Local clipboard note before Drive connection" })
        assertEquals("Expected 2 files in Drive folder after initial two-way sync", 2, remoteFiles.size)

        // 4. Ongoing two-way sync: copying new text to clipboard automatically uploads to Drive without duplicates
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val newCopiedText = "Automatically synced clipboard text 778899"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("clip", newCopiedText))
        viewModel.checkClipboardAndSave(context)
        for (i in 0 until 8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("Expected new clipboard entry to be uploaded to Drive", 3, remoteFiles.size)
        assertTrue(remoteFiles.values.any { it.second == newCopiedText })

        // 5. Adding a new text file directly in Google Drive "Copy Nest" folder appears in the app without duplicates
        val secondRemoteFile = com.zahidcodes.copynest.data.DriveRemoteFile(
            id = "drive_file_external_99",
            name = "External_Drive_File.txt",
            mimeType = "text/plain",
            modifiedTime = System.currentTimeMillis(),
            appProperties = emptyMap()
        )
        remoteFiles[secondRemoteFile.id] = secondRemoteFile to "Added directly inside Google Drive folder"
        viewModel.syncWithGoogleDrive(isManual = true)
        for (i in 0 until 8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        val clipsAfterRemoteAdd = runBlocking { db.clipDao().getAllClipsList() }
        assertEquals("Expected 4 clips after syncing new Drive file", 4, clipsAfterRemoteAdd.size)
        assertTrue(clipsAfterRemoteAdd.any { it.text == "Added directly inside Google Drive folder" })

        // 6. Deleting a clip locally deletes it from Google Drive and does not resurrect it
        val clipToDelete = clipsAfterRemoteAdd.first { it.text == newCopiedText }
        viewModel.deleteClip(clipToDelete.id)
        for (i in 0 until 8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("Expected 3 local clips after deletion", 3, runBlocking { db.clipDao().getAllClipsList().size })
        assertFalse("Expected deleted clip to be removed from Drive", remoteFiles.values.any { it.second == newCopiedText })

        // 7. Network outage handling: copying while offline saves locally and syncs once back online
        isOnline = false
        viewModel.saveManualClip(title = "Offline Clip", text = "Saved while offline 445566")
        for (i in 0 until 6) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("Offline clip must be saved in local DB", 4, runBlocking { db.clipDao().getAllClipsList().size })
        assertEquals("Offline clip not yet in remote Drive while offline", 3, remoteFiles.size)

        isOnline = true
        viewModel.syncWithGoogleDrive(isManual = true)
        for (i in 0 until 8) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        assertEquals("Once back online, offline clip is uploaded to Drive", 4, remoteFiles.size)
        assertTrue(remoteFiles.values.any { it.second == "Saved while offline 445566" })
    }
}


