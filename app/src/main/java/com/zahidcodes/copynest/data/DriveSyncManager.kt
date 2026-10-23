package com.zahidcodes.copynest.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class DriveRemoteFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: Long,
    val appProperties: Map<String, String> = emptyMap()
)

sealed class DriveSyncStatus {
    object Disconnected : DriveSyncStatus()
    data class Idle(
        val accountEmail: String,
        val folderName: String = DriveSyncManager.COPY_NEST_FOLDER_NAME,
        val lastSyncTime: Long = 0L,
        val lastSyncSummary: String = "Connected"
    ) : DriveSyncStatus()

    data class Syncing(
        val accountEmail: String,
        val message: String = "Syncing with Google Drive..."
    ) : DriveSyncStatus()

    data class NeedsConsent(
        val accountEmail: String,
        val consentIntent: Intent?,
        val message: String = "Additional Google Drive permission required"
    ) : DriveSyncStatus()

    data class Error(
        val accountEmail: String,
        val errorMessage: String,
        val isNetworkError: Boolean = false
    ) : DriveSyncStatus()
}

data class DriveSyncReport(
    val folderId: String,
    val downloadedCount: Int,
    val uploadedCount: Int,
    val updatedCount: Int,
    val deletedRemoteCount: Int,
    val deletedLocalCount: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toSummaryString(): String {
        val parts = mutableListOf<String>()
        if (downloadedCount > 0) parts.add("$downloadedCount downloaded")
        if (uploadedCount > 0) parts.add("$uploadedCount uploaded")
        if (updatedCount > 0) parts.add("$updatedCount updated")
        if (deletedRemoteCount > 0 || deletedLocalCount > 0) {
            parts.add("${deletedRemoteCount + deletedLocalCount} removed")
        }
        return if (parts.isEmpty()) {
            "Up to date with Copy Nest folder"
        } else {
            "Synced • ${parts.joinToString(", ")}"
        }
    }
}

class DriveUnauthorizedException(message: String) : IOException(message)
class DriveFileNotFoundException(message: String) : IOException(message)

interface DriveApiClient {
    suspend fun isFolderValid(accessToken: String, folderId: String): Boolean
    suspend fun findFolderByName(accessToken: String, folderName: String): String?
    suspend fun createFolder(accessToken: String, folderName: String): String
    suspend fun listFilesInFolder(accessToken: String, folderId: String): List<DriveRemoteFile>
    suspend fun downloadFileText(accessToken: String, file: DriveRemoteFile): String
    suspend fun uploadTextFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String,
        appProperties: Map<String, String>
    ): DriveRemoteFile

    suspend fun updateTextFile(
        accessToken: String,
        fileId: String,
        fileName: String,
        content: String,
        appProperties: Map<String, String>
    ): DriveRemoteFile

    suspend fun deleteFile(accessToken: String, fileId: String)
}

class OkHttpDriveApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : DriveApiClient {

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val GOOGLE_DOC_MIME_TYPE = "application/vnd.google-apps.document"
    }

    override suspend fun isFolderValid(accessToken: String, folderId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (folderId.isBlank()) return@withContext false
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$folderId?fields=id,name,trashed,mimeType")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                if (!response.isSuccessful) return@withContext false
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                val trashed = json.optBoolean("trashed", false)
                val mimeType = json.optString("mimeType", "")
                !trashed && mimeType == FOLDER_MIME_TYPE
            }
        }

    override suspend fun findFolderByName(accessToken: String, folderName: String): String? =
        withContext(Dispatchers.IO) {
            val escapedName = folderName.replace("'", "\\'")
            val query = "mimeType = '$FOLDER_MIME_TYPE' and name = '$escapedName' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$DRIVE_FILES_URL?q=$encodedQuery&spaces=drive&fields=files(id,name,createdTime)&pageSize=10"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                if (!response.isSuccessful) {
                    throw IOException("Failed to search Drive folder (HTTP ${response.code})")
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val files = json.optJSONArray("files") ?: return@withContext null
                if (files.length() > 0) {
                    files.getJSONObject(0).optString("id").takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        }

    override suspend fun createFolder(accessToken: String, folderName: String): String =
        withContext(Dispatchers.IO) {
            val metadata = JSONObject().apply {
                put("name", folderName)
                put("mimeType", FOLDER_MIME_TYPE)
                put("description", "Copy Nest synchronized clipboard notes")
            }
            val requestBody = metadata.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL?fields=id,name")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Failed to create '$folderName' folder in Google Drive (HTTP ${response.code})")
                }
                val json = JSONObject(body)
                val id = json.optString("id")
                if (id.isBlank()) throw IOException("Drive returned empty folder ID")
                id
            }
        }

    override suspend fun listFilesInFolder(accessToken: String, folderId: String): List<DriveRemoteFile> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<DriveRemoteFile>()
            var pageToken: String? = null
            do {
                val query = "'$folderId' in parents and trashed = false and mimeType != '$FOLDER_MIME_TYPE'"
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val tokenParam = if (!pageToken.isNullOrBlank()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
                val url = "$DRIVE_FILES_URL?q=$encodedQuery&spaces=drive&fields=nextPageToken,files(id,name,mimeType,modifiedTime,appProperties)&pageSize=100$tokenParam"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                    if (!response.isSuccessful) {
                        throw IOException("Failed to list files in Copy Nest folder (HTTP ${response.code})")
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val filesArray = json.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        result.add(parseDriveRemoteFile(fileObj))
                    }
                    pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
                }
            } while (!pageToken.isNullOrBlank())
            result
        }

    override suspend fun downloadFileText(accessToken: String, file: DriveRemoteFile): String =
        withContext(Dispatchers.IO) {
            val url = if (file.mimeType == GOOGLE_DOC_MIME_TYPE) {
                "$DRIVE_FILES_URL/${file.id}/export?mimeType=text/plain"
            } else {
                "$DRIVE_FILES_URL/${file.id}?alt=media"
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                if (response.code == 404) throw DriveFileNotFoundException("Drive file not found: ${file.id}")
                if (!response.isSuccessful) {
                    throw IOException("Failed to download Drive file ${file.name} (HTTP ${response.code})")
                }
                response.body?.string().orEmpty()
            }
        }

    override suspend fun uploadTextFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String,
        appProperties: Map<String, String>
    ): DriveRemoteFile = withContext(Dispatchers.IO) {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "text/plain")
            put("parents", JSONArray().put(folderId))
            if (appProperties.isNotEmpty()) {
                val propsObj = JSONObject()
                appProperties.forEach { (k, v) -> propsObj.put(k, v) }
                put("appProperties", propsObj)
            }
        }

        val boundary = "copynest_boundary_${System.currentTimeMillis()}"
        val multipartBody = buildMultipartRelatedBody(boundary, metadataJson.toString(), content)
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id,name,mimeType,modifiedTime,appProperties")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Failed to upload clip to Google Drive (HTTP ${response.code})")
            }
            parseDriveRemoteFile(JSONObject(body))
        }
    }

    override suspend fun updateTextFile(
        accessToken: String,
        fileId: String,
        fileName: String,
        content: String,
        appProperties: Map<String, String>
    ): DriveRemoteFile = withContext(Dispatchers.IO) {
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", "text/plain")
            if (appProperties.isNotEmpty()) {
                val propsObj = JSONObject()
                appProperties.forEach { (k, v) -> propsObj.put(k, v) }
                put("appProperties", propsObj)
            }
        }

        val boundary = "copynest_boundary_${System.currentTimeMillis()}"
        val multipartBody = buildMultipartRelatedBody(boundary, metadataJson.toString(), content)
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=multipart&fields=id,name,mimeType,modifiedTime,appProperties")
            .addHeader("Authorization", "Bearer $accessToken")
            .patch(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
            if (response.code == 404) throw DriveFileNotFoundException("Drive file $fileId not found")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Failed to update clip in Google Drive (HTTP ${response.code})")
            }
            parseDriveRemoteFile(JSONObject(body))
        }
    }

    override suspend fun deleteFile(accessToken: String, fileId: String) =
        withContext(Dispatchers.IO) {
            if (fileId.isBlank()) return@withContext
            val trashBody = JSONObject().put("trashed", true).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$fileId")
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(trashBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401) throw DriveUnauthorizedException("Unauthorized")
                // Ignore 404 if already deleted on Drive
            }
        }

    private fun buildMultipartRelatedBody(
        boundary: String,
        metadataJson: String,
        textContent: String
    ): String {
        return buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
            append(textContent).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
    }

    private fun parseDriveRemoteFile(fileObj: JSONObject): DriveRemoteFile {
        val id = fileObj.optString("id", "")
        val name = fileObj.optString("name", "")
        val mimeType = fileObj.optString("mimeType", "text/plain")
        val modifiedTimeStr = fileObj.optString("modifiedTime", "")
        val modifiedTime = parseRfc3339Timestamp(modifiedTimeStr)
        val propsMap = mutableMapOf<String, String>()
        val propsObj = fileObj.optJSONObject("appProperties")
        if (propsObj != null) {
            val keys = propsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                propsMap[key] = propsObj.optString(key, "")
            }
        }
        return DriveRemoteFile(
            id = id,
            name = name,
            mimeType = mimeType,
            modifiedTime = modifiedTime,
            appProperties = propsMap
        )
    }

    private fun parseRfc3339Timestamp(value: String): Long {
        if (value.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed: Date? = sdf.parse(value)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}

class DriveSyncManager(
    private val context: Context,
    private val repository: ClipRepository,
    private val userPreferences: UserPreferences,
    private val driveApiClient: DriveApiClient = OkHttpDriveApiClient(),
    private val networkChecker: () -> Boolean = { isNetworkAvailable(context) },
    private val tokenProvider: (suspend (String) -> String)? = null
) {
    private val syncMutex = Mutex()

    @Volatile
    private var cachedAccessToken: String? = null

    fun setCachedAccessToken(token: String?) {
        cachedAccessToken = token?.takeIf { it.isNotBlank() }
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DRIVE_FILE_SCOPE),
                Scope(DRIVE_FULL_SCOPE)
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun buildAuthorizationRequest(accountEmail: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(
                    Scope(DRIVE_FILE_SCOPE),
                    Scope(DRIVE_FULL_SCOPE)
                )
            )
        if (!accountEmail.isNullOrBlank()) {
            builder.setAccount(Account(accountEmail, "com.google"))
        }
        return builder.build()
    }

    suspend fun acquireAccessToken(accountEmail: String): String = withContext(Dispatchers.IO) {
        tokenProvider?.let { return@withContext it(accountEmail) }

        cachedAccessToken?.takeIf { it.isNotBlank() }?.let { return@withContext it }

        // 1. Try Google Identity Services AuthorizationClient silently first
        try {
            val authResult = Identity.getAuthorizationClient(context)
                .authorize(buildAuthorizationRequest(accountEmail))
                .await()
            if (!authResult.hasResolution() && !authResult.accessToken.isNullOrBlank()) {
                val token = authResult.accessToken!!
                cachedAccessToken = token
                return@withContext token
            }
        } catch (_: Exception) {
            // Fall through to GoogleAuthUtil
        }

        // 2. Fallback to GoogleAuthUtil.getToken with explicit Google account
        val resolvedEmail = accountEmail.ifBlank {
            GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()
        }
        if (resolvedEmail.isBlank()) {
            throw IOException("Google account is not signed in. Please connect Google Drive in Settings.")
        }
        val account = Account(resolvedEmail, "com.google")
        val scopeString = "oauth2:$DRIVE_FILE_SCOPE $DRIVE_FULL_SCOPE"
        val token = GoogleAuthUtil.getToken(context, account, scopeString)
        cachedAccessToken = token
        token
    }

    private suspend fun invalidateAccessToken(accountEmail: String) = withContext(Dispatchers.IO) {
        val tokenToClear = cachedAccessToken
        cachedAccessToken = null
        if (!tokenToClear.isNullOrBlank() && tokenProvider == null) {
            try {
                GoogleAuthUtil.clearToken(context, tokenToClear)
            } catch (_: Exception) {}
        }
    }

    suspend fun ensureCopyNestFolder(accessToken: String): String {
        val cachedFolderId = userPreferences.getDriveFolderId()
        if (cachedFolderId.isNotBlank()) {
            try {
                if (driveApiClient.isFolderValid(accessToken, cachedFolderId)) {
                    return cachedFolderId
                }
            } catch (e: DriveUnauthorizedException) {
                throw e
            } catch (_: Exception) {
                // Lookup by name if cached check failed
            }
        }

        val existingFolderId = driveApiClient.findFolderByName(accessToken, COPY_NEST_FOLDER_NAME)
        if (!existingFolderId.isNullOrBlank()) {
            userPreferences.setDriveFolderId(existingFolderId)
            return existingFolderId
        }

        val createdFolderId = driveApiClient.createFolder(accessToken, COPY_NEST_FOLDER_NAME)
        userPreferences.setDriveFolderId(createdFolderId)
        return createdFolderId
    }

    suspend fun performTwoWaySync(
        contentTypeDetector: (String) -> String,
        autoCategoryResolver: (String) -> String
    ): Result<DriveSyncReport> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!userPreferences.isDriveSyncEnabled()) {
                    return@withContext Result.failure(
                        IllegalStateException("Google Drive sync is not connected.")
                    )
                }

                if (!networkChecker()) {
                    return@withContext Result.failure(
                        IOException("No internet connection. Local clips are saved and will sync when online.")
                    )
                }

                val accountEmail = userPreferences.getDriveAccountEmail()
                val report = try {
                    val token = acquireAccessToken(accountEmail)
                    executeSyncWithToken(
                        accessToken = token,
                        contentTypeDetector = contentTypeDetector,
                        autoCategoryResolver = autoCategoryResolver
                    )
                } catch (e: DriveUnauthorizedException) {
                    invalidateAccessToken(accountEmail)
                    val refreshedToken = acquireAccessToken(accountEmail)
                    executeSyncWithToken(
                        accessToken = refreshedToken,
                        contentTypeDetector = contentTypeDetector,
                        autoCategoryResolver = autoCategoryResolver
                    )
                }

                userPreferences.updateDriveLastSync(
                    timestamp = report.timestamp,
                    summary = report.toSummaryString()
                )
                Result.success(report)
            } catch (e: UserRecoverableAuthException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun executeSyncWithToken(
        accessToken: String,
        contentTypeDetector: (String) -> String,
        autoCategoryResolver: (String) -> String
    ): DriveSyncReport {
        // 1. Reuse or create the "Copy Nest" folder in Google Drive
        val folderId = ensureCopyNestFolder(accessToken)

        var downloadedCount = 0
        var uploadedCount = 0
        var updatedCount = 0
        var deletedRemoteCount = 0
        var deletedLocalCount = 0

        // 2. Fetch current remote files in "Copy Nest" folder
        val initialRemoteFiles = driveApiClient.listFilesInFolder(accessToken, folderId).toMutableList()

        // 3. Propagate local deletions (tombstones) to Google Drive
        val unsyncedDeletions = repository.getUnsyncedDeletedClips()
        val deletedRemoteFileIds = mutableSetOf<String>()
        for (deletion in unsyncedDeletions) {
            if (deletion.driveFileId.isNotBlank()) {
                try {
                    driveApiClient.deleteFile(accessToken, deletion.driveFileId)
                    deletedRemoteFileIds.add(deletion.driveFileId)
                    deletedRemoteCount++
                } catch (_: Exception) {}
            }
            // Also delete any remote file with matching contentHash that is older than or equal to deletion time
            val matchingRemotes = initialRemoteFiles.filter { remote ->
                remote.id !in deletedRemoteFileIds &&
                    remote.appProperties["contentHash"] == deletion.contentHash &&
                    remote.modifiedTime <= deletion.deletedAt + 2_000L
            }
            for (remote in matchingRemotes) {
                try {
                    driveApiClient.deleteFile(accessToken, remote.id)
                    deletedRemoteFileIds.add(remote.id)
                    deletedRemoteCount++
                } catch (_: Exception) {}
            }
            repository.markDeletedClipSynced(deletion.contentHash)
        }

        val activeRemoteFiles = initialRemoteFiles.filter { it.id !in deletedRemoteFileIds }
        val activeRemoteById = activeRemoteFiles.associateBy { it.id }

        // 4. Handle remote deletions: if a local clip was previously SYNCED to a Drive file ID,
        // and that Drive file ID is no longer in the "Copy Nest" Drive folder, remove it locally
        val localClipsBeforeDownload = repository.getAllClipsList()
        for (localClip in localClipsBeforeDownload) {
            if (localClip.driveFileId.isNotBlank() &&
                localClip.syncState == SYNC_STATE_SYNCED &&
                !activeRemoteById.containsKey(localClip.driveFileId)
            ) {
                val hash = computeContentFingerprint(localClip.text)
                repository.insertDeletedClip(
                    DeletedClipEntity(
                        contentHash = hash,
                        normalizedText = localClip.text.trim().take(500),
                        deletedAt = System.currentTimeMillis(),
                        clipboardTimestamp = 0L,
                        driveFileId = localClip.driveFileId,
                        syncedDeletionToDrive = true
                    )
                )
                repository.deleteClipById(localClip.id)
                deletedLocalCount++
            }
        }

        // 5. Download existing or new Drive text files into the app (without duplicates)
        val processedRemoteContentHashes = mutableSetOf<String>()
        val remoteHashToFileMap = mutableMapOf<String, DriveRemoteFile>()

        for (remoteFile in activeRemoteFiles) {
            if (!isSupportedTextFile(remoteFile)) continue

            val existingByDriveId = repository.getClipByDriveFileId(remoteFile.id)
            if (existingByDriveId != null) {
                val existingHash = computeContentFingerprint(existingByDriveId.text)
                processedRemoteContentHashes.add(existingHash)
                remoteHashToFileMap[existingHash] = remoteFile

                // If remote file was edited more recently than local clip and local has no pending edits
                if (existingByDriveId.syncState == SYNC_STATE_SYNCED &&
                    remoteFile.modifiedTime > existingByDriveId.updatedAt + 2_000L
                ) {
                    val rawText = try {
                        driveApiClient.downloadFileText(accessToken, remoteFile)
                    } catch (_: Exception) {
                        continue
                    }
                    val trimmedText = rawText.trim()
                    if (trimmedText.isNotBlank()) {
                        val newHash = computeContentFingerprint(trimmedText)
                        val detectedType = remoteFile.appProperties["contentType"]
                            ?.takeIf { it.isNotBlank() }
                            ?: contentTypeDetector(trimmedText)
                        val resolvedCategory = remoteFile.appProperties["category"]
                            ?.takeIf { it.isNotBlank() }
                            ?: existingByDriveId.category.ifBlank { autoCategoryResolver(detectedType) }
                        val resolvedTitle = remoteFile.appProperties["title"]
                            ?: existingByDriveId.title
                        val resolvedPinned = remoteFile.appProperties["isPinned"]
                            ?.toBooleanStrictOrNull()
                            ?: existingByDriveId.isPinned
                        val detectedLink = LinkMetadataManager.extractFirstLink(trimmedText)

                        val updatedClip = existingByDriveId.copy(
                            title = resolvedTitle,
                            text = trimmedText,
                            contentType = detectedType,
                            category = resolvedCategory,
                            isPinned = resolvedPinned,
                            linkUrl = detectedLink?.normalizedUrl ?: existingByDriveId.linkUrl,
                            linkDomain = detectedLink?.domain ?: existingByDriveId.linkDomain,
                            driveFileId = remoteFile.id,
                            syncState = SYNC_STATE_SYNCED,
                            updatedAt = remoteFile.modifiedTime
                        )
                        repository.updateClip(updatedClip)
                        processedRemoteContentHashes.add(newHash)
                        remoteHashToFileMap[newHash] = remoteFile
                        updatedCount++
                    }
                }
                continue
            }

            // Remote file is not linked by driveFileId yet — download its text content
            val rawContent = try {
                driveApiClient.downloadFileText(accessToken, remoteFile)
            } catch (_: Exception) {
                continue
            }
            val trimmedText = rawContent.trim()
            if (trimmedText.isBlank()) continue

            val hash = computeContentFingerprint(trimmedText)
            remoteHashToFileMap.putIfAbsent(hash, remoteFile)

            // Skip if another remote file in this batch already had the exact same text
            if (!processedRemoteContentHashes.add(hash)) {
                continue
            }

            // Check deletion tombstones so deleted items are not re-imported unless re-created on Drive after deletion
            val deletedRecord = repository.getDeletedClip(hash)
            if (deletedRecord != null) {
                if (remoteFile.modifiedTime <= deletedRecord.deletedAt + 2_000L) {
                    try {
                        driveApiClient.deleteFile(accessToken, remoteFile.id)
                        deletedRemoteCount++
                    } catch (_: Exception) {}
                    continue
                } else {
                    repository.deleteDeletedClip(hash)
                }
            }

            // Check if local database already has a clip with this exact normalized text
            val existingByText = repository.getClipByText(trimmedText)
            if (existingByText != null) {
                // Link existing local clip to this Drive file without creating a duplicate
                val mergedTitle = existingByText.title.ifBlank {
                    extractTitleFromRemoteFile(remoteFile)
                }
                val mergedPinned = existingByText.isPinned ||
                    (remoteFile.appProperties["isPinned"]?.toBooleanStrictOrNull() == true)
                val linkedClip = existingByText.copy(
                    title = mergedTitle,
                    isPinned = mergedPinned,
                    driveFileId = remoteFile.id,
                    syncState = if (existingByText.syncState == SYNC_STATE_PENDING_UPDATE) {
                        SYNC_STATE_PENDING_UPDATE
                    } else {
                        SYNC_STATE_SYNCED
                    },
                    updatedAt = maxOf(existingByText.updatedAt, remoteFile.modifiedTime)
                )
                repository.updateClip(linkedClip)
            } else {
                // Insert new clip downloaded from Google Drive into local Room database
                val detectedType = remoteFile.appProperties["contentType"]
                    ?.takeIf { it.isNotBlank() }
                    ?: contentTypeDetector(trimmedText)
                val category = remoteFile.appProperties["category"]
                    ?.takeIf { it.isNotBlank() }
                    ?: autoCategoryResolver(detectedType)
                val title = extractTitleFromRemoteFile(remoteFile)
                val isPinned = remoteFile.appProperties["isPinned"]?.toBooleanStrictOrNull() ?: false
                val clipTimestamp = remoteFile.appProperties["timestamp"]?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: remoteFile.modifiedTime
                val detectedLink = LinkMetadataManager.extractFirstLink(trimmedText)
                val linkUrl = detectedLink?.normalizedUrl ?: ""
                val linkDomain = detectedLink?.domain ?: ""
                val cachedLinkMeta = if (linkUrl.isNotBlank()) {
                    try {
                        repository.getCachedLinkMetadata(linkUrl)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val newClip = ClipEntity(
                    title = title,
                    text = trimmedText,
                    timestamp = clipTimestamp,
                    isPinned = isPinned,
                    contentType = detectedType,
                    category = category,
                    linkUrl = linkUrl,
                    linkDomain = cachedLinkMeta?.domain ?: linkDomain,
                    linkSiteName = cachedLinkMeta?.siteName ?: "",
                    linkTitle = cachedLinkMeta?.title ?: "",
                    linkDescription = cachedLinkMeta?.description ?: "",
                    linkFaviconBase64 = cachedLinkMeta?.faviconBase64 ?: "",
                    linkMetadataLoaded = cachedLinkMeta != null,
                    driveFileId = remoteFile.id,
                    syncState = SYNC_STATE_SYNCED,
                    updatedAt = remoteFile.modifiedTime
                )
                repository.insertClip(newClip)
                downloadedCount++
            }
        }

        // 6. Upload missing local clips and push pending local updates to Google Drive
        val allLocalClips = repository.getAllClipsList()
        val uploadedHashesInBatch = mutableSetOf<String>()

        for (localClip in allLocalClips) {
            val trimmedText = localClip.text.trim()
            if (trimmedText.isBlank()) continue
            val hash = computeContentFingerprint(trimmedText)

            if (localClip.driveFileId.isBlank() || localClip.syncState == SYNC_STATE_PENDING_UPLOAD) {
                // Check if remote already has a file with this exact contentHash
                val existingRemote = remoteHashToFileMap[hash] ?: activeRemoteFiles.firstOrNull {
                    it.appProperties["contentHash"] == hash
                }
                if (existingRemote != null) {
                    repository.updateClipDriveSyncState(
                        clipId = localClip.id,
                        driveFileId = existingRemote.id,
                        syncState = SYNC_STATE_SYNCED,
                        updatedAt = maxOf(localClip.updatedAt, existingRemote.modifiedTime)
                    )
                    uploadedHashesInBatch.add(hash)
                    continue
                }

                if (!uploadedHashesInBatch.add(hash)) {
                    continue
                }

                val fileName = buildDriveFileName(localClip, hash)
                val appProps = buildDriveAppProperties(localClip, hash)
                val uploadedFile = driveApiClient.uploadTextFile(
                    accessToken = accessToken,
                    folderId = folderId,
                    fileName = fileName,
                    content = trimmedText,
                    appProperties = appProps
                )
                remoteHashToFileMap[hash] = uploadedFile
                repository.updateClipDriveSyncState(
                    clipId = localClip.id,
                    driveFileId = uploadedFile.id,
                    syncState = SYNC_STATE_SYNCED,
                    updatedAt = uploadedFile.modifiedTime
                )
                uploadedCount++
            } else if (localClip.syncState == SYNC_STATE_PENDING_UPDATE) {
                val fileName = buildDriveFileName(localClip, hash)
                val appProps = buildDriveAppProperties(localClip, hash)
                try {
                    val updatedFile = driveApiClient.updateTextFile(
                        accessToken = accessToken,
                        fileId = localClip.driveFileId,
                        fileName = fileName,
                        content = trimmedText,
                        appProperties = appProps
                    )
                    repository.updateClipDriveSyncState(
                        clipId = localClip.id,
                        driveFileId = updatedFile.id,
                        syncState = SYNC_STATE_SYNCED,
                        updatedAt = updatedFile.modifiedTime
                    )
                    updatedCount++
                } catch (_: DriveFileNotFoundException) {
                    val uploadedFile = driveApiClient.uploadTextFile(
                        accessToken = accessToken,
                        folderId = folderId,
                        fileName = fileName,
                        content = trimmedText,
                        appProperties = appProps
                    )
                    repository.updateClipDriveSyncState(
                        clipId = localClip.id,
                        driveFileId = uploadedFile.id,
                        syncState = SYNC_STATE_SYNCED,
                        updatedAt = uploadedFile.modifiedTime
                    )
                    uploadedCount++
                }
            }
        }

        return DriveSyncReport(
            folderId = folderId,
            downloadedCount = downloadedCount,
            uploadedCount = uploadedCount,
            updatedCount = updatedCount,
            deletedRemoteCount = deletedRemoteCount,
            deletedLocalCount = deletedLocalCount
        )
    }

    private fun isSupportedTextFile(remoteFile: DriveRemoteFile): Boolean {
        val mime = remoteFile.mimeType.lowercase(Locale.US)
        val name = remoteFile.name.lowercase(Locale.US)
        return mime == "text/plain" ||
            mime.startsWith("text/") ||
            name.endsWith(".txt") ||
            name.endsWith(".md") ||
            mime == "application/vnd.google-apps.document"
    }

    private fun extractTitleFromRemoteFile(remoteFile: DriveRemoteFile): String {
        val propTitle = remoteFile.appProperties["title"]?.trim().orEmpty()
        if (propTitle.isNotEmpty()) return propTitle

        val rawName = remoteFile.name
            .removeSuffix(".txt")
            .removeSuffix(".TXT")
            .removeSuffix(".md")
            .trim()
        // Strip trailing _<8-char-hex-hash> if generated by Copy Nest
        val cleaned = rawName.replace(Regex("_[0-9a-fA-F]{8}$"), "")
            .replace('_', ' ')
            .trim()
        return if (cleaned.equals("Clip", ignoreCase = true) ||
            cleaned.startsWith("Clip ", ignoreCase = true) && cleaned.length <= 14
        ) {
            ""
        } else {
            cleaned
        }
    }

    companion object {
        const val COPY_NEST_FOLDER_NAME = "Copy Nest"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_FULL_SCOPE = "https://www.googleapis.com/auth/drive"

        const val SYNC_STATE_SYNCED = "SYNCED"
        const val SYNC_STATE_PENDING_UPLOAD = "PENDING_UPLOAD"
        const val SYNC_STATE_PENDING_UPDATE = "PENDING_UPDATE"

        fun buildDriveFileName(clip: ClipEntity, contentHash: String): String {
            val shortHash = contentHash.take(8)
            val baseCandidate = clip.title.ifBlank {
                clip.text.lineSequence().firstOrNull()?.trim().orEmpty().take(28)
            }
            val sanitized = baseCandidate
                .replace(Regex("[^a-zA-Z0-9 _-]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .take(32)
                .ifBlank { "Clip" }
            return "${sanitized}_${shortHash}.txt"
        }

        fun buildDriveAppProperties(clip: ClipEntity, contentHash: String): Map<String, String> {
            return mapOf(
                "contentHash" to contentHash.take(64),
                "category" to clip.category.take(32),
                "contentType" to clip.contentType.take(16),
                "isPinned" to clip.isPinned.toString(),
                "timestamp" to clip.timestamp.toString(),
                "title" to clip.title.take(45)
            )
        }

        fun computeContentFingerprint(text: String): String {
            val trimmed = text.trim()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(trimmed.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun isNetworkAvailable(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Exception) {
                false
            }
        }
    }
}
