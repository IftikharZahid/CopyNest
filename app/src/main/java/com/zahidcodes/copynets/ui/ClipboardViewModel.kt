package com.zahidcodes.copynets.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zahidcodes.copynets.data.ClipDatabase
import com.zahidcodes.copynets.data.ClipEntity
import com.zahidcodes.copynets.data.ClipRepository
import com.zahidcodes.copynets.data.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClipboardViewModel(context: Context) : ViewModel() {
    private val database = ClipDatabase.getDatabase(context)
    private val repository = ClipRepository(database.clipDao())
    private val userPreferences = UserPreferences(context)

    val autoCapture = userPreferences.autoCaptureFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.themeModeFlow.collect { mode ->
                _themeMode.value = mode
            }
        }
        try {
            val appContext = context.applicationContext
            val clipboardManager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener {
                checkClipboardAndSave(appContext)
            }
        } catch (e: Exception) {
            // ignore
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
                it.category.contains(query, ignoreCase = true) 
            }
        }

        // Filter by chip
        when (filter) {
            "PINNED" -> result = result.filter { it.isPinned }
            "URL" -> result = result.filter { it.contentType == "URL" }
            "CODE" -> result = result.filter { it.contentType == "CODE" }
            "TEXT" -> result = result.filter { it.contentType == "TEXT" }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun checkClipboardAndSave(context: Context) {
        viewModelScope.launch {
            if (!autoCapture.value) return@launch
            try {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = clipboardManager?.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        val mostRecent = repository.getMostRecentClip()
                        if (mostRecent?.text != text) {
                            val contentType = detectContentType(text)
                            val newClip = ClipEntity(
                                title = "",
                                text = text,
                                contentType = contentType,
                                category = if (contentType == "URL") "Links" else "General"
                            )
                            repository.insertClip(newClip)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle clipboard security restrictions gracefully
            }
        }
    }

    fun saveManualClip(title: String = "", text: String, category: String = "General") {
        if (text.isBlank()) return
        viewModelScope.launch {
            val contentType = detectContentType(text)
            val newClip = ClipEntity(
                title = title.trim(),
                text = text.trim(),
                contentType = contentType,
                category = category.ifBlank { "General" }
            )
            repository.insertClip(newClip)
        }
    }

    fun togglePin(clip: ClipEntity) {
        viewModelScope.launch {
            repository.updateClip(clip.copy(isPinned = !clip.isPinned))
        }
    }

    fun updateClip(clip: ClipEntity, newTitle: String, newText: String, newCategory: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            val contentType = detectContentType(newText)
            repository.updateClip(
                clip.copy(
                    title = newTitle.trim(),
                    text = newText.trim(),
                    contentType = contentType,
                    category = newCategory.ifBlank { "General" }
                )
            )
        }
    }

    fun deleteClip(id: Long) {
        viewModelScope.launch {
            repository.deleteClipById(id)
        }
    }

    fun deleteAllClips() {
        viewModelScope.launch {
            repository.deleteAllClips()
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
                        val category = obj.optString("category", "General")
                        val contentType = obj.optString("contentType", detectContentType(text))
                        val isPinned = obj.optBoolean("isPinned", false)
                        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        importedList.add(
                            ClipEntity(
                                title = title,
                                text = text,
                                category = category,
                                contentType = contentType,
                                isPinned = isPinned,
                                timestamp = timestamp
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
                    importedList.add(
                        ClipEntity(
                            title = "",
                            text = p,
                            category = "Imported",
                            contentType = detectContentType(p),
                            isPinned = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                val lines = content.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                lines.forEach { l ->
                    importedList.add(
                        ClipEntity(
                            title = "",
                            text = l,
                            category = "Imported",
                            contentType = detectContentType(l),
                            isPinned = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        if (importedList.isNotEmpty()) {
            repository.insertClips(importedList)
        }
        return importedList.size
    }

    private fun detectContentType(text: String): String {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("http://", true) || 
            trimmed.startsWith("https://", true) || 
            trimmed.startsWith("www.", true) -> "URL"
            
            trimmed.contains("{") && trimmed.contains("}") || 
            trimmed.contains("fun ") || 
            trimmed.contains("class ") || 
            trimmed.contains("val ") || 
            trimmed.contains("var ") || 
            trimmed.contains("import ") || 
            trimmed.contains("def ") -> "CODE"
            
            else -> "TEXT"
        }
    }
}
