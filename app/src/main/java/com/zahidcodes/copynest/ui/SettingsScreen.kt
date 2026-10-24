package com.zahidcodes.copynest.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.zahidcodes.copynest.BuildConfig
import com.zahidcodes.copynest.R
import com.zahidcodes.copynest.data.DriveSyncManager
import com.zahidcodes.copynest.data.DriveSyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val autoCapture by viewModel.autoCapture.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val driveSyncEnabled by viewModel.driveSyncEnabled.collectAsState()
    val driveAutoSyncOnAddUpdate by viewModel.driveAutoSyncOnAddUpdate.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val driveLastSyncTime by viewModel.driveLastSyncTime.collectAsState()
    val driveLastSyncSummary by viewModel.driveLastSyncSummary.collectAsState()
    val driveSyncStatus by viewModel.driveSyncStatus.collectAsState()
    val hasPendingDriveChanges by viewModel.hasPendingDriveChanges.collectAsState()
    val pendingDriveChangesCount by viewModel.pendingDriveChangesCount.collectAsState()
    val isSystemDark = isSystemInDarkTheme()

    var showClearConfirm by remember { mutableStateOf(false) }
    var clearConfirmInput by remember { mutableStateOf("") }
    var showAppInfoPopup by remember { mutableStateOf(false) }
    var showConnectDrivePrompt by remember { mutableStateOf(false) }
    var pendingAuthEmail by remember { mutableStateOf("") }

    // Launcher for Google Identity Services Drive Scope Authorization resolution
    val driveAuthIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data)
            val accessToken = authResult.accessToken
            val email = pendingAuthEmail.ifBlank {
                GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()
            }
            if (!accessToken.isNullOrBlank() || result.resultCode == Activity.RESULT_OK) {
                viewModel.onGoogleDriveConnected(
                    accountEmail = email.ifBlank { "Google Drive Account" },
                    accessToken = accessToken
                ) { _, summary ->
                    Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Google Drive authorization was cancelled", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Drive authorization error: ${e.localizedMessage ?: "Failed"}", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher for UserRecoverableAuthException consent intent
    val recoverableConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.syncWithGoogleDrive(isManual = true) { _, summary ->
                Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Google Drive permission was not granted", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for real Google Sign-In + Drive scopes flow
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                val email = account?.email.orEmpty()
                pendingAuthEmail = email

                // Request Drive OAuth token via Google Identity Services AuthorizationClient
                try {
                    val authRequest = viewModel.driveSyncManager.buildAuthorizationRequest(email)
                    val authResult = Identity.getAuthorizationClient(context)
                        .authorize(authRequest)
                        .await()
                    if (authResult.hasResolution() && authResult.pendingIntent != null) {
                        driveAuthIntentSenderLauncher.launch(
                            IntentSenderRequest.Builder(authResult.pendingIntent!!).build()
                        )
                    } else {
                        viewModel.onGoogleDriveConnected(
                            accountEmail = email.ifBlank { account?.displayName ?: "Google Drive" },
                            accessToken = authResult.accessToken
                        ) { _, summary ->
                            Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    // Proceed with GoogleSignInAccount + GoogleAuthUtil token exchange
                    viewModel.onGoogleDriveConnected(
                        accountEmail = email.ifBlank { account?.displayName ?: "Google Drive" },
                        accessToken = null
                    ) { _, summary ->
                        Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                val message = when (e.statusCode) {
                    12501 -> "Google Sign-In cancelled"
                    10 -> "Google Cloud OAuth client setup required (Developer Error 10: verify SHA-1 & package name in Google Cloud Console)"
                    7 -> "Network error while connecting to Google Drive"
                    else -> "Google Sign-In failed (code ${e.statusCode})"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not complete Google Sign-In: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val data = viewModel.exportNotesAsText()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(data.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Exported notes successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    } ?: ""
                    if (content.isNotBlank()) {
                        val count = viewModel.importNotesFromText(content)
                        Toast.makeText(context, "Imported $count notes successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Selected file is empty", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAppInfoPopup = true },
                        modifier = Modifier.testTag("app_info_button")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "App Info & Terms of Use",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Clipboard Settings Card (Compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = "Clipboard Capture",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-capture on resume",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatically save new clipboard text when opening the app.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCapture,
                            onCheckedChange = { viewModel.setAutoCapture(it) },
                            thumbContent = if (autoCapture) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier
                                .scale(0.75f)
                                .height(26.dp)
                                .testTag("auto_capture_switch")
                        )
                    }
                }
            }

            // Sync with Google Drive Card (App Design System)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_with_google_drive_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Sync with Google Drive",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("sync_with_google_drive_title")
                            )
                        }

                        val isSyncing = driveSyncStatus is DriveSyncStatus.Syncing
                        val isError = driveSyncStatus is DriveSyncStatus.Error || driveSyncStatus is DriveSyncStatus.NeedsConsent
                        val statusBg = when {
                            !driveSyncEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            isSyncing -> MaterialTheme.colorScheme.primaryContainer
                            isError -> Color(0xFFFEE2E2)
                            else -> Color(0xFFDCFCE7)
                        }
                        val statusTextColor = when {
                            !driveSyncEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                            isSyncing -> MaterialTheme.colorScheme.onPrimaryContainer
                            isError -> Color(0xFFB91C1C)
                            else -> Color(0xFF15803D)
                        }
                        val statusLabel = when {
                            !driveSyncEnabled -> "Not Connected"
                            isSyncing -> "Syncing..."
                            isError -> "Attention"
                            else -> "Connected"
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusBg,
                            modifier = Modifier.testTag("drive_sync_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(9.dp),
                                        strokeWidth = 1.5.dp,
                                        color = statusTextColor
                                    )
                                }
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = statusTextColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!driveSyncEnabled) {
                        Text(
                            text = "Please connect your Google Drive account before uploading clipboard contents. Once connected, Copy Nest syncs automatically only when a new record is added or an existing record is updated.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("drive_connect_prompt_text")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showConnectDrivePrompt = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("sync_with_google_drive_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddToDrive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync with Google Drive",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    } else {
                        // Connected state details
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = driveAccountEmail.ifBlank { "Google Account Connected" },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("drive_connected_email_text")
                                    )
                                    Text(
                                        text = "Folder: ${DriveSyncManager.COPY_NEST_FOLDER_NAME}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                val statusDescription = when (val st = driveSyncStatus) {
                                    is DriveSyncStatus.Syncing -> st.message
                                    is DriveSyncStatus.Error -> st.errorMessage
                                    is DriveSyncStatus.NeedsConsent -> st.message
                                    else -> {
                                        val summary = driveLastSyncSummary.ifBlank { "Auto-syncs on new or updated records" }
                                        if (driveLastSyncTime > 0L) {
                                            val formattedTime = remember(driveLastSyncTime) {
                                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                                    .format(Date(driveLastSyncTime))
                                            }
                                            "$summary • $formattedTime"
                                        } else {
                                            summary
                                        }
                                    }
                                }

                                Text(
                                    text = statusDescription,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5.sp,
                                        lineHeight = 13.5.sp
                                    ),
                                    color = if (driveSyncStatus is DriveSyncStatus.Error) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.testTag("drive_sync_summary_text")
                                )
                            }
                        }

                        if (driveSyncStatus is DriveSyncStatus.NeedsConsent) {
                            val consentIntent = (driveSyncStatus as DriveSyncStatus.NeedsConsent).consentIntent
                            if (consentIntent != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { recoverableConsentLauncher.launch(consentIntent) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .testTag("drive_grant_permission_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve Google Drive Access", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Modern "Auto on Add / Update" toggle switch row matching "Auto-capture on resume"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setDriveAutoSyncOnAddUpdate(!driveAutoSyncOnAddUpdate)
                                }
                                .padding(vertical = 2.dp)
                                .testTag("drive_auto_sync_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto on Add / Update",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.5.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (driveAutoSyncOnAddUpdate) {
                                        "Automatically update records on Google Drive when added or edited."
                                    } else {
                                        "Auto-sync is off. Records will not update on Google Drive when added or edited."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = driveAutoSyncOnAddUpdate,
                                onCheckedChange = { enabled ->
                                    viewModel.setDriveAutoSyncOnAddUpdate(enabled)
                                },
                                thumbContent = if (driveAutoSyncOnAddUpdate) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier
                                    .scale(0.75f)
                                    .height(26.dp)
                                    .testTag("drive_auto_sync_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.disconnectGoogleDrive {
                                    Toast.makeText(context, "Disconnected from Google Drive", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .testTag("drive_disconnect_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Disconnect", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }
                }
            }

            // Appearance Card (Compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        val activeThemeLabel = when (themeMode.uppercase()) {
                            "DARK" -> "Dark active"
                            "LIGHT" -> "Light active"
                            else -> "System (${if (isSystemDark) "Dark" else "Light"})"
                        }
                        Text(
                            text = activeThemeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("SYSTEM", "System", Icons.Outlined.BrightnessAuto),
                            Triple("LIGHT", "Light", Icons.Outlined.LightMode),
                            Triple("DARK", "Dark", Icons.Outlined.DarkMode)
                        ).forEach { (mode, label, icon) ->
                            val isSelected = themeMode.equals(mode, ignoreCase = true)
                            Surface(
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .testTag("theme_chip_${mode.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Data Management Card (Compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Export notes to a local text file or import them back.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("copynest_backup.txt") },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("export_notes_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Notes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("import_notes_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Notes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("clear_all_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Saved Clips", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp))
                    }
                }
            }

            // Privacy Card (Compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (driveSyncEnabled) "Local DB + Private Google Drive Sync" else "100% Private & Local",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (driveSyncEnabled) {
                                "Clips are saved in your local database and synced only with your own Google Drive 'Copy Nest' folder."
                            } else {
                                "All clips are stored locally on your device. Clipboard contents are never uploaded unless you connect Google Drive."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // About Developer Card (Ultra-Compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    // Profile Header Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_developer),
                            contentDescription = "Iftikhar Zahid Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "Iftikhar Zahid",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "PRO",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Compact "Buy me a coffee" button in the top-right corner next to developer name
                                Surface(
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://buymeacoffee.com/iftikharzahid")
                                            )
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFFDD00),
                                    border = BorderStroke(0.8.dp, Color(0xFFE5C100)),
                                    modifier = Modifier
                                        .height(22.dp)
                                        .testTag("buy_me_a_coffee_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Coffee,
                                            contentDescription = "Buy me a coffee",
                                            tint = Color(0xFF1A1A1A),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Buy me a coffee",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color(0xFF1A1A1A),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Senior Mobile & Web Developer",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "iftikharzahid.me",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://iftikharzahid.me"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact Bio
                    Text(
                        text = "Crafting high-performance cross-platform apps, modern UI/UX, and AI automation with clean architecture.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact Skill Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Mobile Apps", "Flutter & React", "AI Automation", "Firebase").forEach { skill ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = skill,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Connect Links
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // GitHub Button
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/IftikharZahid"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("developer_github_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_github),
                                    contentDescription = "GitHub",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    maxLines = 1
                                )
                            }
                        }

                        // WhatsApp Button
                        Surface(
                            onClick = {
                                try {
                                    val message = Uri.encode("Hello Iftikhar! I am contacting you through the CopyNest app.")
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/+923007971374?text=$message"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("developer_whatsapp_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_whatsapp),
                                    contentDescription = "WhatsApp",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "WhatsApp",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    maxLines = 1
                                )
                            }
                        }

                        // Facebook Button
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/iftikharxahid"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Facebook", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("developer_facebook_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_facebook),
                                    contentDescription = "Facebook",
                                    tint = Color(0xFF1877F2),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Facebook",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    maxLines = 1
                                )
                            }
                        }

                        // Web Portfolio Button
                        Surface(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://iftikharzahid.me"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Website", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .testTag("developer_web_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = "Website",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Website",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Consent Dialog asking the user clearly to connect Google Drive before uploading clipboard contents
    if (showConnectDrivePrompt) {
        AlertDialog(
            onDismissRequest = { showConnectDrivePrompt = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.AddToDrive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Connect Google Drive?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Connect your Google Drive account to enable automatic clipboard synchronization:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "• Reuses or creates the \"${DriveSyncManager.COPY_NEST_FOLDER_NAME}\" folder in your Google Drive.\n" +
                            "• Downloads existing text files from \"${DriveSyncManager.COPY_NEST_FOLDER_NAME}\" into Copy Nest on initial connect.\n" +
                            "• Automatically syncs only when a new record is added or an existing record is updated.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConnectDrivePrompt = false
                        try {
                            val signInIntent = viewModel.driveSyncManager.getGoogleSignInClient().signInIntent
                            googleSignInLauncher.launch(signInIntent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Unable to launch Google Sign-In: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_connect_drive_button")
                ) {
                    Text("Connect Google Drive")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConnectDrivePrompt = false },
                    modifier = Modifier.testTag("cancel_connect_drive_button")
                ) {
                    Text("Not Now")
                }
            },
            modifier = Modifier.testTag("connect_drive_confirm_dialog")
        )
    }

    if (showAppInfoPopup) {
        AppInfoTermsPopup(
            onDismiss = { showAppInfoPopup = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = {
                showClearConfirm = false
                clearConfirmInput = ""
            },
            title = { Text("Clear All Records") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This action will permanently delete all saved clips. To confirm, please type \"Delete All Record\" below:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = clearConfirmInput,
                        onValueChange = { clearConfirmInput = it },
                        placeholder = { Text("Delete All Record") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_confirm_input")
                    )
                }
            },
            confirmButton = {
                val isConfirmed = clearConfirmInput.trim().equals("Delete All Record", ignoreCase = true) ||
                    clearConfirmInput.trim().equals("Delete All Records", ignoreCase = true)
                Button(
                    onClick = {
                        if (isConfirmed) {
                            viewModel.deleteAllClips()
                            showClearConfirm = false
                            clearConfirmInput = ""
                            Toast.makeText(context, "All records deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_all_button")
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    clearConfirmInput = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppInfoTermsPopup(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val contactEmail = "IftikharXahid@gmail.com"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .testTag("app_info_popup")
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // Popup Header: Brand Logo + App Name & Version Badge + Close Button
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
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF041228),
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_fg_art),
                                    contentDescription = "CopyNest Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "CopyNest",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "v$versionName (Build $versionCode)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("app_version_badge")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Smart Clipboard Notebook • Terms & Info",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("close_app_info_popup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Terms of Use Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 310.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TermsSectionItem(
                        icon = Icons.Outlined.VerifiedUser,
                        title = "1. Terms of Use & Purpose",
                        description = "CopyNest is designed to help you capture, organize, categorize, and search your copied text, links, and code snippets cleanly on your Android device."
                    )
                    TermsSectionItem(
                        icon = Icons.Outlined.Lock,
                        title = "2. Local Storage & Optional Google Drive Sync",
                        description = "All saved clipboard records are stored locally on your device. If you enable Sync with Google Drive, your notes are synced exclusively with the 'Copy Nest' folder in your personal Google Drive."
                    )
                    TermsSectionItem(
                        icon = Icons.Outlined.Language,
                        title = "3. Link Previews & Network Access",
                        description = "Internet access is used on demand to fetch public webpage titles/favicons for URLs and, when enabled by you, to synchronize notes with your Google Drive."
                    )
                    TermsSectionItem(
                        icon = Icons.Outlined.Backup,
                        title = "4. Data Ownership & Backups",
                        description = "You retain full ownership of your notes. You can back up notes at any time using Sync with Google Drive or Export Notes."
                    )
                    TermsSectionItem(
                        icon = Icons.Outlined.Shield,
                        title = "5. Disclaimer & Safe Usage",
                        description = "CopyNest is provided \"as is\" without warranties of any kind. For your security, avoid keeping plain-text passwords, banking PINs, or private recovery keys in your clipboard history."
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))

                // Official Contact Email Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val emailIntent = Intent(
                                        Intent.ACTION_SENDTO,
                                        Uri.parse("mailto:$contactEmail?subject=${Uri.encode("CopyNest App Support (v$versionName)")}")
                                    )
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Support Email", contactEmail))
                                    Toast.makeText(context, "Email copied: $contactEmail", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("contact_email_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Email,
                                        contentDescription = "Contact Email",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Questions or Feedback? Contact Us",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contactEmail,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("contact_email_text")
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Support Email", contactEmail))
                                Toast.makeText(context, "Copied $contactEmail", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Package: com.zahidcodes.copynest",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = MaterialTheme.colorScheme.outline
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("got_it_info_popup_button")
                    ) {
                        Text(
                            text = "Got It",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsSectionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(15.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        lineHeight = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
