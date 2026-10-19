package com.zahidcodes.copynest.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
import android.util.Base64
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class DetectedLink(
    val rawText: String,
    val normalizedUrl: String,
    val domain: String,
    val startIndex: Int,
    val endIndex: Int
)

data class LinkMetadata(
    val url: String,
    val domain: String,
    val siteName: String,
    val title: String,
    val description: String = "",
    val favicon: ImageBitmap? = null,
    val faviconBase64: String = "",
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false
)

object LinkMetadataManager {

    private val urlRegex = Regex(
        pattern = """(?i)\b((?:https?://|ftp://|www\.)[^\s<>"]+|[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.(?:com|org|net|io|dev|app|edu|gov|co|me|ai|pk|in|uk|us|xyz|info|biz|gg|tv|ly|gl|to|sh|tech|online|site|store|blog|cloud)(?:/[^\s<>"]*)?)"""
    )

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Global in-memory state store (acts like a Redux slice for link metadata across the app)
    private val _metadataCache = MutableStateFlow<Map<String, LinkMetadata>>(emptyMap())
    val metadataCache: StateFlow<Map<String, LinkMetadata>> = _metadataCache.asStateFlow()

    private val faviconBitmapCache = ConcurrentHashMap<String, ImageBitmap>()
    private val inFlightUrls = mutableSetOf<String>()
    private val fetchMutex = Mutex()

    fun extractLinks(text: String): List<DetectedLink> {
        if (text.isBlank()) return emptyList()
        val results = mutableListOf<DetectedLink>()
        val matches = urlRegex.findAll(text)
        for (match in matches) {
            var raw = match.value
            var endIdx = match.range.last + 1

            // Trim trailing sentence punctuation that isn't part of the URL
            while (raw.isNotEmpty() && raw.last() in charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')) {
                val lastChar = raw.last()
                if (lastChar == ')' && raw.count { it == '(' } >= raw.count { it == ')' }) {
                    break
                }
                raw = raw.dropLast(1)
                endIdx--
            }

            if (raw.length < 4) continue

            // Avoid matching email addresses as bare domain links
            val startIdx = match.range.first
            if (startIdx > 0 && text[startIdx - 1] == '@') continue

            val normalized = normalizeUrl(raw)
            val domain = extractDomain(normalized)
            if (domain.isNotBlank()) {
                results.add(
                    DetectedLink(
                        rawText = raw,
                        normalizedUrl = normalized,
                        domain = domain,
                        startIndex = startIdx,
                        endIndex = endIdx
                    )
                )
            }
        }
        return results
    }

    fun extractFirstLink(text: String): DetectedLink? {
        return extractLinks(text).firstOrNull()
    }

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("ftp://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun extractDomain(url: String): String {
        return try {
            val normalized = normalizeUrl(url)
            val uri = URI(normalized)
            val host = uri.host ?: Uri.parse(normalized).host ?: ""
            host.removePrefix("www.").lowercase()
        } catch (e: Exception) {
            try {
                val host = Uri.parse(normalizeUrl(url)).host ?: ""
                host.removePrefix("www.").lowercase()
            } catch (_: Exception) {
                url.removePrefix("https://")
                    .removePrefix("http://")
                    .removePrefix("www.")
                    .substringBefore("/")
                    .substringBefore("?")
                    .lowercase()
            }
        }
    }

    fun formatFallbackSiteName(domain: String): String {
        val clean = domain.removePrefix("www.")
        val mainPart = clean.substringBefore(".")
        return when (clean.lowercase()) {
            "github.com" -> "GitHub"
            "youtube.com", "youtu.be" -> "YouTube"
            "stackoverflow.com" -> "Stack Overflow"
            "linkedin.com" -> "LinkedIn"
            "twitter.com", "x.com" -> "X (Twitter)"
            "facebook.com", "fb.com" -> "Facebook"
            "instagram.com" -> "Instagram"
            "whatsapp.com", "wa.me" -> "WhatsApp"
            "medium.com" -> "Medium"
            "reddit.com" -> "Reddit"
            "google.com" -> "Google"
            "buymeacoffee.com" -> "Buy Me a Coffee"
            else -> mainPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .ifBlank { domain }
        }
    }

    fun formatFallbackTitle(url: String, domain: String): String {
        val siteName = formatFallbackSiteName(domain)
        return try {
            val uri = Uri.parse(normalizeUrl(url))
            val pathSegments = uri.pathSegments?.filter { it.isNotBlank() }.orEmpty()
            if (pathSegments.isNotEmpty()) {
                val readablePath = pathSegments.take(2).joinToString(" / ") { segment ->
                    Uri.decode(segment)
                        .replace('-', ' ')
                        .replace('_', ' ')
                }
                "$siteName • $readablePath"
            } else {
                "$siteName ($domain)"
            }
        } catch (e: Exception) {
            siteName
        }
    }

