package com.zahidcodes.copynest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zahidcodes.copynest.data.ClipDatabase
import com.zahidcodes.copynest.data.ClipEntity
import com.zahidcodes.copynest.data.ClipRepository
import com.zahidcodes.copynest.data.LinkMetadata
import com.zahidcodes.copynest.data.LinkMetadataEntity
import com.zahidcodes.copynest.data.LinkMetadataManager
import com.zahidcodes.copynest.data.UserPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClipboardViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val database = ClipDatabase.getDatabase(appContext)
    private val repository = ClipRepository(database.clipDao())
    private val userPreferences = UserPreferences(appContext)
    private val clipboardMutex = Mutex()
    private val initialDatabaseLoadComplete = CompletableDeferred<Unit>()

    @Volatile
    private var lastInternalCopiedText: String? = null

    @Volatile
    private var lastProcessedClipText: String? = null

    @Volatile
    private var hasCompletedInitialHomeLoad: Boolean = false

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Increments whenever the app opens, resumes, or a clip is added/moved to top so HomeScreen scrolls to index 0
    private val _scrollToTopVersion = MutableStateFlow(0L)
    val scrollToTopVersion: StateFlow<Long> = _scrollToTopVersion.asStateFlow()

    fun requestScrollToTop() {
        _scrollToTopVersion.update { it + 1L }
    }

    val autoCapture = userPreferences.autoCaptureFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        checkClipboardAndSave(appContext, force = false)
    }

    init {
        viewModelScope.launch {
            userPreferences.themeModeFlow.collect { mode ->
                _themeMode.value = mode
            }
        }
        viewModelScope.launch {
            try {
                clipboardMutex.withLock {
                    // Clean up duplicate records and auto-assign category (Text, Links, Code) for existing records
                    val all = repository.getAllClipsList()
                    val seenTexts = HashSet<String>()
                    val activeClips = mutableListOf<ClipEntity>()
                    for (item in all) {
                        val normalized = item.text.trim()
                        if (!seenTexts.add(normalized)) {
                            repository.deleteClipById(item.id)
                        } else {
                            val detectedType = detectContentType(normalized)
                            val autoCategory = autoCategoryForContentType(detectedType)
                            val shouldAutoUpdateCategory = item.category.isBlank() ||
                                item.category.equals("General", ignoreCase = true) ||
                                item.category.equals("Text", ignoreCase = true) ||
                                item.category.equals("Links", ignoreCase = true) ||
                                item.category.equals("Code", ignoreCase = true)

                            val targetCategory = if (shouldAutoUpdateCategory) autoCategory else item.category
                            val detectedLink = LinkMetadataManager.extractFirstLink(normalized)
                            val targetLinkUrl = detectedLink?.normalizedUrl ?: ""
                            val targetLinkDomain = detectedLink?.domain ?: ""

                            val updatedItem = if (
                                item.contentType != detectedType ||
                                item.category != targetCategory ||
                                (targetLinkUrl.isNotBlank() && item.linkUrl != targetLinkUrl)
                            ) {
                                val copyItem = item.copy(
                                    contentType = detectedType,
                                    category = targetCategory,
                                    linkUrl = if (targetLinkUrl.isNotBlank()) targetLinkUrl else item.linkUrl,
                                    linkDomain = if (targetLinkDomain.isNotBlank()) targetLinkDomain else item.linkDomain
                                )
                                repository.updateClip(copyItem)
                                copyItem
                            } else {
                                item
                            }
                            activeClips.add(updatedItem)
                        }
                    }

                    // Hydrate in-memory LinkMetadataManager state store from Room database so cached links never re-fetch
                    LinkMetadataManager.hydrateFromDatabase(repository, activeClips)

                    lastProcessedClipText = repository.getMostRecentClip()?.text?.trim()
                }
                // Wait for the Room flow to emit its initial snapshot
                repository.allClips.first()
            } catch (e: Exception) {
                // Ignore initialization errors
            } finally {
                initialDatabaseLoadComplete.complete(Unit)
            }
        }
        try {
            val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        } catch (e: Exception) {
            // Ignore clipboard listener registration restrictions
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow("ALL") // ALL, PINNED, URL, CODE, TEXT
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    val clips: StateFlow<List<ClipEntity>> = combine(
        repository.allClips,
        _searchQuery,
        _selectedFilter
    ) { allClips, query, filter ->
        var result = allClips

        // Filter by search query (checks title, text, and category)
        if (query.isNotBlank()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.text.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.linkTitle.contains(query, ignoreCase = true) ||
                    it.linkSiteName.contains(query, ignoreCase = true)
            }
        }

        // Filter by chip
        when (filter) {
            "PINNED" -> result = result.filter { it.isPinned }
            "URL" -> result = result.filter {
                it.contentType == "URL" ||
                    it.category.equals("Links", ignoreCase = true) ||
                    it.linkUrl.isNotBlank() ||
                    LinkMetadataManager.extractFirstLink(it.text) != null
            }
            "CODE" -> result = result.filter {
                it.contentType == "CODE" || it.category.equals("Code", ignoreCase = true)
            }
            "TEXT" -> result = result.filter {
                it.contentType == "TEXT" || it.category.equals("Text", ignoreCase = true)
            }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val linkMetadataCache: StateFlow<Map<String, LinkMetadata>> = LinkMetadataManager.metadataCache

    fun requestLinkMetadata(url: String, clipId: Long? = null) {
        LinkMetadataManager.requestMetadata(
            url = url,
            scope = viewModelScope,
            repository = repository,
            clipId = clipId
        )
    }

    fun onHomeScreenShown(context: Context) {
        requestScrollToTop()
        if (hasCompletedInitialHomeLoad) {
            _isLoading.value = false
            checkClipboardAndSave(context, force = false)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val startTime = System.currentTimeMillis()
            initialDatabaseLoadComplete.await()
            checkClipboardAndSaveSuspend(context, force = false)
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 280L) {
                delay(280L - elapsed)
            }
            hasCompletedInitialHomeLoad = true
            _isLoading.value = false
            requestScrollToTop()
        }
    }

    fun onAppForegrounded(context: Context) {
        requestScrollToTop()
        checkClipboardAndSave(context, force = false)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        requestScrollToTop()
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        requestScrollToTop()
    }

    fun markInternalCopy(text: String) {
        val trimmed = text.trim()
        lastInternalCopiedText = trimmed
        lastProcessedClipText = trimmed
    }

    private fun readCurrentClipboardText(context: Context): String? {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val rawText = item.text?.toString() ?: item.coerceToText(context)?.toString()
                rawText?.trim()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun resolveCachedLinkMetadataForUrl(normalizedUrl: String): LinkMetadata? {
        if (normalizedUrl.isBlank()) return null
        LinkMetadataManager.getLoadedMetadata(normalizedUrl)?.let { return it }
        val dbEntity = try {
            repository.getCachedLinkMetadata(normalizedUrl)
        } catch (_: Exception) {
            null
        }
        if (dbEntity != null && dbEntity.isLoaded) {
            return LinkMetadataManager.fromCacheEntity(dbEntity)
        }
        return null
    }

    private suspend fun checkClipboardAndSaveSuspend(
        context: Context,
        force: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (!force) {
            val isAutoCaptureEnabled = try {
                userPreferences.autoCaptureFlow.first()
            } catch (e: Exception) {
                autoCapture.value
            }
            if (!isAutoCaptureEnabled) return
        }

        // Serialize all clipboard checks with a Mutex so concurrent lifecycle/listener triggers
        // never insert the same copied content multiple times.
        clipboardMutex.withLock {
            val retryDelays = if (force) listOf(0L) else listOf(0L, 120L)
            for (delayMs in retryDelays) {
                if (delayMs > 0L) delay(delayMs)
                val text = readCurrentClipboardText(context)
                if (!text.isNullOrEmpty()) {
                    if (!force && (text == lastInternalCopiedText || text == lastProcessedClipText)) {
                        return@withLock
                    }

                    val contentType = detectContentType(text)
                    val autoCategory = autoCategoryForContentType(contentType)
                    val detectedLink = LinkMetadataManager.extractFirstLink(text)
                    val linkUrl = detectedLink?.normalizedUrl ?: ""
                    val linkDomain = detectedLink?.domain ?: ""
                    val cachedMeta = resolveCachedLinkMetadataForUrl(linkUrl)

                    val existingClip = repository.getClipByText(text)
                    if (existingClip == null) {
                        val newClip = ClipEntity(
                            title = "",
                            text = text,
                            contentType = contentType,
                            category = autoCategory,
                            linkUrl = linkUrl,
                            linkDomain = cachedMeta?.domain ?: linkDomain,
                            linkSiteName = cachedMeta?.siteName ?: "",
                            linkTitle = cachedMeta?.title ?: "",
                            linkDescription = cachedMeta?.description ?: "",
                            linkFaviconBase64 = cachedMeta?.faviconBase64 ?: "",
                            linkMetadataLoaded = cachedMeta != null
                        )
                        val insertedId = repository.insertClip(newClip)
                        lastProcessedClipText = text
                        requestScrollToTop()
                        if (linkUrl.isNotBlank() && cachedMeta == null) {
                            requestLinkMetadata(linkUrl, insertedId)
                        }
                        onResult?.invoke(true, "Saved as $autoCategory!")
                    } else {
                        lastProcessedClipText = text
                        val updatedClip = existingClip.copy(
                            contentType = contentType,
                            category = if (existingClip.category.isBlank() || existingClip.category.equals("General", ignoreCase = true)) {
                                autoCategory
                            } else {
                                existingClip.category
                            },
                            timestamp = System.currentTimeMillis(),
                            linkUrl = linkUrl.ifBlank { existingClip.linkUrl },
                            linkDomain = cachedMeta?.domain ?: linkDomain.ifBlank { existingClip.linkDomain },
                            linkSiteName = cachedMeta?.siteName ?: existingClip.linkSiteName,
                            linkTitle = cachedMeta?.title ?: existingClip.linkTitle,
                            linkDescription = cachedMeta?.description ?: existingClip.linkDescription,
                            linkFaviconBase64 = cachedMeta?.faviconBase64 ?: existingClip.linkFaviconBase64,
                            linkMetadataLoaded = existingClip.linkMetadataLoaded || cachedMeta != null
                        )
                        repository.updateClip(updatedClip)
                        requestScrollToTop()
                        if (linkUrl.isNotBlank() && !updatedClip.linkMetadataLoaded) {
                            requestLinkMetadata(linkUrl, existingClip.id)
                        }
                        if (force) {
                            onResult?.invoke(true, "Clip moved to top!")
                        }
                    }
                    return@withLock
                }
            }
            if (force) {
                onResult?.invoke(false, "Clipboard is empty")
            }
        }
    }

    fun checkClipboardAndSave(
        context: Context,
        force: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            checkClipboardAndSaveSuspend(context, force, onResult)
        }
    }

    fun saveManualClip(title: String = "", text: String, category: String = "") {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        viewModelScope.launch {
            clipboardMutex.withLock {
                val detectedType = detectContentType(trimmedText)
                val contentType = when {
                    category.equals("Links", ignoreCase = true) -> "URL"
                    category.equals("Code", ignoreCase = true) -> "CODE"
                    category.equals("Text", ignoreCase = true) -> "TEXT"
                    else -> detectedType
                }
                val resolvedCategory = if (category.isBlank() || category.equals("General", ignoreCase = true)) {
                    autoCategoryForContentType(contentType)
                } else {
                    category
                }
                val detectedLink = LinkMetadataManager.extractFirstLink(trimmedText)
                val linkUrl = detectedLink?.normalizedUrl ?: ""
                val linkDomain = detectedLink?.domain ?: ""
                val cachedMeta = resolveCachedLinkMetadataForUrl(linkUrl)

                val newClip = ClipEntity(
                    title = title.trim(),
                    text = trimmedText,
                    contentType = contentType,
                    category = resolvedCategory,
                    linkUrl = linkUrl,
                    linkDomain = cachedMeta?.domain ?: linkDomain,
                    linkSiteName = cachedMeta?.siteName ?: "",
                    linkTitle = cachedMeta?.title ?: "",
                    linkDescription = cachedMeta?.description ?: "",
                    linkFaviconBase64 = cachedMeta?.faviconBase64 ?: "",
                    linkMetadataLoaded = cachedMeta != null
                )
                val insertedId = repository.insertClip(newClip)
                lastProcessedClipText = trimmedText
                requestScrollToTop()
                if (linkUrl.isNotBlank() && cachedMeta == null) {
                    requestLinkMetadata(linkUrl, insertedId)
                }
            }
        }
    }

    fun togglePin(clip: ClipEntity) {
        viewModelScope.launch {
            repository.updateClip(clip.copy(isPinned = !clip.isPinned))
        }
    }

    fun updateClip(clip: ClipEntity, newTitle: String, newText: String, newCategory: String) {
        val trimmedText = newText.trim()
        if (trimmedText.isBlank()) return
        viewModelScope.launch {
            clipboardMutex.withLock {
                val detectedType = detectContentType(trimmedText)
                val contentType = when {
                    newCategory.equals("Links", ignoreCase = true) -> "URL"
                    newCategory.equals("Code", ignoreCase = true) -> "CODE"
                    newCategory.equals("Text", ignoreCase = true) -> "TEXT"
                    else -> detectedType
                }
                val resolvedCategory = if (newCategory.isBlank() || newCategory.equals("General", ignoreCase = true)) {
                    autoCategoryForContentType(contentType)
                } else {
                    newCategory
                }
                val detectedLink = LinkMetadataManager.extractFirstLink(trimmedText)
                val linkUrl = detectedLink?.normalizedUrl ?: ""
                val linkDomain = detectedLink?.domain ?: ""
                val sameUrl = linkUrl.isNotBlank() && linkUrl == clip.linkUrl && clip.linkMetadataLoaded
                val cachedMeta = if (sameUrl) null else resolveCachedLinkMetadataForUrl(linkUrl)

                val updated = clip.copy(
                    title = newTitle.trim(),
                    text = trimmedText,
                    contentType = contentType,
                    category = resolvedCategory,
                    linkUrl = linkUrl,
                    linkDomain = when {
                        sameUrl -> clip.linkDomain
                        cachedMeta != null -> cachedMeta.domain
                        else -> linkDomain
                    },
                    linkSiteName = when {
                        sameUrl -> clip.linkSiteName
                        cachedMeta != null -> cachedMeta.siteName
                        else -> ""
                    },
                    linkTitle = when {
                        sameUrl -> clip.linkTitle
                        cachedMeta != null -> cachedMeta.title
                        else -> ""
                    },
                    linkDescription = when {
                        sameUrl -> clip.linkDescription
                        cachedMeta != null -> cachedMeta.description
                        else -> ""
                    },
                    linkFaviconBase64 = when {
                        sameUrl -> clip.linkFaviconBase64
                        cachedMeta != null -> cachedMeta.faviconBase64
                        else -> ""
                    },
                    linkMetadataLoaded = sameUrl || cachedMeta != null
                )
                repository.updateClip(updated)
                lastProcessedClipText = trimmedText
                if (linkUrl.isNotBlank() && !updated.linkMetadataLoaded) {
                    requestLinkMetadata(linkUrl, clip.id)
                }
            }
        }
    }

    fun deleteClip(id: Long) {
        viewModelScope.launch {
            clipboardMutex.withLock {
                val clip = repository.getClipById(id)
                if (clip != null) {
                    // Mark deleted clip text as already processed so auto-capture does not re-add it
                    val deletedText = clip.text.trim()
                    lastProcessedClipText = deletedText
                    lastInternalCopiedText = deletedText
                }
                repository.deleteClipById(id)
            }
        }
    }

    fun deleteClips(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val idList = ids.toList()
        viewModelScope.launch {
            clipboardMutex.withLock {
                val currentClipText = readCurrentClipboardText(appContext)
                for (id in idList) {
                    val clip = repository.getClipById(id)
                    if (clip != null) {
                        val deletedText = clip.text.trim()
                        if (currentClipText == null || deletedText == currentClipText) {
                            lastProcessedClipText = deletedText
                            lastInternalCopiedText = deletedText
                        }
                    }
                }
                if (!currentClipText.isNullOrEmpty()) {
                    lastProcessedClipText = currentClipText
                    lastInternalCopiedText = currentClipText
                }
                repository.deleteClipsByIds(idList)
            }
        }
    }

    fun deleteAllClips() {
        viewModelScope.launch {
            clipboardMutex.withLock {
                val currentClipText = readCurrentClipboardText(appContext)
                    ?: repository.getMostRecentClip()?.text?.trim()
                if (!currentClipText.isNullOrEmpty()) {
                    lastProcessedClipText = currentClipText
                    lastInternalCopiedText = currentClipText
                }

                try {
                    val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboardManager?.clearPrimaryClip()
                    } else {
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } catch (e: Exception) {
                    // Ignore if OS restricts clearing primary clip
                }

                repository.deleteAllClips()
            }
        }
    }

    fun setAutoCapture(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAutoCapture(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    suspend fun exportNotesAsText(): String {
        val list = repository.getAllClipsList()
        val jsonArray = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("title", item.title)
                put("text", item.text)
                put("category", item.category)
                put("contentType", item.contentType)
                put("isPinned", item.isPinned)
                put("timestamp", item.timestamp)
                put("linkUrl", item.linkUrl)
                put("linkDomain", item.linkDomain)
                put("linkSiteName", item.linkSiteName)
                put("linkTitle", item.linkTitle)
                put("linkDescription", item.linkDescription)
                put("linkFaviconBase64", item.linkFaviconBase64)
                put("linkMetadataLoaded", item.linkMetadataLoaded)
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    suspend fun importNotesFromText(content: String): Int {
        val importedList = mutableListOf<ClipEntity>()
        try {
            val trimmed = content.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                val jsonArray = org.json.JSONArray(trimmed)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val text = obj.optString("text", "")
                    if (text.isNotBlank()) {
                        val title = obj.optString("title", "")
                        val contentType = obj.optString("contentType", detectContentType(text))
                        val rawCategory = obj.optString("category", "")
                        val category = if (rawCategory.isBlank() || rawCategory.equals("General", ignoreCase = true)) {
                            autoCategoryForContentType(contentType)
                        } else {
                            rawCategory
                        }
                        val isPinned = obj.optBoolean("isPinned", false)
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        val detectedLink = LinkMetadataManager.extractFirstLink(text)
                        val linkUrl = obj.optString("linkUrl", detectedLink?.normalizedUrl ?: "")
                        val linkDomain = obj.optString("linkDomain", detectedLink?.domain ?: "")
                        val linkSiteName = obj.optString("linkSiteName", "")
                        val linkTitle = obj.optString("linkTitle", "")
                        val linkDescription = obj.optString("linkDescription", "")
                        val linkFaviconBase64 = obj.optString("linkFaviconBase64", "")
                        val linkMetadataLoaded = obj.optBoolean("linkMetadataLoaded", false)

                        importedList.add(
                            ClipEntity(
                                title = title,
                                text = text,
                                category = category,
                                contentType = contentType,
                                isPinned = isPinned,
                                timestamp = timestamp,
                                linkUrl = linkUrl,
                                linkDomain = linkDomain,
                                linkSiteName = linkSiteName,
                                linkTitle = linkTitle,
                                linkDescription = linkDescription,
                                linkFaviconBase64 = linkFaviconBase64,
                                linkMetadataLoaded = linkMetadataLoaded
                            )
                        )
                    }
                }
            } else {
                throw Exception("Not standard JSON array")
            }
        } catch (e: Exception) {
            val paragraphs = content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
            if (paragraphs.isNotEmpty()) {
                paragraphs.forEach { p ->
                    val type = detectContentType(p)
                    val detectedLink = LinkMetadataManager.extractFirstLink(p)
                    importedList.add(
                        ClipEntity(
                            title = "",
                            text = p,
                            category = autoCategoryForContentType(type),
                            contentType = type,
                            isPinned = false,
                            timestamp = System.currentTimeMillis(),
                            linkUrl = detectedLink?.normalizedUrl ?: "",
                            linkDomain = detectedLink?.domain ?: ""
                        )
                    )
                }
            } else {
                val lines = content.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                lines.forEach { l ->
                    val type = detectContentType(l)
                    val detectedLink = LinkMetadataManager.extractFirstLink(l)
                    importedList.add(
                        ClipEntity(
                            title = "",
                            text = l,
                            category = autoCategoryForContentType(type),
                            contentType = type,
                            isPinned = false,
                            timestamp = System.currentTimeMillis(),
                            linkUrl = detectedLink?.normalizedUrl ?: "",
                            linkDomain = detectedLink?.domain ?: ""
                        )
                    )
                }
            }
        }

        if (importedList.isNotEmpty()) {
            repository.insertClips(importedList)
            LinkMetadataManager.hydrateFromDatabase(repository, repository.getAllClipsList())
            requestScrollToTop()
        }
        return importedList.size
    }

    fun detectCategoryForText(text: String): String {
        return autoCategoryForContentType(detectContentType(text))
    }

    fun autoCategoryForContentType(contentType: String): String {
        return when (contentType.uppercase()) {
            "URL" -> "Links"
            "CODE" -> "Code"
            else -> "Text"
        }
    }

    fun detectContentType(text: String): String {
        val trimmed = text.trim()
        val hasSingleTokenLink = !trimmed.contains(" ") && !trimmed.contains("\n") &&
            LinkMetadataManager.extractFirstLink(trimmed) != null
        val isCodeSnippet = (trimmed.contains("{") && trimmed.contains("}")) ||
            (trimmed.contains("<") && trimmed.contains("</") && trimmed.contains(">")) ||
            trimmed.contains("fun ") ||
            trimmed.contains("function ") ||
            trimmed.contains("class ") ||
            trimmed.contains("val ") ||
            trimmed.contains("var ") ||
            trimmed.contains("const ") ||
            trimmed.contains("import ") ||
            trimmed.contains("export ") ||
            trimmed.contains("def ") ||
            trimmed.contains("return ") ||
            trimmed.contains("public ") ||
            trimmed.contains("private ") ||
            trimmed.contains("#include") ||
            trimmed.contains("=>") ||
            trimmed.contains("println(") ||
            trimmed.contains("console.log(") ||
            (trimmed.contains("SELECT ", ignoreCase = false) && trimmed.contains("FROM ", ignoreCase = false))

        return when {
            trimmed.startsWith("http://", true) ||
                trimmed.startsWith("https://", true) ||
                trimmed.startsWith("www.", true) ||
                trimmed.startsWith("ftp://", true) ||
                hasSingleTokenLink -> "URL"

            isCodeSnippet -> "CODE"

            LinkMetadataManager.extractFirstLink(trimmed) != null -> "URL"

            else -> "TEXT"
        }
    }
}
