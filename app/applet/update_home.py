path = 'app/src/main/java/com/zahidcodes/copynets/ui/HomeScreen.kt'
with open(path, 'r') as f:
    code = f.read()

old_block = """                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(clips, key = { it.id }) { clip ->
                        ClipCard(
                            clip = clip,
                            onClick = { onNavigateToDetail(clip.id) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = ClipData.newPlainText("CopyNest", clip.text)
                                clipboard.setPrimaryClip(clipData)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onTogglePin = { viewModel.togglePin(clip) },
                            onDelete = { viewModel.deleteClip(clip.id) }
                        )
                    }
                }"""

new_block = """                val pinnedClips = clips.filter { it.isPinned }
                val unpinnedClips = clips.filter { !it.isPinned }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (pinnedClips.isNotEmpty()) {
                        item(key = "header_pinned") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Pinned Clips",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        items(pinnedClips, key = { "pinned_${it.id}" }) { clip ->
                            ClipCard(
                                clip = clip,
                                onClick = { onNavigateToDetail(clip.id) },
                                onCopy = {
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
                            item(key = "divider_pinned_unpinned") {
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }

                    if (unpinnedClips.isNotEmpty() && pinnedClips.isNotEmpty()) {
                        item(key = "header_other") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "Recent Clips",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }

                    items(unpinnedClips, key = { "unpinned_${it.id}" }) { clip ->
                        ClipCard(
                            clip = clip,
                            onClick = { onNavigateToDetail(clip.id) },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = ClipData.newPlainText("CopyNest", clip.text)
                                clipboard.setPrimaryClip(clipData)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onTogglePin = { viewModel.togglePin(clip) },
                            onDelete = { viewModel.deleteClip(clip.id) }
                        )
                    }
                }"""

if old_block in code:
    code = code.replace(old_block, new_block)
    with open(path, 'w') as f:
        f.write(code)
    print("SUCCESS")
else:
    print("ERROR: old_block not found")
