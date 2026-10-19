package com.zahidcodes.copynets.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.Html
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
import java.net.URI
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

    private val _metadataCache = MutableStateFlow<Map<String, LinkMetadata>>(emptyMap())
    val metadataCache: StateFlow<Map<String, LinkMetadata>> = _metadataCache.asStateFlow()

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

    fun requestMetadata(url: String, scope: CoroutineScope) {
        val normalized = normalizeUrl(url)
        val domain = extractDomain(normalized)
        if (domain.isBlank()) return

        val existing = _metadataCache.value[normalized]
        if (existing != null && (existing.isLoaded || existing.isLoading)) {
            return
        }

        scope.launch(Dispatchers.IO) {
            val shouldFetch = fetchMutex.withLock {
                val cached = _metadataCache.value[normalized]
                if (cached != null && (cached.isLoaded || cached.isLoading)) {
                    false
                } else if (inFlightUrls.contains(normalized)) {
                    false
                } else {
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
            }

            if (!shouldFetch) return@launch

            try {
                val fetched = fetchUrlMetadataInternal(normalized, domain)
                _metadataCache.update { current ->
                    current + (normalized to fetched)
                }
            } catch (e: Exception) {
                _metadataCache.update { current ->
                    current + (normalized to LinkMetadata(
                        url = normalized,
                        domain = domain,
                        siteName = formatFallbackSiteName(domain),
                        title = formatFallbackTitle(normalized, domain),
                        description = normalized,
                        favicon = null,
                        isLoading = false,
                        isLoaded = true
                    ))
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

        val faviconBitmap = fetchFaviconBitmap(domain)

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
            isLoading = false,
            isLoaded = true
        )
    }

    private fun fetchFaviconBitmap(domain: String): ImageBitmap? {
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
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
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
