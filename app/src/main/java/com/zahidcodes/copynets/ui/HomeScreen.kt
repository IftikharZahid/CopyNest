package com.zahidcodes.copynets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zahidcodes.copynets.R
import com.zahidcodes.copynets.data.ClipEntity
import com.zahidcodes.copynets.data.DetectedLink
import com.zahidcodes.copynets.data.LinkMetadata
import com.zahidcodes.copynets.data.LinkMetadataManager
import java.text.SimpleDateFormat
import java.util.*

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

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedClipIds by remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(Unit) {
        viewModel.onHomeScreenShown(context)
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

    val pinnedClips = remember(clips) { clips.filter { it.isPinned } }
    val unpinnedClips = remember(clips) { clips.filter { !it.isPinned } }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                val allSelected = clips.isNotEmpty() && selectedClipIds.size == clips.size
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedClipIds = emptySet()
                            },
                            modifier = Modifier.testTag("cancel_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit Selection Mode"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "${selectedClipIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                modifier = Modifier.testTag("selection_count_text")
                            )
                            Text(
                                text = if (selectedClipIds.isEmpty()) {
                                    "Tap clips to select"
                                } else {
                                    "Tap delete to remove selected clips"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedClipIds = if (allSelected) {
                                    emptySet()
                                } else {
                                    clips.map { it.id }.toSet()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (allSelected) "None" else "All",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        IconButton(
                            onClick = deleteSelectedClips,
                            enabled = selectedClipIds.isNotEmpty(),
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected Clips",
                                tint = if (selectedClipIds.isNotEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0xFF051E58),
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFF0D3FA8),
                                                    Color(0xFF031236)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_fg_art),
                                        contentDescription = "CopyNest Logo",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.dp)
                                            .testTag("home_app_logo")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CopyNest",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                )
                                Text(
                                    text = "Smart Clipboard & Notes",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    actions = {
                        if (clips.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = true
                                    selectedClipIds = emptySet()
                                },
                                modifier = Modifier.testTag("multi_select_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checklist,
                                    contentDescription = "Select Clips"
                                )
                            }
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (isSelectionMode) {
                if (selectedClipIds.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = deleteSelectedClips,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected"
                            )
                        },
                        text = {
                            Text(
                                text = "Delete (${selectedClipIds.size})",
                                style = MaterialTheme.typography.labelLarge.copy(
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_clip_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Clip")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Ultra-Compact Search Box & Paste Button Row (34.dp height, zero text clipping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_input"),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search your clips and notes...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // Ultra-Compact Paste Button
                Surface(
                    onClick = {
                        viewModel.checkClipboardAndSave(context, force = true) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("paste_clipboard_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Paste",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Full-Width Adaptive Filter Buttons Row (Adjusts proportionally to screen width)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    Triple("ALL", "All Clips", Icons.Outlined.List),
                    Triple("PINNED", "Pinned", Icons.Outlined.PushPin),
                    Triple("URL", "Links", Icons.Outlined.Link),
                    Triple("CODE", "Code", Icons.Outlined.Code),
                    Triple("TEXT", "Text", Icons.Outlined.TextSnippet)
                )
                filters.forEach { (id, label, icon) ->
                    val isSelected = selectedFilter == id
                    val chipWeight = when (id) {
                        "ALL" -> 1.25f
                        "PINNED" -> 1.05f
                        else -> 0.95f
                    }
                    Surface(
                        onClick = { viewModel.setFilter(id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .weight(chipWeight)
                            .height(28.dp)
                            .testTag("filter_chip_${id.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(11.5.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Smooth transition between Professional Loader, Empty State, and Clips List
            val contentState = when {
                isLoading -> "LOADING"
                clips.isEmpty() -> "EMPTY"
                else -> "CONTENT"
            }

            Crossfade(
                targetState = contentState,
                animationSpec = tween(durationMillis = 220),
                label = "home_content_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        ProfessionalClipsLoader()
                    }
                    "EMPTY" -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPasteOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No saved clips yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Copy text from any app, tap Paste above, or tap + to add a new note.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            if (pinnedClips.isNotEmpty()) {
                                items(pinnedClips, key = { "pinned_${it.id}" }) { clip ->
                                    val isClipSelected = selectedClipIds.contains(clip.id)
                                    ClipCard(
                                        clip = clip,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isClipSelected,
                                        linkMetadataCache = linkMetadataCache,
                                        onRequestLinkMetadata = { url -> viewModel.requestLinkMetadata(url) },
                                        onOpenLink = { url -> LinkMetadataManager.openUrl(context, url) },
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
                                        onCopy = {
                                            viewModel.markInternalCopy(clip.text)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clipData = ClipData.newPlainText("CopyNest", clip.text)
                                            clipboard.setPrimaryClip(clipData)
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onTogglePin = { viewModel.togglePin(clip) },
                                        onDelete = { viewModel.deleteClip(clip.id) }
                                    )
                                }

                                if (unpinnedClips.isNotEmpty()) {
                                    item(key = "pinned_divider") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                            Text(
                                                text = "RECENT CLIPS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                thickness = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                }
                            }

                            items(unpinnedClips, key = { "unpinned_${it.id}" }) { clip ->
                                val isClipSelected = selectedClipIds.contains(clip.id)
                                ClipCard(
                                    clip = clip,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isClipSelected,
                                    linkMetadataCache = linkMetadataCache,
                                    onRequestLinkMetadata = { url -> viewModel.requestLinkMetadata(url) },
                                    onOpenLink = { url -> LinkMetadataManager.openUrl(context, url) },
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
                                    onCopy = {
                                        viewModel.markInternalCopy(clip.text)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = ClipData.newPlainText("CopyNest", clip.text)
                                        clipboard.setPrimaryClip(clipData)
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onTogglePin = { viewModel.togglePin(clip) },
                                    onDelete = { viewModel.deleteClip(clip.id) }
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
fun ProfessionalClipsLoader() {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 6.dp)
            .testTag("home_professional_loader"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Branded Loading Status Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(34.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        strokeWidth = 2.6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loading saved records...",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "Syncing your clipboard history & notes",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        // Compact Shimmer Skeleton Cards matching ClipCard layout
        repeat(4) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .width(if (index % 2 == 0) 110.dp else 85.dp)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .width(42.dp)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha * 0.6f)
                                    )
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha * 0.7f)
                                        )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.92f else 0.84f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha * 0.85f)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.65f else 0.74f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = pulseAlpha * 0.65f)
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
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    linkMetadataCache: Map<String, LinkMetadata> = emptyMap(),
    onRequestLinkMetadata: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(clip.timestamp) { dateFormat.format(Date(clip.timestamp)) }
    val detectedLinks = remember(clip.text) { LinkMetadataManager.extractLinks(clip.text) }
    val primaryLink = detectedLinks.firstOrNull()
    val primaryMetadata = primaryLink?.let { linkMetadataCache[it.normalizedUrl] }

    LaunchedEffect(primaryLink?.normalizedUrl) {
        if (primaryLink != null) {
            onRequestLinkMetadata(primaryLink.normalizedUrl)
        }
    }

    val effectiveContentType = remember(clip.contentType, primaryLink) {
        if (primaryLink != null && clip.contentType != "CODE") "URL" else clip.contentType
    }

    val displayCategory = remember(clip.category, effectiveContentType) {
        if (clip.category.isBlank() || clip.category.equals("General", ignoreCase = true)) {
            when (effectiveContentType.uppercase()) {
                "URL" -> "Links"
                "CODE" -> "Code"
                else -> "Text"
            }
        } else {
            clip.category
        }
    }

    val headerTitle = remember(clip.title, clip.text, primaryMetadata?.title, primaryLink) {
        when {
            clip.title.isNotBlank() -> clip.title
            primaryMetadata != null && primaryMetadata.title.isNotBlank() -> primaryMetadata.title
            primaryLink != null -> LinkMetadataManager.formatFallbackSiteName(primaryLink.domain)
            else -> clip.text.take(26)
        }
    }

    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedContent: AnnotatedString = remember(clip.text, detectedLinks, linkColor) {
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
                                color = linkColor,
                                fontWeight = FontWeight.Medium,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("clip_card_" + clip.id),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                clip.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Single Top Line: Selection Indicator (when active), Type Icon, Title, Category Pill, Timestamp, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("clip_select_indicator_" + clip.id)
                        )
                    }

                    // Type Icon
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = when (effectiveContentType) {
                            "URL" -> MaterialTheme.colorScheme.primaryContainer
                            "CODE" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (effectiveContentType) {
                                    "URL" -> Icons.Outlined.Link
                                    "CODE" -> Icons.Outlined.Code
                                    else -> Icons.Outlined.TextSnippet
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = when (effectiveContentType) {
                                    "URL" -> MaterialTheme.colorScheme.primary
                                    "CODE" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }
                    }

                    // Title (uses fetched page title when clip has no custom title)
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Auto-Assigned Category Pill (Text, Links, Code, or Custom)
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = when (displayCategory.uppercase()) {
                            "LINKS" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            "CODE" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        }
                    ) {
                        Text(
                            text = displayCategory,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp
                            ),
                            color = when (displayCategory.uppercase()) {
                                "LINKS" -> MaterialTheme.colorScheme.primary
                                "CODE" -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Timestamp
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }

                // Action Buttons in the same line (hidden during multi-select mode so taps toggle selection)
                if (!isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        if (primaryLink != null) {
                            IconButton(
                                onClick = { onOpenLink(primaryLink.normalizedUrl) },
                                modifier = Modifier
                                    .size(22.dp)
                                    .testTag("open_link_button_" + clip.id)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInNew,
                                    contentDescription = "Open Link",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                modifier = Modifier.size(13.dp),
                                tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Below the single top line: Content with clickable URLs
            Spacer(modifier = Modifier.height(3.dp))
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = annotatedContent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (effectiveContentType == "CODE") FontFamily.Monospace else FontFamily.Default,
                    fontSize = 12.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onTextLayout = { textLayoutResult = it },
                modifier = if (detectedLinks.isNotEmpty() && !isSelectionMode) {
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(annotatedContent, isSelectionMode) {
                            detectTapGestures(
                                onTap = { pos ->
                                    val layout = textLayoutResult
                                    if (layout != null) {
                                        val offset = layout.getOffsetForPosition(pos)
                                        val urlAnnotation = annotatedContent
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

            // Rich Link Metadata Preview Card when a link is detected in the clip
            if (primaryLink != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinkMetadataPreviewCard(
                    clipId = clip.id,
                    link = primaryLink,
                    metadata = primaryMetadata,
                    onClick = {
                        if (isSelectionMode) {
                            onClick()
                        } else {
                            onOpenLink(primaryLink.normalizedUrl)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun LinkMetadataPreviewCard(
    clipId: Long,
    link: DetectedLink,
    metadata: LinkMetadata?,
    onClick: () -> Unit
) {
    val siteName = remember(link.domain, metadata?.siteName) {
        metadata?.siteName?.ifBlank { null } ?: LinkMetadataManager.formatFallbackSiteName(link.domain)
    }
    val pageTitle = remember(link.normalizedUrl, link.domain, metadata?.title) {
        metadata?.title?.ifBlank { null } ?: LinkMetadataManager.formatFallbackTitle(link.normalizedUrl, link.domain)
    }
    val pageDescription = remember(link.normalizedUrl, metadata?.description) {
        metadata?.description?.takeIf {
            it.isNotBlank() && !it.equals(link.normalizedUrl, ignoreCase = true)
        }
    }
    val isLoading = metadata == null || metadata.isLoading

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        border = BorderStroke(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("link_preview_card_$clipId")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Favicon / Site Emblem Container
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val favicon = metadata?.favicon
                    if (favicon != null) {
                        Image(
                            bitmap = favicon,
                            contentDescription = siteName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.6.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = siteName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Link Metadata Details (Site Name + Domain, Page Title, Description)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = siteName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (isLoading) "${link.domain} (loading...)" else link.domain,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!pageDescription.isNullOrBlank()) {
                    Text(
                        text = pageDescription,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Open Link Action Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "Open link in browser",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

