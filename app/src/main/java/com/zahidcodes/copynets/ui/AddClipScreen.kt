package com.zahidcodes.copynets.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClipScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var noteTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }
    var textContent by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    val categories = listOf("General", "Links", "Code", "Study", "Work", "Important")

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Note") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (textContent.isNotBlank()) {
                                viewModel.saveManualClip(
                                    title = noteTitle.trim(),
                                    text = textContent.trim(),
                                    category = selectedCategory.ifBlank { "General" }
                                )
                                Toast.makeText(context, "Note saved successfully", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        enabled = textContent.isNotBlank(),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_new_clip_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and Category in the same line - compact
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compact Note Title input
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = {
                        noteTitle = it
                        if (selectedCategory.isBlank()) {
                            selectedCategory = "General"
                        }
                    },
                    label = { Text("Title") },
                    placeholder = { Text("Note title") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 56.dp)
                        .testTag("add_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Title,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Compact Category Selector dropdown
                Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                    OutlinedButton(
                        onClick = { expandedDropdown = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("category_dropdown_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Category,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedCategory, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }

                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                leadingIcon = {
                                    if (selectedCategory == cat) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCategory = cat
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Body Content section: compact, 5 lines minLines by default, auto-expanding as more details added
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                label = { Text("Content / Text") },
                placeholder = { Text("Paste or type your note content here...") },
                minLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_clip_content_input"),
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
