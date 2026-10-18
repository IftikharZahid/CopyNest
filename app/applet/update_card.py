path = 'app/src/main/java/com/zahidcodes/copynets/ui/HomeScreen.kt'
with open(path, 'r') as f:
    content = f.read()

lines = content.splitlines(keepends=True)
card_start = -1
for i, line in enumerate(lines):
    if "fun ClipCard(" in line:
        card_start = i - 2
        if card_start < 0: card_start = 0
        break

print(f"ClipCard starts at line {card_start}")

new_clip_card = '''@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipCard(
    clip: ClipEntity,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val formattedDate = remember(clip.timestamp) { dateFormat.format(Date(clip.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onCopy
            )
            .testTag("clip_card_${clip.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            // Single Top Line: Type Icon, Title, Category Pill, Timestamp, and Actions
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
                    // Type Icon
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (clip.contentType) {
                            "URL" -> MaterialTheme.colorScheme.primaryContainer
                            "CODE" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (clip.contentType) {
                                    "URL" -> Icons.Outlined.Link
                                    "CODE" -> Icons.Outlined.Code
                                    else -> Icons.Outlined.TextSnippet
                                },
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Title (or text snippet if title blank)
                    Text(
                        text = clip.title.ifBlank { clip.text.take(28) },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Category Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = clip.category,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Timestamp
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }

                // Action Buttons in the same line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (clip.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            modifier = Modifier.size(14.dp),
                            tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Below the single top line: Content
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = clip.text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (clip.contentType == "CODE") FontFamily.Monospace else FontFamily.Default,
                    fontSize = 12.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
'''

brace_count = 0
card_end = -1
in_card = False
for i in range(card_start, len(lines)):
    line = lines[i]
    if "fun ClipCard" in line:
        in_card = True
    if in_card:
        brace_count += line.count('{') - line.count('}')
        if brace_count == 0 and i > card_start:
            card_end = i
            break

print(f"ClipCard ends at line {card_end}")
if card_start != -1 and card_end != -1:
    new_lines = lines[:card_start] + [new_clip_card + "\n"] + lines[card_end+1:]
    with open(path, 'w') as f:
        f.writelines(new_lines)
    print("REPLACED SUCCESSFULLY")
else:
    print("FAILED TO LOCATE BOUNDARIES")
