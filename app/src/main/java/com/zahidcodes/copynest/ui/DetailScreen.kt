package com.zahidcodes.copynest.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DetailScreen(
    clipId: Long,
    viewModel: ClipboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val clip = clips.find { it.id == clipId }

    val isDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
    }

    // HomeScreen signature color palette
    val screenBgColor = if (isDark) Color(0xFF090D16) else Color(0xFFF6F8FC)
    val navyTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0A1D4B)
    val pillUnselectedBg = if (isDark) Color(0xFF1E293B) else Color(0xFFEBF0F6)
    val pillUnselectedText = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val pillSelectedBg = if (isDark) Color(0xFF2563EB) else Color(0xFF0048B5)
    val cardBgColor = if (isDark) Color(0xFF131B2E) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF26334D) else Color(0xFFEDF2F7)
    val bodyTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
    val timestampColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    // Light Red Cancel Button palette
    val cancelBtnBg = if (isDark) Color(0xFF3F1D24) else Color(0xFFFEE2E2)
    val cancelBtnBorder = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFECACA)
    val cancelBtnText = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)

    var editedTitle by remember { mutableStateOf(clip?.title ?: "") }
    var editedText by remember { mutableStateOf(clip?.text ?: "") }
    var editedCategory by remember {
        mutableStateOf(
            clip?.category?.takeIf { it.isNotBlank() && !it.equals("General", ignoreCase = true) }
                ?: clip?.let { viewModel.detectCategoryForText(it.text) }
                ?: "Text"
        )
    }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isTitleFocused by remember { mutableStateOf(false) }
    var isContentFocused by remember { mutableStateOf(false) }
    val contentFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val categories = listOf("Text", "Links", "Code", "Study", "Work", "Important")

    LaunchedEffect(clip?.id) {
        if (clip != null) {
            editedTitle = clip.title
            editedText = clip.text
            editedCategory = clip.category.takeIf { it.isNotBlank() && !it.equals("General", ignoreCase = true) }
                ?: viewModel.detectCategoryForText(clip.text)
        }
    }

    if (clip == null) {
        Scaffold(
            containerColor = screenBgColor,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(screenBgColor)
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = pillUnselectedBg,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = navyTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    CopyNestHeaderBrand(navyColor = navyTextColor, compact = true)
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = pillSelectedBg,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Clip not found or deleted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = bodyTextColor
                        )
                    }
                }
            }
        }
        return
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(clip.timestamp) { dateFormat.format(Date(clip.timestamp)) }
    val relativeTimeText = remember(clip.timestamp) { formatRelativeTimestamp(clip.timestamp) }
    val wordCount = remember(editedText) {
        if (editedText.isBlank()) 0 else editedText.trim().split("\\s+".toRegex()).size
    }
    val charCount = editedText.length
    val lineCount = remember(editedText) {
        if (editedText.isEmpty()) 1 else editedText.count { it == '\n' } + 1
    }

    var isSaving by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val saveChangesAction = {
        if (!isSaving && editedText.isNotBlank()) {
            isSaving = true
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.updateClip(clip, editedTitle, editedText, editedCategory)
            Toast.makeText(context, "Note updated successfully", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val currentCategorySpec = remember(editedCategory, isDark) {
        getCategoryVisualSpec(editedCategory, isDark)
    }
    val isCodeCategory = editedCategory.equals("Code", ignoreCase = true) ||
        clip.contentType.equals("CODE", ignoreCase = true)
    val isLinkCategory = editedCategory.equals("Links", ignoreCase = true) ||
        clip.contentType.equals("URL", ignoreCase = true)

    Scaffold(
        containerColor = screenBgColor,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBgColor)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fixed Top Brand Header Row matching HomeScreen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = pillUnselectedBg,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = navyTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    CopyNestHeaderBrand(
                        navyColor = navyTextColor,
                        compact = true
                    )
                }

                // Right Top Actions: Pin, Copy, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        onClick = { viewModel.togglePin(clip) },
                        shape = CircleShape,
                        color = if (clip.isPinned) {
                            if (isDark) Color(0xFF3B2F14) else Color(0xFFFEF5DC)
                        } else {
                            pillUnselectedBg
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("detail_pin_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin",
                                tint = if (clip.isPinned) Color(0xFFF59E0B) else navyTextColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(if (clip.isPinned) 35f else 0f)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            viewModel.markInternalCopy(editedText.ifBlank { clip.text })
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("CopyNest", editedText.ifBlank { clip.text })
                            clipboard.setPrimaryClip(clipData)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        color = pillUnselectedBg,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("detail_copy_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = navyTextColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { showDeleteConfirm = true },
                        shape = CircleShape,
                        color = cancelBtnBg,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("detail_delete_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete",
                                tint = cancelBtnText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Section Heading Row ("Update note" + live word/char counter pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Update note",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.4).sp
                    ),
                    color = navyTextColor
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = pillUnselectedBg
                ) {
                    Text(
                        text = "$wordCount words • $charCount chars",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = pillUnselectedText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Compact Pill Title Input + Category Dropdown List Trigger (with Category Logo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBgColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = cardBorderColor
                    ),
                    shadowElevation = if (isDark) 0.dp else 1.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    BasicTextField(
                        value = editedTitle,
                        onValueChange = {
                            editedTitle = it
                            if (editedCategory.isBlank()) {
                                editedCategory = "Text"
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = navyTextColor
                        ),
                        cursorBrush = SolidColor(pillSelectedBg),
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { isTitleFocused = it.isFocused }
                            .testTag("edit_clip_title_input"),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Title Icon",
                                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF3E4E68),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (editedTitle.isEmpty()) {
                                        Text(
                                            text = "Add note title",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF5A6B82),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                                if (editedTitle.isNotEmpty()) {
                                    IconButton(
                                        onClick = { editedTitle = "" },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear title",
                                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF5A6B82),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // Category Dropdown Selector with Logo & Label (No separate category row above body)
                Box {
                    Surface(
                        onClick = { categoryMenuExpanded = !categoryMenuExpanded },
                        shape = RoundedCornerShape(20.dp),
                        color = cardBgColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (categoryMenuExpanded) pillSelectedBg else cardBorderColor
                        ),
                        shadowElevation = if (isDark) 0.dp else 1.dp,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("edit_category_dropdown_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = currentCategorySpec.boxBg,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = currentCategorySpec.icon,
                                        contentDescription = null,
                                        tint = currentCategorySpec.iconTint,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .rotate(currentCategorySpec.rotateDegrees)
                                    )
                                }
                            }
                            Text(
                                text = editedCategory,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = navyTextColor,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = if (categoryMenuExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = "Select Category",
                                tint = pillUnselectedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        modifier = Modifier
                            .width(185.dp)
                            .background(cardBgColor)
                    ) {
                        categories.forEach { cat ->
                            val spec = getCategoryVisualSpec(cat, isDark)
                            val isSelected = editedCategory.equals(cat, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) pillSelectedBg else navyTextColor
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = spec.boxBg,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = spec.icon,
                                                contentDescription = cat,
                                                tint = spec.iconTint,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .rotate(spec.rotateDegrees)
                                            )
                                        }
                                    }
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = pillSelectedBg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    editedCategory = cat
                                    categoryMenuExpanded = false
                                },
                                modifier = Modifier.testTag("edit_category_chip_${cat.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Dynamic Content-Aware Note Editor Card (grows naturally with text & caps above keyboard so buttons stay visible)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val maxEditorHeight = (maxHeight - 140.dp).coerceAtLeast(64.dp)
                val minEditorHeight = 110.dp.coerceAtMost(maxEditorHeight)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardBgColor,
                    shadowElevation = if (isDark) 0.dp else 1.8.dp,
                    border = BorderStroke(1.dp, cardBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(bottom = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Card Header Row: Category Logo Box + Live Title + Pinned Indicator + Paste/Clear Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = currentCategorySpec.boxBg,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = currentCategorySpec.icon,
                                            contentDescription = editedCategory,
                                            tint = currentCategorySpec.iconTint,
                                            modifier = Modifier
                                                .size(19.dp)
                                                .rotate(currentCategorySpec.rotateDegrees)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = editedTitle.ifBlank {
                                                when {
                                                    isCodeCategory -> "Code Snippet"
                                                    isLinkCategory -> "Website Link"
                                                    else -> "Saved Clip"
                                                }
                                            },
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.5.sp,
                                                lineHeight = 18.sp
                                            ),
                                            color = navyTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (clip.isPinned) {
                                            Spacer(modifier = Modifier.width(5.dp))
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

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = timestampColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "$relativeTimeText ($formattedDate)",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp
                                            ),
                                            color = timestampColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editedText.isNotEmpty()) {
                                    Surface(
                                        onClick = { editedText = "" },
                                        shape = RoundedCornerShape(50),
                                        color = cancelBtnBg,
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = cancelBtnText,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Clear",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = cancelBtnText
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = {
                                        try {
                                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipData = clipboardManager?.primaryClip
                                            if (clipData != null && clipData.itemCount > 0) {
                                                val item = clipData.getItemAt(0)
                                                val text = item.text?.toString() ?: item.coerceToText(context)?.toString()
                                                if (!text.isNullOrBlank()) {
                                                    editedText = if (editedText.isBlank()) text else "$editedText\n$text"
                                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (_: Exception) {
                                            // Ignore
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = pillUnselectedBg,
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = pillSelectedBg,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Paste",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = pillSelectedBg
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = cardBorderColor.copy(alpha = 0.75f)
                        )

                        // Dynamic Content-Aware Body Editor Area (expands naturally with content up to maxEditorHeight, then scrolls internally)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .background(
                                    color = if (isCodeCategory) {
                                        if (isDark) Color(0xFF152033) else Color(0xFFF3F6FA)
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(
                                    horizontal = if (isCodeCategory) 8.dp else 2.dp,
                                    vertical = if (isCodeCategory) 6.dp else 2.dp
                                )
                        ) {
                            BasicTextField(
                                value = editedText,
                                onValueChange = { editedText = it },
                                minLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = if (isCodeCategory || isLinkCategory) {
                                        KeyboardCapitalization.None
                                    } else {
                                        KeyboardCapitalization.Sentences
                                    },
                                    imeAction = ImeAction.Default
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = if (isCodeCategory) FontFamily.Monospace else FontFamily.Default,
                                    fontSize = if (isCodeCategory) 13.sp else 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (isCodeCategory) {
                                        if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                                    } else {
                                        navyTextColor
                                    }
                                ),
                                cursorBrush = SolidColor(pillSelectedBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = minEditorHeight, max = maxEditorHeight)
                                    .focusRequester(contentFocusRequester)
                                    .onFocusChanged { isContentFocused = it.isFocused }
                                    .testTag("edit_clip_text_input"),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minEditorHeight),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        if (editedText.isEmpty()) {
                                            Text(
                                                text = "Paste or type your note details here...",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = if (isCodeCategory) FontFamily.Monospace else FontFamily.Default,
                                                    fontSize = 13.5.sp,
                                                    lineHeight = 20.sp
                                                ),
                                                color = bodyTextColor.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$lineCount ${if (lineCount == 1) "line" else "lines"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = timestampColor
                            )
                        }

                        HorizontalDivider(
                            thickness = 0.8.dp,
                            color = cardBorderColor.copy(alpha = 0.75f)
                        )

                        // Action Buttons Directly Below Content Textbox (Light Red Cancel + Royal Blue Save Changes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Light Red Compact Cancel Button
                            Surface(
                                onClick = onBack,
                                shape = RoundedCornerShape(8.dp),
                                color = cancelBtnBg,
                                border = BorderStroke(1.dp, cancelBtnBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("cancel_edit_button")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = cancelBtnText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Cancel",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = cancelBtnText
                                    )
                                }
                            }

                            // Royal Blue Compact Save Changes Button
                            Button(
                                onClick = saveChangesAction,
                                enabled = editedText.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = pillSelectedBg,
                                    contentColor = Color.White,
                                    disabledContainerColor = pillUnselectedBg,
                                    disabledContentColor = pillUnselectedText.copy(alpha = 0.5f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 1.dp,
                                    pressedElevation = 3.dp,
                                    disabledElevation = 0.dp
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(34.dp)
                                    .testTag("save_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Save Changes",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Clip") },
            text = { Text("Are you sure you want to delete this saved clip? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteClip(clip.id)
                        showDeleteConfirm = false
                        onBack()
                        Toast.makeText(context, "Clip deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