    fun openUrl(context: Context, rawUrl: String) {
        try {
            val normalized = normalizeUrl(rawUrl)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }

    fun decodeFaviconBase64(cacheKey: String, base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        faviconBitmapCache[cacheKey]?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            if (bmp != null) {
                faviconBitmapCache[cacheKey] = bmp
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun fromClipEntity(clip: ClipEntity, expectedUrl: String? = null): LinkMetadata? {
        if (!clip.linkMetadataLoaded) return null
        val url = clip.linkUrl.ifBlank { expectedUrl ?: return null }
        if (expectedUrl != null && url != expectedUrl) return null
        val domain = clip.linkDomain.ifBlank { extractDomain(url) }
        if (domain.isBlank()) return null
        val siteName = clip.linkSiteName.ifBlank { formatFallbackSiteName(domain) }
        val title = clip.linkTitle.ifBlank { formatFallbackTitle(url, domain) }
        val favicon = decodeFaviconBase64(url, clip.linkFaviconBase64)
        return LinkMetadata(
            url = url,
            domain = domain,
            siteName = siteName,
            title = title,
            description = clip.linkDescription,
            favicon = favicon,
            faviconBase64 = clip.linkFaviconBase64,
            isLoading = false,
            isLoaded = true
        )
    }

    fun fromCacheEntity(entity: LinkMetadataEntity): LinkMetadata {
        val domain = entity.domain.ifBlank { extractDomain(entity.normalizedUrl) }
        val siteName = entity.siteName.ifBlank { formatFallbackSiteName(domain) }
        val title = entity.title.ifBlank { formatFallbackTitle(entity.normalizedUrl, domain) }
        val favicon = decodeFaviconBase64(entity.normalizedUrl, entity.faviconBase64)
        return LinkMetadata(
            url = entity.normalizedUrl,
            domain = domain,
            siteName = siteName,
            title = title,
            description = entity.description,
            favicon = favicon,
            faviconBase64 = entity.faviconBase64,
            isLoading = false,
            isLoaded = true
        )
    }

    suspend fun hydrateFromDatabase(repository: ClipRepository, clips: List<ClipEntity>) {
        val restoredMap = mutableMapOf<String, LinkMetadata>()

        // 1. Load all entries from the dedicated link_metadata_cache table
        val cachedEntities = try {
            repository.getAllCachedLinkMetadata()
        } catch (_: Exception) {
            emptyList()
        }
        for (entity in cachedEntities) {
            restoredMap[entity.normalizedUrl] = fromCacheEntity(entity)
        }

        // 2. Also load any persisted metadata directly from ClipEntity rows
        for (clip in clips) {
            if (clip.linkMetadataLoaded && clip.linkUrl.isNotBlank()) {
                val fromClip = fromClipEntity(clip)
                if (fromClip != null) {
                    if (!restoredMap.containsKey(fromClip.url)) {
                        restoredMap[fromClip.url] = fromClip
                    }
                    // Ensure link_metadata_cache table also has this entry
                    if (cachedEntities.none { it.normalizedUrl == fromClip.url }) {
                        try {
                            repository.insertCachedLinkMetadata(
                                LinkMetadataEntity(
                                    normalizedUrl = fromClip.url,
                                    domain = fromClip.domain,
                                    siteName = fromClip.siteName,
                                    title = fromClip.title,
                                    description = fromClip.description,
                                    faviconBase64 = fromClip.faviconBase64,
                                    isLoaded = true
                                )
                            )
                        } catch (_: Exception) {
                            // Ignore DB write error
                        }
                    }
                }
            } else {
                // If clip doesn't have linkMetadataLoaded marked yet, check if its URL is already in restoredMap
                val detected = extractFirstLink(clip.text)
                if (detected != null) {
                    val cached = restoredMap[detected.normalizedUrl]
                    if (cached != null && cached.isLoaded) {
                        try {
                            repository.updateClipLinkMetadataById(
                                clipId = clip.id,
                                linkUrl = cached.url,
                                linkDomain = cached.domain,
                                linkSiteName = cached.siteName,
                                linkTitle = cached.title,
                                linkDescription = cached.description,
                                linkFaviconBase64 = cached.faviconBase64
                            )
                        } catch (_: Exception) {
                            // Ignore DB write error
                        }
                    }
                }
            }
        }

        if (restoredMap.isNotEmpty()) {
            _metadataCache.update { current ->
                restoredMap + current.filterValues { it.isLoaded }
            }
        }
    }

    fun getLoadedMetadata(url: String): LinkMetadata? {
        val normalized = normalizeUrl(url)
        return _metadataCache.value[normalized]?.takeIf { it.isLoaded }
    }

    fun requestMetadata(
        url: String,
        scope: CoroutineScope,
        repository: ClipRepository? = null,
        clipId: Long? = null
    ) {
        val normalized = normalizeUrl(url)
        val domain = extractDomain(normalized)
        if (domain.isBlank()) return

        val existing = _metadataCache.value[normalized]
        if (existing != null && existing.isLoaded) {
            if (repository != null && clipId != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        repository.updateClipLinkMetadataById(
                            clipId = clipId,
                            linkUrl = existing.url,
                            linkDomain = existing.domain,
                            linkSiteName = existing.siteName,
                            linkTitle = existing.title,
                            linkDescription = existing.description,
                            linkFaviconBase64 = existing.faviconBase64
                        )
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
            }
            return
        }

        if (existing != null && existing.isLoading) {
            return
        }

        scope.launch(Dispatchers.IO) {
            val shouldFetch = fetchMutex.withLock {
                val cachedInMemory = _metadataCache.value[normalized]
                if (cachedInMemory != null && cachedInMemory.isLoaded) {
                    if (repository != null && clipId != null) {
                        try {
                            repository.updateClipLinkMetadataById(
                                clipId = clipId,
                                linkUrl = cachedInMemory.url,
                                linkDomain = cachedInMemory.domain,
                                linkSiteName = cachedInMemory.siteName,
                                linkTitle = cachedInMemory.title,
                                linkDescription = cachedInMemory.description,
                                linkFaviconBase64 = cachedInMemory.faviconBase64
                            )
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                    return@withLock false
                }

                if (cachedInMemory != null && cachedInMemory.isLoading) {
                    return@withLock false
                }

                if (inFlightUrls.contains(normalized)) {
                    return@withLock false
                }

                // Check Room Database persistent cache BEFORE triggering any network request!
                if (repository != null) {
                    val dbCached = try {
                        repository.getCachedLinkMetadata(normalized)
                    } catch (_: Exception) {
                        null
                    }
                    if (dbCached != null && dbCached.isLoaded) {
                        val hydrated = fromCacheEntity(dbCached)
                        _metadataCache.update { current ->
                            current + (normalized to hydrated)
                        }
                        if (clipId != null) {
                            try {
                                repository.updateClipLinkMetadataById(
                                    clipId = clipId,
                                    linkUrl = hydrated.url,
                                    linkDomain = hydrated.domain,
                                    linkSiteName = hydrated.siteName,
                                    linkTitle = hydrated.title,
                                    linkDescription = hydrated.description,
                                    linkFaviconBase64 = hydrated.faviconBase64
                                )
                            } catch (_: Exception) {
                                // Ignore
                            }
                        }
                        return@withLock false
                    }
                }

                inFlightUrls.add(normalized)
                _metadataCache.update { current ->
                    current + (normalized to LinkMetadata(
                        url = normalized,
                        domain = domain,
                        siteName = formatFallbackSiteName(domain),
                        title = formatFallbackTitle(normalized, domain),
                        description = "",
                        isLoading = true,
                        isLoaded = false
                    ))
                }
                true
            }

            if (!shouldFetch) return@launch

            val finalMetadata = try {
                fetchUrlMetadataInternal(normalized, domain)
            } catch (e: Exception) {
                LinkMetadata(
                    url = normalized,
                    domain = domain,
                    siteName = formatFallbackSiteName(domain),
                    title = formatFallbackTitle(normalized, domain),
                    description = normalized,
                    favicon = null,
                    faviconBase64 = "",
                    isLoading = false,
                    isLoaded = true
                )
            }

            try {
                // 1. Save in-memory global StateFlow store
                _metadataCache.update { current ->
                    current + (normalized to finalMetadata)
                }

                // 2. Persist permanently in Room Database (both link_metadata_cache and clips tables)
                if (repository != null) {
                    try {
                        repository.insertCachedLinkMetadata(
                            LinkMetadataEntity(
                                normalizedUrl = finalMetadata.url,
                                domain = finalMetadata.domain,
                                siteName = finalMetadata.siteName,
                                title = finalMetadata.title,
                                description = finalMetadata.description,
                                faviconBase64 = finalMetadata.faviconBase64,
                                isLoaded = true
                            )
                        )
                        if (clipId != null) {
                            repository.updateClipLinkMetadataById(
                                clipId = clipId,
                                linkUrl = finalMetadata.url,
                                linkDomain = finalMetadata.domain,
                                linkSiteName = finalMetadata.siteName,
                                linkTitle = finalMetadata.title,
                                linkDescription = finalMetadata.description,
                                linkFaviconBase64 = finalMetadata.faviconBase64
                            )
                        }
                        repository.updateClipsLinkMetadataByUrl(
                            linkUrl = finalMetadata.url,
                            linkDomain = finalMetadata.domain,
                            linkSiteName = finalMetadata.siteName,
                            linkTitle = finalMetadata.title,
                            linkDescription = finalMetadata.description,
                            linkFaviconBase64 = finalMetadata.faviconBase64
                        )
                    } catch (_: Exception) {
                        // Ignore DB errors
                    }
                }
            } finally {
                fetchMutex.withLock {
                    inFlightUrls.remove(normalized)
                }
            }
        }
    }

    private fun fetchUrlMetadataInternal(normalizedUrl: String, domain: String): LinkMetadata {
        var title = ""
        var description = ""
        var siteName = ""

        try {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type") ?: ""
                if (response.isSuccessful && (contentType.contains("html", ignoreCase = true) || contentType.isBlank())) {
                    val bodyStream = response.body?.byteStream()
                    if (bodyStream != null) {
                        val buffer = ByteArray(65536)
                        var totalRead = 0
                        while (totalRead < buffer.size) {
                            val read = bodyStream.read(buffer, totalRead, buffer.size - totalRead)
                            if (read == -1) break
                            totalRead += read
                            val partial = String(buffer, 0, totalRead, Charsets.UTF_8)
                            if (partial.contains("</head>", ignoreCase = true)) {
                                break
                            }
                        }
                        val html = String(buffer, 0, totalRead, Charsets.UTF_8)
                        title = extractMetaTag(html, "og:title")
                            .ifBlank { extractMetaTag(html, "twitter:title") }
                            .ifBlank { extractHtmlTitle(html) }

                        description = extractMetaTag(html, "og:description")
                            .ifBlank { extractMetaTag(html, "twitter:description") }
                            .ifBlank { extractMetaTag(html, "description") }

                        siteName = extractMetaTag(html, "og:site_name")
                            .ifBlank { extractMetaTag(html, "application-name") }
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to domain-based metadata below if HTML fetch fails
        }

        val (faviconBitmap, faviconBase64) = fetchFaviconBitmapAndBase64(normalizedUrl, domain)

        val resolvedSiteName = siteName.ifBlank { formatFallbackSiteName(domain) }
        val resolvedTitle = title.ifBlank { formatFallbackTitle(normalizedUrl, domain) }
        val resolvedDescription = description.take(180).trim()

        return LinkMetadata(
            url = normalizedUrl,
            domain = domain,
            siteName = resolvedSiteName,
            title = resolvedTitle,
            description = resolvedDescription,
            favicon = faviconBitmap,
            faviconBase64 = faviconBase64,
            isLoading = false,
            isLoaded = true
        )
    }

    private fun fetchFaviconBitmapAndBase64(cacheKey: String, domain: String): Pair<ImageBitmap?, String> {
        return try {
            val faviconUrl = "https://www.google.com/s2/favicons?domain=${Uri.encode(domain)}&sz=64"
            val request = Request.Builder()
                .url(faviconUrl)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (rawBitmap != null) {
                            val output = ByteArrayOutputStream()
                            rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                            val imageBitmap = rawBitmap.asImageBitmap()
                            faviconBitmapCache[cacheKey] = imageBitmap
                            imageBitmap to base64
                        } else {
                            null to ""
                        }
                    } else {
                        null to ""
                    }
                } else {
                    null to ""
                }
            }
        } catch (_: Exception) {
            null to ""
        }
    }

    private fun extractHtmlTitle(html: String): String {
        val regex = Regex("""(?is)<title[^>]*>(.*?)</title>""")
        val raw = regex.find(html)?.groupValues?.getOrNull(1) ?: return ""
        return cleanHtmlText(raw)
    }

    private fun extractMetaTag(html: String, key: String): String {
        val escapedKey = Regex.escape(key)
        val patterns = listOf(
            Regex("""(?is)<meta[^>]+(?:property|name|itemprop)\s*=\s*["']$escapedKey["'][^>]+content\s*=\s*["'](.*?)["'][^>]*>"""),
            Regex("""(?is)<meta[^>]+content\s*=\s*["'](.*?)["'][^>]+(?:property|name|itemprop)\s*=\s*["']$escapedKey["'][^>]*>""")
        )
        for (pattern in patterns) {
            val match = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) {
                return cleanHtmlText(match)
            }
        }
        return ""
    }

    private fun cleanHtmlText(raw: String): String {
        return try {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace(Regex("""\s+"""), " ")
                .trim()
        } catch (e: Exception) {
            raw.replace(Regex("""\s+"""), " ").trim()
        }
    }
}
