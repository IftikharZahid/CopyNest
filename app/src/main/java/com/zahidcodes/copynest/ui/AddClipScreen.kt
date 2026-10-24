package com.zahidcodes.copynest.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class CategoryVisualSpec(
    val name: String,
    val icon: ImageVector,
    val boxBg: Color,
    val iconTint: Color,
    val rotateDegrees: Float = 0f
)

internal fun getCategoryVisualSpec(category: String, isDark: Boolean): CategoryVisualSpec {
    return when (category.lowercase()) {
        "links", "url" -> CategoryVisualSpec(
            name = "Links",
            icon = Icons.Outlined.Link,
            boxBg = if (isDark) Color(0xFF2E1B4B) else Color(0xFFF1E6FF),
            iconTint = if (isDark) Color(0xFFC084FC) else Color(0xFF6D28D9),
            rotateDegrees = -45f
        )
        "code" -> CategoryVisualSpec(
            name = "Code",
            icon = Icons.Default.Code,
            boxBg = if (isDark) Color(0xFF133829) else Color(0xFFE5F7ED),
            iconTint = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
        )
        "study" -> CategoryVisualSpec(
            name = "Study",
            icon = Icons.Outlined.School,
            boxBg = if (isDark) Color(0xFF3B2F14) else Color(0xFFFEF5DC),
            iconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD98200)
        )
        "work" -> CategoryVisualSpec(
            name = "Work",
            icon = Icons.Outlined.WorkOutline,
            boxBg = if (isDark) Color(0xFF1E1B4B) else Color(0xFFE0E7FF),
            iconTint = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
        )
        "important" -> CategoryVisualSpec(
            name = "Important",
            icon = Icons.Outlined.StarOutline,
            boxBg = if (isDark) Color(0xFF3F2A14) else Color(0xFFFEF3C7),
            iconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        )
        else -> CategoryVisualSpec(
            name = category.ifBlank { "Text" },
            icon = Icons.Outlined.Description,
            boxBg = if (isDark) Color(0xFF152C4A) else Color(0xFFE6F2FC),
            iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
    }
}

@Composable
fun AddClipScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()

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

    var noteTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Text") }
    var hasManualCategory by remember { mutableStateOf(false) }
    var textContent by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var isTitleFocused by remember { mutableStateOf(false) }
    var isContentFocused by remember { mutableStateOf(false) }
    val contentFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val categories = listOf("Text", "Links", "Code", "Study", "Work", "Important")
    val wordCount = remember(textContent) {
        if (textContent.isBlank()) 0 else textContent.trim().split("\\s+".toRegex()).size
    }
    val charCount = textContent.length
    val lineCount = remember(textContent) {
        if (textContent.isEmpty()) 1 else textContent.count { it == '\n' } + 1
    }

    LaunchedEffect(textContent) {
        if (!hasManualCategory && textContent.isNotBlank()) {
            selectedCategory = viewModel.detectCategoryForText(textContent)
        }
    }

    val saveNoteAction = {
        if (textContent.isNotBlank()) {
            val finalCat = if (!hasManualCategory) {
                viewModel.detectCategoryForText(textContent)
            } else {
                selectedCategory.ifBlank { viewModel.detectCategoryForText(textContent) }
            }
            viewModel.saveManualClip(
                title = noteTitle.trim(),
                text = textContent.trim(),
                category = finalCat
            )
            Toast.makeText(context, "Note saved as $finalCat", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val currentCategorySpec = remember(selectedCategory, isDark) {
        getCategoryVisualSpec(selectedCategory, isDark)
    }
    val isCodeCategory = selectedCategory.equals("Code", ignoreCase = true)
    val isLinkCategory = selectedCategory.equals("Links", ignoreCase = true)

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

            // Section Heading ("Add new note")
            Text(
                text = "Add new note",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    letterSpacing = (-0.4).sp
                ),
                color = navyTextColor
            )

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
                        value = noteTitle,
                        onValueChange = {
                            noteTitle = it
                            if (selectedCategory.isBlank()) {
                                selectedCategory = "Text"
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = navyTextColor
                        ),
                        cursorBrush = SolidColor(pillSelectedBg),
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { isTitleFocused = it.isFocused }
                            .testTag("add_title_input"),
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
                                    if (noteTitle.isEmpty()) {
                                        Text(
                                            text = "Add note title (optional)",
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
                                if (noteTitle.isNotEmpty()) {
                                    IconButton(
                                        onClick = { noteTitle = "" },
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
                            .testTag("category_dropdown_button")
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
                                text = selectedCategory,
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
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
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
                                    selectedCategory = cat
                                    hasManualCategory = true
                                    categoryMenuExpanded = false
                                },
                                modifier = Modifier.testTag("category_chip_${cat.lowercase()}")
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
                        // Card Header Row: Category Logo Box + Live Title + Paste/Clear Actions
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
                                            contentDescription = selectedCategory,
                                            tint = currentCategorySpec.iconTint,
                                            modifier = Modifier
                                                .size(19.dp)
                                                .rotate(currentCategorySpec.rotateDegrees)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = noteTitle.ifBlank {
                                            when {
                                                isCodeCategory -> "Code Snippet"
                                                isLinkCategory -> "Website Link"
                                                else -> "New $selectedCategory Note"
                                            }
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            lineHeight = 18.sp
                                        ),
                                        color = navyTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                                            text = "Just now • $selectedCategory",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp
                                            ),
                                            color = timestampColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (textContent.isNotEmpty()) {
                                    Surface(
                                        onClick = { textContent = "" },
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
                                                    textContent = if (textContent.isBlank()) text else "$textContent\n$text"
                                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Clipboard has no text", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Could not read clipboard", Toast.LENGTH_SHORT).show()
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
                                value = textContent,
                                onValueChange = { textContent = it },
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
                                    .testTag("add_clip_content_input"),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = minEditorHeight),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        if (textContent.isEmpty()) {
                                            Text(
                                                text = if (isCodeCategory) {
                                                    "Paste or type code snippet here..."
                                                } else if (isLinkCategory) {
                                                    "Paste website URL or link notes here..."
                                                } else {
                                                    "Paste or type your note details here..."
                                                },
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

                        // Action Buttons Directly Below Content Textbox (Light Red Cancel + Royal Blue Save Note)
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
                                    .testTag("cancel_add_button")
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

                            // Royal Blue Compact Save Note Button
                            Button(
                                onClick = saveNoteAction,
                                enabled = textContent.isNotBlank(),
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
                                    .testTag("save_new_clip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Save Note",
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
}
