package com.zahidcodes.copynest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zahidcodes.copynest.data.ClipEntity
import com.zahidcodes.copynest.data.LinkMetadata
import com.zahidcodes.copynest.data.LinkMetadataManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ClipboardViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val linkMetadataCache by viewModel.linkMetadataCache.collectAsState()
    val scrollToTopVersion by viewModel.scrollToTopVersion.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val isDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    val screenBgColor = if (isDark) Color(0xFF090D16) else Color(0xFFF6F8FC)
    val navyTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0A1D4B)
    val pillUnselectedBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEBF0F6)
    val pillUnselectedText = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val pillSelectedBg = if (isDark) Color(0xFF2563EB) else Color(0xFF0048B5)
    val fabBlueColor = if (isDark) Color(0xFF2563EB) else Color(0xFF0D5BD9)

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedClipIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        viewModel.onHomeScreenShown(context)
        listState.scrollToItem(0, 0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    listState.scrollToItem(0, 0)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(clips) {
        val validIds = clips.map { it.id }.toSet()
        val retained = selectedClipIds.intersect(validIds)
        if (retained.size != selectedClipIds.size) {
            selectedClipIds = retained
        }
        if (clips.isEmpty() && isSelectionMode) {
            isSelectionMode = false
            selectedClipIds = emptySet()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedClipIds = emptySet()
    }

    val toggleClipSelection: (Long) -> Unit = { clipId ->
        selectedClipIds = if (selectedClipIds.contains(clipId)) {
            selectedClipIds - clipId
        } else {
            selectedClipIds + clipId
        }
    }

    val deleteSelectedClips: () -> Unit = {
        val count = selectedClipIds.size
        if (count > 0) {
            viewModel.deleteClips(selectedClipIds)
            Toast.makeText(
                context,
                if (count == 1) "Deleted 1 clip" else "Deleted $count clips",
                Toast.LENGTH_SHORT
            ).show()
            selectedClipIds = emptySet()
            isSelectionMode = false
        }
    }

    val displayClips = remember(clips, selectedFilter) {
        if (selectedFilter == "RECENT") {
            clips.sortedByDescending { it.timestamp }
        } else {
            val pinned = clips.filter { it.isPinned }
            val unpinned = clips.filter { !it.isPinned }
            pinned + unpinned
        }
    }

    val topClipSignature = remember(displayClips) {
        displayClips.firstOrNull()?.let { "${it.id}_${it.timestamp}" } ?: ""
    }

    LaunchedEffect(scrollToTopVersion, topClipSignature, selectedFilter, isLoading) {
        if (!isLoading && displayClips.isNotEmpty()) {
            listState.scrollToItem(0, 0)
            withFrameNanos { }
            listState.scrollToItem(0, 0)
        }
    }

    // No bottomBar per user requirement; compact layout to display maximum records
    Scaffold(
        containerColor = screenBgColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (isSelectionMode) {
                if (selectedClipIds.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = deleteSelectedClips,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = "Delete (${selectedClipIds.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.testTag("delete_selected_fab")
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    shape = CircleShape,
                    containerColor = fabBlueColor,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 5.dp,
                        pressedElevation = 8.dp
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("add_clip_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Clip",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBgColor)
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Compact Top Brand Header Row (or Multi-Select Action Row)
            if (isSelectionMode) {
                val allSelected = displayClips.isNotEmpty() && selectedClipIds.size == displayClips.size
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillUnselectedBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedClipIds = emptySet()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("cancel_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit Selection Mode",
                                    tint = navyTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${selectedClipIds.size} Selected",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = navyTextColor,
                                modifier = Modifier.testTag("selection_count_text")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    selectedClipIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        displayClips.map { it.id }.toSet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("select_all_button")
                            ) {
                                Text(
                                    text = if (allSelected) "None" else "Select All",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    ),
                                    color = pillSelectedBg
                                )
                            }
                            IconButton(
                                onClick = deleteSelectedClips,
                                enabled = selectedClipIds.isNotEmpty(),
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("delete_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected Clips",
                                    tint = if (selectedClipIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        navyTextColor.copy(alpha = 0.35f)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // CopyNest Wordmark with Wavy Golden Underline under "Nest"
                    CopyNestHeaderBrand(
                        navyColor = navyTextColor,
                        compact = true,
                        modifier = Modifier.testTag("home_app_logo")
                    )

                    // Outlined Settings Icon on the top right
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = navyTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact "Your clips" Heading with record count badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Your clips",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.4).sp
                    ),
                    color = navyTextColor,
                    modifier = Modifier.testTag("your_clips_heading")
                )

                if (displayClips.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillUnselectedBg
                    ) {
                        Text(
                            text = "${displayClips.size} ${if (displayClips.size == 1) "clip" else "clips"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = pillUnselectedText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Compact Rounded Pill Search Bar ("Search your clips")
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = pillUnselectedBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        color = navyTextColor
                    ),
                    cursorBrush = SolidColor(pillSelectedBg),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_input"),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF3E4E68),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search your clips",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF5A6B82),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF5A6B82),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Compact 4 Filter Pills Row: All, Pinned, Links, Recent
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "All",
                    "PINNED" to "Pinned",
                    "URL" to "Links",
                    "RECENT" to "Recent"
                )
                filters.forEach { (id, label) ->
                    val isSelected = selectedFilter == id
                    Surface(
                        onClick = { viewModel.setFilter(id) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) pillSelectedBg else pillUnselectedBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(31.dp)
                            .testTag("filter_chip_${id.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isSelected) Color.White else pillUnselectedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val contentState = when {
                isLoading -> "LOADING"
                displayClips.isEmpty() -> "EMPTY"
                else -> "CONTENT"
            }

            Crossfade(
                targetState = contentState,
                animationSpec = tween(durationMillis = 180),
                label = "home_content_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        ProfessionalClipsLoader(isDark = isDark)
                    }
                    "EMPTY" -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPasteOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "No matching clips found"
                                    } else {
                                        "No clips in this view yet"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = navyTextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button to add a new clip or copy text from any app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.checkClipboardAndSave(context, force = true) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("paste_clipboard_button"),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Paste from Clipboard",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("clips_lazy_column"),
                            contentPadding = PaddingValues(top = 1.dp, bottom = 76.dp)
                        ) {
                            items(displayClips, key = { it.id }) { clip ->
                                val isClipSelected = selectedClipIds.contains(clip.id)
                                ClipCard(
                                    clip = clip,
                                    isDark = isDark,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isClipSelected,
                                    linkMetadataCache = linkMetadataCache,
                                    onRequestLinkMetadata = { url ->
                                        viewModel.requestLinkMetadata(url, clip.id)
                                    },
                                    onOpenLink = { url ->
                                        LinkMetadataManager.openUrl(context, url)
                                    },
                                    onClick = {
                                        if (isSelectionMode) {
                                            toggleClipSelection(clip.id)
                                        } else {
                                            onNavigateToDetail(clip.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedClipIds = setOf(clip.id)
                                        } else {
                                            toggleClipSelection(clip.id)
                                        }
                                    },
                                    onSelectClip = {
                                        isSelectionMode = true
                                        selectedClipIds = selectedClipIds + clip.id
                                    },
                                    onCopy = {
                                        viewModel.markInternalCopy(clip.text)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = ClipData.newPlainText("CopyNest", clip.text)
                                        clipboard.setPrimaryClip(clipData)
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onShare = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    clip.title.ifBlank { "Shared from CopyNest" }
                                                )
                                                putExtra(Intent.EXTRA_TEXT, clip.text)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Share clip")
                                            )
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Unable to share clip", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onTogglePin = { viewModel.togglePin(clip) },
                                    onDelete = {
                                        viewModel.deleteClip(clip.id)
                                        Toast.makeText(context, "Clip deleted", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CopyNestHeaderBrand(
    navyColor: Color,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val amberGold = Color(0xFFFFA000)
    val waveGold = Color(0xFFF59E0B)
    val fontSize = if (compact) 21.sp else 25.sp
    val lineHeight = if (compact) 23.sp else 27.sp
    val spacerWidth = if (compact) 49.dp else 59.dp
    val waveWidth = if (compact) 45.dp else 54.dp
    val waveHeight = if (compact) 4.5.dp else 6.dp

    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(
                            color = navyColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) {
                        append("Copy")
                    }
                    withStyle(
                        style = SpanStyle(
                            color = amberGold,
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) {
                        append("Nest")
                    }
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = fontSize,
                    letterSpacing = (-0.5).sp,
                    lineHeight = lineHeight
                )
            )

            // Wavy golden underline aligned beneath "Nest"
            Row {
                Spacer(modifier = Modifier.width(spacerWidth))
                Canvas(
                    modifier = Modifier
                        .width(waveWidth)
                        .height(waveHeight)
                ) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.72f)
                        quadraticTo(
                            w * 0.25f,
                            h * 0.05f,
                            w * 0.50f,
                            h * 0.65f
                        )
                        quadraticTo(
                            w * 0.76f,
                            h * 0.05f,
                            w,
                            h * 0.58f
                        )
                    }
                    drawPath(
                        path = path,
                        color = waveGold,
                        style = Stroke(
                            width = 2.1.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipCard(
    clip: ClipEntity,
    isDark: Boolean = false,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    linkMetadataCache: Map<String, LinkMetadata> = emptyMap(),
    onRequestLinkMetadata: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSelectClip: () -> Unit = {},
    onCopy: () -> Unit,
    onShare: () -> Unit = {},
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val relativeTimeText = remember(clip.timestamp) {
        formatRelativeTimestamp(clip.timestamp)
    }
    val detectedLinks = remember(clip.text) { LinkMetadataManager.extractLinks(clip.text) }
    val primaryLink = detectedLinks.firstOrNull()
    val primaryMetadata = remember(
        primaryLink?.normalizedUrl,
        linkMetadataCache,
        clip.linkMetadataLoaded,
        clip.linkUrl,
        clip.linkDomain,
        clip.linkSiteName,
        clip.linkTitle,
        clip.linkDescription,
        clip.linkFaviconBase64
    ) {
        val url = primaryLink?.normalizedUrl ?: return@remember null
        val cachedInMemory = linkMetadataCache[url]
        when {
            cachedInMemory != null && cachedInMemory.isLoaded -> cachedInMemory
            clip.linkMetadataLoaded -> LinkMetadataManager.fromClipEntity(clip, url) ?: cachedInMemory
            else -> cachedInMemory
        }
    }

    LaunchedEffect(primaryLink?.normalizedUrl, clip.id, clip.linkMetadataLoaded) {
        if (primaryLink != null && !clip.linkMetadataLoaded && primaryMetadata?.isLoaded != true) {
            onRequestLinkMetadata(primaryLink.normalizedUrl)
        }
    }

    val effectiveContentType = remember(clip.contentType, primaryLink, clip.category) {
        when {
            clip.contentType.equals("CODE", ignoreCase = true) ||
                clip.category.equals("Code", ignoreCase = true) -> "CODE"
            primaryLink != null ||
                clip.contentType.equals("URL", ignoreCase = true) ||
                clip.category.equals("Links", ignoreCase = true) -> "URL"
            else -> "TEXT"
        }
    }

    val isAmberStudyStyle = remember(clip.category, clip.title, clip.isPinned, effectiveContentType) {
        effectiveContentType == "TEXT" && (
            clip.category.equals("Study", ignoreCase = true) ||
                clip.category.equals("Important", ignoreCase = true) ||
                clip.title.contains("Study", ignoreCase = true) ||
                clip.isPinned
            )
    }

    val headerTitle = remember(clip.title, clip.text, primaryMetadata?.title, primaryLink, effectiveContentType) {
        when {
            clip.title.isNotBlank() -> clip.title
            effectiveContentType == "URL" -> {
                primaryMetadata?.title?.takeIf { it.isNotBlank() } ?: "Website Link"
            }
            effectiveContentType == "CODE" -> "Code Snippet"
            else -> {
                clip.text
                    .lineSequence()
                    .firstOrNull()
                    ?.removePrefix("•")
                    ?.removePrefix("-")
                    ?.removePrefix("*")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.take(32)
                    ?: "Saved Clip"
            }
        }
    }

    val cardBgColor = when {
        isSelected -> if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)
        isDark -> Color(0xFF131B2E)
        else -> Color.White
    }
    val cardBorderColor = when {
        isSelected -> if (isDark) Color(0xFF3B82F6) else Color(0xFF0048B5)
        isDark -> Color(0xFF26334D)
        else -> Color(0xFFEDF2F7)
    }
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0A1D4B)
    val bodyTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
    val timestampColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val linkBlueColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1565D8)

    val annotatedContent: AnnotatedString = remember(clip.text, detectedLinks, linkBlueColor) {
        if (detectedLinks.isEmpty()) {
            AnnotatedString(clip.text)
        } else {
            buildAnnotatedString {
                append(clip.text)
                detectedLinks.forEach { link ->
                    val safeStart = link.startIndex.coerceIn(0, clip.text.length)
                    val safeEnd = link.endIndex.coerceIn(safeStart, clip.text.length)
                    if (safeStart < safeEnd) {
                        addStyle(
                            style = SpanStyle(
                                color = linkBlueColor,
                                fontWeight = FontWeight.Normal,
                                textDecoration = TextDecoration.Underline
                            ),
                            start = safeStart,
                            end = safeEnd
                        )
                        addStringAnnotation(
                            tag = "URL",
                            annotation = link.normalizedUrl,
                            start = safeStart,
                            end = safeEnd
                        )
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBgColor,
        shadowElevation = if (isDark) 0.dp else 1.8.dp,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = cardBorderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("clip_card_" + clip.id)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Compact Left Colored Rounded-Square Icon Box (38.dp)
            val iconBoxBg = when {
                effectiveContentType == "URL" -> if (isDark) Color(0xFF2E1B4B) else Color(0xFFF1E6FF)
                effectiveContentType == "CODE" -> if (isDark) Color(0xFF133829) else Color(0xFFE5F7ED)
                isAmberStudyStyle -> if (isDark) Color(0xFF3B2F14) else Color(0xFFFEF5DC)
                else -> if (isDark) Color(0xFF152C4A) else Color(0xFFE6F2FC)
            }
            val iconTint = when {
                effectiveContentType == "URL" -> if (isDark) Color(0xFFC084FC) else Color(0xFF6D28D9)
                effectiveContentType == "CODE" -> if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
                isAmberStudyStyle -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD98200)
                else -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconBoxBg,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) Color(0xFF0048B5) else iconTint,
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("clip_select_indicator_" + clip.id)
                        )
                    } else {
                        when (effectiveContentType) {
                            "URL" -> {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = "Link Clip",
                                    tint = iconTint,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(-45f)
                                )
                            }
                            "CODE" -> {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Code Snippet",
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = "Note Clip",
                                    tint = iconTint,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Content Column: Compact Header Row + Body Content + Timestamp Row
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Header Row: Bold Title + Pinned Amber Icon + 3-Dots More Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            lineHeight = 17.sp
                        ),
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isSelectionMode) {
                        if (clip.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onTogglePin)
                                    .testTag("pin_button_${clip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .rotate(35f)
                                )
                            }
                        }

                        Box {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { menuExpanded = true }
                                    .testTag("more_menu_button_${clip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More actions",
                                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onCopy()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (clip.isPinned) "Unpin" else "Pin") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onTogglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onClick()
                                    }
                                )
                                if (primaryLink != null) {
                                    DropdownMenuItem(
                                        text = { Text("Open Link") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.OpenInNew,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenLink(primaryLink.normalizedUrl)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSelectClip()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Compact Body Content Area: Code Box, Clickable URL, or Formatted Text / Bullet List
                when (effectiveContentType) {
                    "CODE" -> {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFEEF2F6),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = clip.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                ),
                                color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        val formattedBodyText = remember(annotatedContent, effectiveContentType) {
                            if (effectiveContentType == "TEXT") {
                                formatBulletLines(annotatedContent)
                            } else {
                                annotatedContent
                            }
                        }

                        Text(
                            text = formattedBodyText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 16.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = bodyTextColor,
                            onTextLayout = { textLayoutResult = it },
                            modifier = if (detectedLinks.isNotEmpty() && !isSelectionMode) {
                                Modifier
                                    .fillMaxWidth()
                                    .pointerInput(formattedBodyText, isSelectionMode) {
                                        detectTapGestures(
                                            onTap = { pos ->
                                                val layout = textLayoutResult
                                                if (layout != null) {
                                                    val offset = layout.getOffsetForPosition(pos)
                                                    val urlAnnotation = formattedBodyText
                                                        .getStringAnnotations(tag = "URL", start = offset, end = offset)
                                                        .firstOrNull()
                                                    if (urlAnnotation != null) {
                                                        onOpenLink(urlAnnotation.item)
                                                        return@detectTapGestures
                                                    }
                                                }
                                                onClick()
                                            },
                                            onLongPress = { onLongClick() }
                                        )
                                    }
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Compact Bottom Timestamp Row with Clock Icon + Relative Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = timestampColor,
                        modifier = Modifier.size(11.5.dp)
                    )
                    Text(
                        text = relativeTimeText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = timestampColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatBulletLines(input: AnnotatedString): AnnotatedString {
    val raw = input.text
    if (!raw.contains("• ") && !raw.contains("- ") && !raw.contains("* ")) {
        return input
    }
    val formatted = raw.lines().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("• ") -> "•  " + trimmed.removePrefix("• ").trimStart()
            trimmed.startsWith("- ") -> "•  " + trimmed.removePrefix("- ").trimStart()
            trimmed.startsWith("* ") -> "•  " + trimmed.removePrefix("* ").trimStart()
            else -> line
        }
    }
    return AnnotatedString(formatted)
}

internal fun formatRelativeTimestamp(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = (now - timestampMs).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    val hours = diffMs / (60L * 60_000L)
    val days = diffMs / (24L * 60L * 60_000L)

    return when {
        minutes < 1L -> "Just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60L -> "$minutes minutes ago"
        hours == 1L -> "1 hour ago"
        hours < 24L -> "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestampMs))
    }
}

@Composable
fun ProfessionalClipsLoader(isDark: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader_shimmer")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val cardColor = if (isDark) Color(0xFF131B2E) else Color.White
    val shimmerBase = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 2.dp)
            .testTag("home_professional_loader"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(6) { index ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cardColor,
                shadowElevation = if (isDark) 0.dp else 1.5.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF26334D) else Color(0xFFEDF2F7)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(shimmerBase.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (index % 2 == 0) 120.dp else 95.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBase.copy(alpha = pulseAlpha))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBase.copy(alpha = pulseAlpha * 0.8f))
                        )
                        Box(
                            modifier = Modifier
                                .width(75.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBase.copy(alpha = pulseAlpha * 0.65f))
                        )
                    }
                }
            }
        }
    }
}
