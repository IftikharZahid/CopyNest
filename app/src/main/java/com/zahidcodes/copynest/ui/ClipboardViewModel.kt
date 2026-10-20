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
import com.zahidcodes.copynest.data.DeletedClipEntity
import com.zahidcodes.copynest.data.LinkMetadata
import com.zahidcodes.copynest.data.LinkMetadataEntity
import com.zahidcodes.copynest.data.LinkMetadataManager
import com.zahidcodes.copynest.data.UserPreferences
import java.security.MessageDigest
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
    private var lastProcessedClipTimestamp: Long = 0L

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
        checkClipboardAndSave(appContext, force = false, isFromListener = true)
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
                    // Prune old deleted records older than 30 days
                    try {
                        repository.pruneOldDeletedClips(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000L)
                    } catch (_: Exception) {}

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

                    // Inspect initial system clipboard state to prevent stale deleted clipboard items from re-importing
                    val initialClipboard = readCurrentClipboardData(appContext)
                    if (initialClipboard != null) {
                        val clipText = initialClipboard.text
                        val clipTimestamp = initialClipboard.timestamp
                        val hash = computeContentFingerprint(clipText)
                        val isExisting = activeClips.any { it.text.trim() == clipText }
                        val deletedRecord = repository.getDeletedClip(hash)
                        if (deletedRecord != null) {
                            deletedHashesCache.add(hash)
                        }
                        val isDeletedStale = deletedRecord != null && (
                            (clipTimestamp > 0L && clipTimestamp <= deletedRecord.deletedAt) ||
                            (deletedRecord.clipboardTimestamp > 0L && clipTimestamp <= deletedRecord.clipboardTimestamp) ||
                            (clipTimestamp <= 0L)
                        )
                        if (isExisting || isDeletedStale) {
                            lastProcessedClipText = clipText
                            lastProcessedClipTimestamp = clipTimestamp
                        }
                    }
                    if (lastProcessedClipText == null) {
                        lastProcessedClipText = activeClips.firstOrNull()?.text?.trim()
                    }
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
            synchronized(listenerLock) {
                activeClipboardListener?.let { oldListener ->
                    try {
                        clipboardManager?.removePrimaryClipChangedListener(oldListener)
                    } catch (_: Exception) {}
                }
                clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
                activeClipboardListener = clipChangedListener
            }
        } catch (e: Exception) {
            // Ignore clipboard listener registration restrictions
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            synchronized(listenerLock) {
                clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
                if (activeClipboardListener === clipChangedListener) {
                    activeClipboardListener = null
                }
            }
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
            checkClipboardAndSave(context, force = false, isFromListener = false)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val startTime = System.currentTimeMillis()
            initialDatabaseLoadComplete.await()
            checkClipboardAndSaveSuspend(context, force = false, isFromListener = false)
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
        checkClipboardAndSave(context, force = false, isFromListener = false)
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
        lastProcessedClipTimestamp = System.currentTimeMillis()
    }

    data class SystemClipboardData(
        val text: String,
        val timestamp: Long
    )

    private fun readCurrentClipboardData(context: Context): SystemClipboardData? {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val rawText = item.text?.toString() ?: item.coerceToText(context)?.toString()
                val text = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    clipData.description?.timestamp ?: 0L
                } else {
                    0L
                }
                SystemClipboardData(text = text, timestamp = timestamp)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readCurrentClipboardText(context: Context): String? {
        return readCurrentClipboardData(context)?.text
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
        isFromListener: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        if (!force && !autoCapture.value) {
            return
        }

        // Serialize all clipboard checks with a Mutex so concurrent lifecycle/listener triggers
        // never insert the same copied content multiple times.
        clipboardMutex.withLock {
            val retryDelays = if (force) listOf(0L) else listOf(0L, 120L)
            for (delayMs in retryDelays) {
                if (delayMs > 0L) delay(delayMs)
                val clipData = readCurrentClipboardData(context)
                if (clipData != null && clipData.text.isNotEmpty()) {
                    val text = clipData.text
                    val clipTimestamp = clipData.timestamp
                    val hash = computeContentFingerprint(text)

                    // 1. Skip if internal copy by the app
                    if (!force && text == lastInternalCopiedText) {
                        return@withLock
                    }
                    if (text != lastInternalCopiedText) {
                        lastInternalCopiedText = null
                    }

                    val deletedRecord = if (deletedHashesCache.contains(hash) || !initialDatabaseLoadComplete.isCompleted) {
                        repository.getDeletedClip(hash)
                    } else {
                        null
                    }

                    // 2. Skip if already processed in this session with same or older timestamp
                    // (unless this is a fresh listener copy event for a previously deleted item)
                    if (!force && text == lastProcessedClipText && !(isFromListener && deletedRecord != null)) {
                        if (clipTimestamp <= 0L || clipTimestamp <= lastProcessedClipTimestamp) {
                            return@withLock
                        }
                    }

                    // 3. Persistent deletion check: ensure previously deleted entries are not resurrected
                    if (deletedRecord != null && !force) {
                        // Determine whether this clipboard item is old stale content from before deletion,
                        // or a genuine new copy event that occurred after deletion.
                        val isLegitimateNewCopy = when {
                            isFromListener -> {
                                // OnPrimaryClipChangedListener only fires when primary clip is actively set
                                clipTimestamp <= 0L ||
                                    clipTimestamp >= deletedRecord.deletedAt ||
                                    (deletedRecord.clipboardTimestamp > 0L && clipTimestamp > deletedRecord.clipboardTimestamp)
                            }
                            clipTimestamp > 0L -> {
                                val afterDeletion = clipTimestamp > deletedRecord.deletedAt
                                val newerThanDeletedClipboard = deletedRecord.clipboardTimestamp > 0L &&
                                    clipTimestamp > deletedRecord.clipboardTimestamp
                                afterDeletion || newerThanDeletedClipboard
                            }
                            else -> {
                                // Startup or resume polling: stale clipboard content must NOT be re-imported
                                false
                            }
                        }

                        if (!isLegitimateNewCopy) {
                            lastProcessedClipText = text
                            lastProcessedClipTimestamp = clipTimestamp
                            return@withLock
                        } else {
                            // Legitimate new copy: remove tombstone since user explicitly re-copied this content
                            deletedHashesCache.remove(hash)
                            repository.deleteDeletedClip(hash)
                        }
                    }

                    // Since the system clipboard now holds valid content different from any stale deleted clip,
                    // any previous deletion tombstones are no longer sitting on the system clipboard.
                    if (deletedHashesCache.isNotEmpty()) {
                        deletedHashesCache.clear()
                        repository.deleteAllDeletedClips()
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
                        lastProcessedClipTimestamp = clipTimestamp
                        requestScrollToTop()
                        if (linkUrl.isNotBlank() && cachedMeta == null) {
                            requestLinkMetadata(linkUrl, insertedId)
                        }
                        onResult?.invoke(true, "Saved as $autoCategory!")
                    } else {
                        lastProcessedClipText = text
                        lastProcessedClipTimestamp = clipTimestamp
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
        isFromListener: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            checkClipboardAndSaveSuspend(context, force, isFromListener, onResult)
        }
    }

    fun saveManualClip(title: String = "", text: String, category: String = "") {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return
        viewModelScope.launch {
            clipboardMutex.withLock {
                val hash = computeContentFingerprint(trimmedText)
                if (deletedHashesCache.remove(hash)) {
                    repository.deleteDeletedClip(hash)
                }

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
                lastProcessedClipTimestamp = System.currentTimeMillis()
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
                val hash = computeContentFingerprint(trimmedText)
                if (deletedHashesCache.remove(hash)) {
                    repository.deleteDeletedClip(hash)
                }

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
                lastProcessedClipTimestamp = System.currentTimeMillis()
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
                    val deletedText = clip.text.trim()
                    val hash = computeContentFingerprint(deletedText)
                    val currentClipboard = readCurrentClipboardData(appContext)
                    val isOnClipboard = currentClipboard != null &&
                        computeContentFingerprint(currentClipboard.text) == hash
                    val clipTimestamp = if (isOnClipboard) currentClipboard.timestamp else 0L

                    // Persist tombstone in Room to prevent re-import upon app restarts
                    val deletedEntity = DeletedClipEntity(
                        contentHash = hash,
                        normalizedText = deletedText.take(500),
                        deletedAt = System.currentTimeMillis(),
                        clipboardTimestamp = clipTimestamp
                    )
                    deletedHashesCache.add(hash)
                    repository.insertDeletedClip(deletedEntity)

                    lastProcessedClipText = deletedText
                    lastProcessedClipTimestamp = clipTimestamp
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
                val currentClipboard = readCurrentClipboardData(appContext)
                val currentHash = currentClipboard?.let { computeContentFingerprint(it.text) }
                val now = System.currentTimeMillis()
                val deletedEntities = mutableListOf<DeletedClipEntity>()

                for (id in idList) {
                    val clip = repository.getClipById(id) ?: continue
                    val text = clip.text.trim()
                    val hash = computeContentFingerprint(text)
                    val isOnClipboard = currentHash != null && currentHash == hash
                    val clipTimestamp = if (isOnClipboard) (currentClipboard?.timestamp ?: 0L) else 0L

                    deletedHashesCache.add(hash)
                    deletedEntities.add(
                        DeletedClipEntity(
                            contentHash = hash,
                            normalizedText = text.take(500),
                            deletedAt = now,
                            clipboardTimestamp = clipTimestamp
                        )
                    )
                    if (isOnClipboard) {
                        lastProcessedClipText = text
                        lastProcessedClipTimestamp = clipTimestamp
                    }
                }
                if (deletedEntities.isNotEmpty()) {
                    repository.insertDeletedClips(deletedEntities)
                }
                repository.deleteClipsByIds(idList)
            }
        }
    }

    fun deleteAllClips() {
        viewModelScope.launch {
            clipboardMutex.withLock {
                val currentClipboard = readCurrentClipboardData(appContext)
                val currentHash = currentClipboard?.let { computeContentFingerprint(it.text) }
                val now = System.currentTimeMillis()

                val allClips = repository.getAllClipsList()
                val deletedEntities = allClips.map { clip ->
                    val text = clip.text.trim()
                    val hash = computeContentFingerprint(text)
                    deletedHashesCache.add(hash)
                    val isOnClipboard = currentHash != null && currentHash == hash
                    val clipTimestamp = if (isOnClipboard) (currentClipboard?.timestamp ?: 0L) else 0L
                    DeletedClipEntity(
                        contentHash = hash,
                        normalizedText = text.take(500),
                        deletedAt = now,
                        clipboardTimestamp = clipTimestamp
                    )
                }
                if (deletedEntities.isNotEmpty()) {
                    repository.insertDeletedClips(deletedEntities)
                }
                if (currentClipboard != null) {
                    lastProcessedClipText = currentClipboard.text
                    lastProcessedClipTimestamp = currentClipboard.timestamp
                    lastInternalCopiedText = currentClipboard.text
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

    companion object {
        private val listenerLock = Any()
        private var activeClipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
        private val deletedHashesCache: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        fun computeContentFingerprint(text: String): String {
            val trimmed = text.trim()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(trimmed.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
