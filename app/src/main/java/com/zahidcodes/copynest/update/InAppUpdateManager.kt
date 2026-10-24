package com.zahidcodes.copynest.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.zahidcodes.copynest.BuildConfig
import com.zahidcodes.copynest.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Classification of an update requirement evaluated by [InAppUpdatePolicy].
 */
enum class UpdateRequirement {
    NONE,
    OPTIONAL,
    MANDATORY
}

/**
 * Reason why access to the main application is blocked by a mandatory update.
 */
enum class UpdateBlockReason {
    USER_CANCELLED,
    UPDATE_FAILED,
    PLAY_STORE_UNAVAILABLE,
    NETWORK_OR_API_ERROR
}

/**
 * Maintainable policy configuration for determining whether an update is mandatory, optional, or not required.
 */
data class UpdatePolicyConfig(
    val minSupportedVersionCode: Int = BuildConfig.VERSION_CODE,
    val mandatoryPriorityThreshold: Int = 4,
    val mandatoryStalenessDaysThreshold: Int? = null,
    val enforceImmediateForAllAvailablePlayUpdates: Boolean = true
)

/**
 * Platform-independent snapshot of Google Play's [AppUpdateInfo] state.
 */
data class AppUpdateSnapshot(
    val updateAvailability: Int,
    val installStatus: Int = InstallStatus.UNKNOWN,
    val availableVersionCode: Int = 0,
    val updatePriority: Int = 0,
    val clientVersionStalenessDays: Int? = null,
    val isImmediateAllowed: Boolean = false,
    val isFlexibleAllowed: Boolean = false,
    val bytesDownloaded: Long = 0L,
    val totalBytesToDownload: Long = 0L,
    val rawAppUpdateInfo: AppUpdateInfo? = null
)

/**
 * UI state rendered on the custom SplashScreen during update checks and mandatory update enforcement.
 */
sealed class SplashUpdateUiState {
    data class Checking(
        val message: String = ""
    ) : SplashUpdateUiState()

    data class PreparingImmediateUpdate(
        val availableVersionCode: Int,
        val message: String = "Preparing update..."
    ) : SplashUpdateUiState()

    data class ImmediateUpdateInProgress(
        val availableVersionCode: Int,
        val bytesDownloaded: Long = 0L,
        val totalBytesToDownload: Long = 0L,
        val statusMessage: String = "Downloading required update..."
    ) : SplashUpdateUiState()

    data class UpdateDownloadedRestartRequired(
        val availableVersionCode: Int,
        val message: String = "Update downloaded. Restart required to complete installation."
    ) : SplashUpdateUiState()

    data class UpdateRequiredBlocked(
        val reason: UpdateBlockReason,
        val title: String,
        val message: String,
        val availableVersionCode: Int? = null,
        val errorCode: Int? = null,
        val canRetryInApp: Boolean = true,
        val showOpenPlayStore: Boolean = true
    ) : SplashUpdateUiState()

    data class OptionalUpdatePrompt(
        val availableVersionCode: Int,
        val message: String = "A new version of CopyNest is available on Google Play."
    ) : SplashUpdateUiState()

    data object AllowedToProceed : SplashUpdateUiState()
}

/**
 * Evaluates whether an update is mandatory, optional, or not needed based on Google Play update info
 * and minimum version policy.
 */
object InAppUpdatePolicy {
    fun evaluate(
        installedVersionCode: Int,
        pendingMandatoryVersionCode: Int,
        policyConfig: UpdatePolicyConfig,
        snapshot: AppUpdateSnapshot?
    ): UpdateRequirement {
        val effectiveMinVersion = maxOf(policyConfig.minSupportedVersionCode, pendingMandatoryVersionCode)
        val isBelowMinSupportedVersion = installedVersionCode < effectiveMinVersion

        if (snapshot == null) {
            return if (isBelowMinSupportedVersion) {
                UpdateRequirement.MANDATORY
            } else {
                UpdateRequirement.NONE
            }
        }

        if (snapshot.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            return UpdateRequirement.MANDATORY
        }

        if (snapshot.installStatus == InstallStatus.DOWNLOADING ||
            snapshot.installStatus == InstallStatus.INSTALLING ||
            snapshot.installStatus == InstallStatus.PENDING ||
            snapshot.installStatus == InstallStatus.DOWNLOADED
        ) {
            return UpdateRequirement.MANDATORY
        }

        if (snapshot.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE &&
            snapshot.availableVersionCode > installedVersionCode
        ) {
            val priorityRequiresMandatory = snapshot.updatePriority >= policyConfig.mandatoryPriorityThreshold
            val stalenessRequiresMandatory = policyConfig.mandatoryStalenessDaysThreshold != null &&
                (snapshot.clientVersionStalenessDays ?: 0) >= policyConfig.mandatoryStalenessDaysThreshold
            val immediatePolicyRequiresMandatory =
                policyConfig.enforceImmediateForAllAvailablePlayUpdates && snapshot.isImmediateAllowed

            return if (
                isBelowMinSupportedVersion ||
                priorityRequiresMandatory ||
                stalenessRequiresMandatory ||
                immediatePolicyRequiresMandatory
            ) {
                UpdateRequirement.MANDATORY
            } else {
                UpdateRequirement.OPTIONAL
            }
        }

        return if (isBelowMinSupportedVersion) {
            UpdateRequirement.MANDATORY
        } else {
            UpdateRequirement.NONE
        }
    }
}

/**
 * Abstraction over Google Play's [AppUpdateManager] to allow deterministic testing of all Play states.
 */
interface PlayInAppUpdateDataSource {
    suspend fun fetchAppUpdateSnapshot(): AppUpdateSnapshot
    fun startImmediateUpdate(
        snapshot: AppUpdateSnapshot,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ): Boolean
    suspend fun completeUpdate(): Boolean
    fun registerInstallStateListener(listener: (InstallStateEvent) -> Unit)
    fun unregisterInstallStateListener()
}

data class InstallStateEvent(
    val installStatus: Int,
    val bytesDownloaded: Long = 0L,
    val totalBytesToDownload: Long = 0L,
    val installErrorCode: Int = InstallErrorCode.NO_ERROR
)

/**
 * Production implementation backed by the official Google Play In-App Updates API ([AppUpdateManager]).
 */
class GooglePlayInAppUpdateDataSource(
    context: Context
) : PlayInAppUpdateDataSource {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)

    private var activeListener: InstallStateUpdatedListener? = null

    override suspend fun fetchAppUpdateSnapshot(): AppUpdateSnapshot =
        suspendCancellableCoroutine { continuation ->
            try {
                val task = appUpdateManager.appUpdateInfo
                task.addOnSuccessListener { info ->
                    if (continuation.isActive) {
                        continuation.resume(
                            AppUpdateSnapshot(
                                updateAvailability = info.updateAvailability(),
                                installStatus = info.installStatus(),
                                availableVersionCode = info.availableVersionCode(),
                                updatePriority = info.updatePriority(),
                                clientVersionStalenessDays = info.clientVersionStalenessDays(),
                                isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                                isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                                bytesDownloaded = info.bytesDownloaded(),
                                totalBytesToDownload = info.totalBytesToDownload(),
                                rawAppUpdateInfo = info
                            )
                        )
                    }
                }
                task.addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
                task.addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Google Play appUpdateInfo task was canceled")
                        )
                    }
                }
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        }

    override fun startImmediateUpdate(
        snapshot: AppUpdateSnapshot,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ): Boolean {
        val rawInfo = snapshot.rawAppUpdateInfo ?: return false
        val activityLauncher = launcher ?: return false
        val updateType = when {
            snapshot.isImmediateAllowed -> AppUpdateType.IMMEDIATE
            snapshot.isFlexibleAllowed -> AppUpdateType.FLEXIBLE
            else -> return false
        }

        return try {
            val options = AppUpdateOptions.newBuilder(updateType)
                .setAllowAssetPackDeletion(false)
                .build()
            appUpdateManager.startUpdateFlowForResult(
                rawInfo,
                activityLauncher,
                options
            )
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun completeUpdate(): Boolean =
        suspendCancellableCoroutine { continuation ->
            try {
                appUpdateManager.completeUpdate()
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(false)
                    }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
        }

    override fun registerInstallStateListener(listener: (InstallStateEvent) -> Unit) {
        unregisterInstallStateListener()
        val playListener = InstallStateUpdatedListener { state ->
            listener(
                InstallStateEvent(
                    installStatus = state.installStatus(),
                    bytesDownloaded = state.bytesDownloaded(),
                    totalBytesToDownload = state.totalBytesToDownload(),
                    installErrorCode = state.installErrorCode()
                )
            )
        }
        activeListener = playListener
        try {
            appUpdateManager.registerListener(playListener)
        } catch (_: Exception) {
            // Ignore registration errors on unsupported devices
        }
    }

    override fun unregisterInstallStateListener() {
        val listener = activeListener ?: return
        activeListener = null
        try {
            appUpdateManager.unregisterListener(listener)
        } catch (_: Exception) {
            // Ignore unregistration errors
        }
    }
}

/**
 * Coordinates the Google Play In-App Update check, Immediate Update flow, lifecycle resumption,
 * and mandatory force-update gating under the custom SplashScreen.
 */
class InAppUpdateManager(
    private val context: Context,
    private val userPreferences: UserPreferences = UserPreferences(context.applicationContext),
    private val dataSource: PlayInAppUpdateDataSource = GooglePlayInAppUpdateDataSource(context.applicationContext),
    private val initialPolicyConfig: UpdatePolicyConfig = UpdatePolicyConfig(),
    private val updateCheckTimeoutMs: Long = 1500L,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val _uiState = MutableStateFlow<SplashUpdateUiState>(SplashUpdateUiState.Checking())
    val uiState: StateFlow<SplashUpdateUiState> = _uiState.asStateFlow()

    private var policyConfig: UpdatePolicyConfig = initialPolicyConfig
    private var lastSnapshot: AppUpdateSnapshot? = null
    private var isImmediateFlowActive: Boolean = false
    private var isCheckInFlight: Boolean = false
    private var hasUserCancelledCurrentSession: Boolean = false

    val installedVersionCode: Int
        get() = try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE
        }

    init {
        dataSource.registerInstallStateListener { event ->
            handleInstallStateEvent(event)
        }
    }

    fun updatePolicyConfig(newConfig: UpdatePolicyConfig) {
        policyConfig = newConfig
    }

    /**
     * Checks Google Play for update availability and initiates the Immediate Update flow if mandatory.
     */
    fun checkForUpdates(
        launcher: ActivityResultLauncher<IntentSenderRequest>?,
        isUserInitiatedRetry: Boolean = false
    ) {
        if (isImmediateFlowActive && !isUserInitiatedRetry) return
        if (isCheckInFlight && !isUserInitiatedRetry) return

        if (isUserInitiatedRetry) {
            hasUserCancelledCurrentSession = false
            isImmediateFlowActive = false
        }

        isCheckInFlight = true
        if (_uiState.value !is SplashUpdateUiState.AllowedToProceed &&
            _uiState.value !is SplashUpdateUiState.ImmediateUpdateInProgress &&
            _uiState.value !is SplashUpdateUiState.UpdateDownloadedRestartRequired
        ) {
            _uiState.value = SplashUpdateUiState.Checking()
        }

        scope.launch {
            try {
                val storedMinVersion = userPreferences.getMinSupportedVersionCode()
                val effectivePolicy = if (storedMinVersion > policyConfig.minSupportedVersionCode) {
                    policyConfig.copy(minSupportedVersionCode = storedMinVersion)
                } else {
                    policyConfig
                }
                val currentVersion = installedVersionCode
                var pendingMandatoryVersion = userPreferences.getPendingMandatoryUpdateVersion()

                // Clear any stale pending mandatory version if the installed app has already been updated
                if (pendingMandatoryVersion > 0 && currentVersion >= pendingMandatoryVersion) {
                    userPreferences.setPendingMandatoryUpdateVersion(0)
                    pendingMandatoryVersion = 0
                }

                val snapshot = withTimeoutOrNull(updateCheckTimeoutMs) {
                    dataSource.fetchAppUpdateSnapshot()
                } ?: throw TimeoutException("Google Play In-App Update check timed out")
                lastSnapshot = snapshot

                val requirement = InAppUpdatePolicy.evaluate(
                    installedVersionCode = currentVersion,
                    pendingMandatoryVersionCode = pendingMandatoryVersion,
                    policyConfig = effectivePolicy,
                    snapshot = snapshot
                )

                when (requirement) {
                    UpdateRequirement.NONE -> {
                        if (pendingMandatoryVersion > 0 && currentVersion >= pendingMandatoryVersion) {
                            userPreferences.setPendingMandatoryUpdateVersion(0)
                        }
                        _uiState.value = SplashUpdateUiState.AllowedToProceed
                    }

                    UpdateRequirement.OPTIONAL -> {
                        if ((snapshot.isImmediateAllowed || snapshot.isFlexibleAllowed) && isUserInitiatedRetry) {
                            launchImmediateUpdateFlow(snapshot, launcher)
                        } else {
                            _uiState.value = SplashUpdateUiState.OptionalUpdatePrompt(
                                availableVersionCode = snapshot.availableVersionCode
                            )
                        }
                    }

                    UpdateRequirement.MANDATORY -> {
                        val targetVersion = maxOf(
                            snapshot.availableVersionCode,
                            effectivePolicy.minSupportedVersionCode,
                            pendingMandatoryVersion
                        )
                        if (targetVersion > currentVersion) {
                            userPreferences.setPendingMandatoryUpdateVersion(targetVersion)
                        }

                        // Handle downloaded state requiring restart
                        if (snapshot.installStatus == InstallStatus.DOWNLOADED) {
                            _uiState.value = SplashUpdateUiState.UpdateDownloadedRestartRequired(
                                availableVersionCode = targetVersion
                            )
                            dataSource.completeUpdate()
                            return@launch
                        }

                        // Handle active download/installation state
                        if (snapshot.installStatus == InstallStatus.DOWNLOADING ||
                            snapshot.installStatus == InstallStatus.INSTALLING ||
                            snapshot.installStatus == InstallStatus.PENDING
                        ) {
                            _uiState.value = SplashUpdateUiState.ImmediateUpdateInProgress(
                                availableVersionCode = targetVersion,
                                bytesDownloaded = snapshot.bytesDownloaded,
                                totalBytesToDownload = snapshot.totalBytesToDownload,
                                statusMessage = if (snapshot.installStatus == InstallStatus.INSTALLING) {
                                    "Installing required update..."
                                } else {
                                    "Downloading required update..."
                                }
                            )
                            if (snapshot.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                                (snapshot.isImmediateAllowed || snapshot.isFlexibleAllowed) &&
                                !isImmediateFlowActive
                            ) {
                                launchImmediateUpdateFlow(snapshot, launcher)
                            }
                            return@launch
                        }

                        // If user already cancelled during this session and this is an automatic lifecycle check
                        // (not a user-initiated retry and not DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS),
                        // keep showing the blocked recovery screen instead of infinite-looping the Play sheet.
                        if (hasUserCancelledCurrentSession &&
                            !isUserInitiatedRetry &&
                            snapshot.updateAvailability != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                        ) {
                            _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                                reason = UpdateBlockReason.USER_CANCELLED,
                                title = "Update Required",
                                message = "A newer version of CopyNest is required to continue. Please complete the update to access the app.",
                                availableVersionCode = targetVersion,
                                canRetryInApp = snapshot.isImmediateAllowed || snapshot.isFlexibleAllowed,
                                showOpenPlayStore = true
                            )
                            return@launch
                        }

                        val isAvailableOnPlay =
                            (snapshot.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                                snapshot.updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                                (snapshot.isImmediateAllowed || snapshot.isFlexibleAllowed)

                        if (isAvailableOnPlay) {
                            launchImmediateUpdateFlow(snapshot, launcher)
                        } else {
                            // A mandatory update is required by policy, but Play Store immediate flow is not eligible/available
                            _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                                reason = UpdateBlockReason.PLAY_STORE_UNAVAILABLE,
                                title = "Update Required",
                                message = "This version of CopyNest is no longer supported. Please update to the latest version on Google Play to continue.",
                                availableVersionCode = targetVersion.takeIf { it > 0 },
                                canRetryInApp = true,
                                showOpenPlayStore = true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                handleUpdateCheckException(e)
            } finally {
                isCheckInFlight = false
            }
        }
    }

    private fun launchImmediateUpdateFlow(
        snapshot: AppUpdateSnapshot,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ) {
        if (isImmediateFlowActive) return
        _uiState.value = SplashUpdateUiState.PreparingImmediateUpdate(
            availableVersionCode = snapshot.availableVersionCode,
            message = "Preparing update..."
        )
        isImmediateFlowActive = true
        val started = dataSource.startImmediateUpdate(snapshot, launcher)
        if (!started) {
            isImmediateFlowActive = false
            _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                reason = UpdateBlockReason.UPDATE_FAILED,
                title = "Update Required",
                message = "Unable to launch the Google Play update screen. Please retry or update directly from the Google Play Store.",
                availableVersionCode = snapshot.availableVersionCode.takeIf { it > 0 },
                canRetryInApp = true,
                showOpenPlayStore = true
            )
        }
    }

    private suspend fun handleUpdateCheckException(exception: Exception) {
        val currentVersion = installedVersionCode
        val storedMinVersion = userPreferences.getMinSupportedVersionCode()
        val pendingMandatoryVersion = userPreferences.getPendingMandatoryUpdateVersion()
        val effectiveMinVersion = maxOf(
            policyConfig.minSupportedVersionCode,
            storedMinVersion,
            pendingMandatoryVersion
        )
        val isMandatoryRequired = currentVersion < effectiveMinVersion
        val installErrorCode = (exception as? InstallException)?.errorCode

        if (isMandatoryRequired) {
            val isPlayUnavailableError = installErrorCode == InstallErrorCode.ERROR_API_NOT_AVAILABLE ||
                installErrorCode == InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND ||
                installErrorCode == InstallErrorCode.ERROR_APP_NOT_OWNED

            _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                reason = if (isPlayUnavailableError) {
                    UpdateBlockReason.PLAY_STORE_UNAVAILABLE
                } else {
                    UpdateBlockReason.NETWORK_OR_API_ERROR
                },
                title = "Update Required",
                message = if (isPlayUnavailableError) {
                    "A mandatory update is required to use CopyNest, but Google Play In-App Updates is unavailable. Please update via Google Play Store."
                } else {
                    "Unable to verify or complete the required update. Please check your internet connection and try again."
                },
                availableVersionCode = effectiveMinVersion.takeIf { it > 0 },
                errorCode = installErrorCode,
                canRetryInApp = true,
                showOpenPlayStore = true
            )
        } else {
            // No mandatory update is flagged and the installed version meets the minimum supported version
            _uiState.value = SplashUpdateUiState.AllowedToProceed
        }
    }

    /**
     * Handles the result returned from Google Play's Immediate Update Activity.
     */
    fun onUpdateActivityResult(
        resultCode: Int,
        launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    ) {
        isImmediateFlowActive = false
        val targetVersion = lastSnapshot?.availableVersionCode?.takeIf { it > 0 }

        when (resultCode) {
            Activity.RESULT_OK -> {
                hasUserCancelledCurrentSession = false
                // Re-verify update state on completion
                checkForUpdates(launcher = launcher, isUserInitiatedRetry = false)
            }

            Activity.RESULT_CANCELED -> {
                hasUserCancelledCurrentSession = true
                _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                    reason = UpdateBlockReason.USER_CANCELLED,
                    title = "Update Required to Continue",
                    message = "You cancelled the mandatory update. CopyNest requires the latest update to continue safely.",
                    availableVersionCode = targetVersion,
                    canRetryInApp = true,
                    showOpenPlayStore = true
                )
            }

            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                    reason = UpdateBlockReason.UPDATE_FAILED,
                    title = "Update Failed",
                    message = "The mandatory update could not be completed. Please retry the update or install it from Google Play.",
                    availableVersionCode = targetVersion,
                    canRetryInApp = true,
                    showOpenPlayStore = true
                )
            }

            else -> {
                _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                    reason = UpdateBlockReason.UPDATE_FAILED,
                    title = "Update Incomplete",
                    message = "Please complete the required update to access CopyNest.",
                    availableVersionCode = targetVersion,
                    canRetryInApp = true,
                    showOpenPlayStore = true
                )
            }
        }
    }

    /**
     * Called when the Activity resumes from the background or Play update overlay.
     * Re-checks update status and resumes any interrupted immediate update without launching duplicates.
     */
    fun onResume(launcher: ActivityResultLauncher<IntentSenderRequest>?) {
        if (isImmediateFlowActive || isCheckInFlight) return
        checkForUpdates(launcher = launcher, isUserInitiatedRetry = false)
    }

    /**
     * Triggered when the user taps "Update Now" / "Retry Update" on the blocked splash state.
     */
    fun retryUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>?) {
        checkForUpdates(launcher = launcher, isUserInitiatedRetry = true)
    }

    /**
     * Completes an update that has reached [InstallStatus.DOWNLOADED].
     */
    fun completeDownloadedUpdate() {
        scope.launch {
            dataSource.completeUpdate()
        }
    }

    /**
     * Allows skipping an update ONLY if the policy classified it as [UpdateRequirement.OPTIONAL].
     */
    fun dismissOptionalUpdate() {
        if (_uiState.value is SplashUpdateUiState.OptionalUpdatePrompt) {
            _uiState.value = SplashUpdateUiState.AllowedToProceed
        }
    }

    /**
     * Opens the official Google Play Store listing for CopyNest as a fallback when in-app update is unavailable.
     */
    fun openPlayStoreListing(activityContext: Context) {
        val packageName = activityContext.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activityContext.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                activityContext.startActivity(webIntent)
            } catch (_: Exception) {
                // Ignore if no browser is installed
            }
        }
    }

    private fun handleInstallStateEvent(event: InstallStateEvent) {
        val targetVersion = lastSnapshot?.availableVersionCode ?: 0
        when (event.installStatus) {
            InstallStatus.PENDING -> {
                _uiState.value = SplashUpdateUiState.ImmediateUpdateInProgress(
                    availableVersionCode = targetVersion,
                    bytesDownloaded = 0L,
                    totalBytesToDownload = event.totalBytesToDownload,
                    statusMessage = "Preparing download..."
                )
            }

            InstallStatus.DOWNLOADING -> {
                _uiState.value = SplashUpdateUiState.ImmediateUpdateInProgress(
                    availableVersionCode = targetVersion,
                    bytesDownloaded = event.bytesDownloaded,
                    totalBytesToDownload = event.totalBytesToDownload,
                    statusMessage = "Downloading required update..."
                )
            }

            InstallStatus.DOWNLOADED -> {
                isImmediateFlowActive = false
                _uiState.value = SplashUpdateUiState.UpdateDownloadedRestartRequired(
                    availableVersionCode = targetVersion
                )
                scope.launch {
                    dataSource.completeUpdate()
                }
            }

            InstallStatus.INSTALLING -> {
                _uiState.value = SplashUpdateUiState.ImmediateUpdateInProgress(
                    availableVersionCode = targetVersion,
                    bytesDownloaded = event.bytesDownloaded,
                    totalBytesToDownload = event.totalBytesToDownload,
                    statusMessage = "Installing update..."
                )
            }

            InstallStatus.INSTALLED -> {
                isImmediateFlowActive = false
                hasUserCancelledCurrentSession = false
                scope.launch {
                    userPreferences.setPendingMandatoryUpdateVersion(0)
                    _uiState.value = SplashUpdateUiState.AllowedToProceed
                }
            }

            InstallStatus.CANCELED -> {
                isImmediateFlowActive = false
                hasUserCancelledCurrentSession = true
                _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                    reason = UpdateBlockReason.USER_CANCELLED,
                    title = "Update Required to Continue",
                    message = "You cancelled the mandatory update. CopyNest requires the latest update to continue.",
                    availableVersionCode = targetVersion.takeIf { it > 0 },
                    canRetryInApp = true,
                    showOpenPlayStore = true
                )
            }

            InstallStatus.FAILED -> {
                isImmediateFlowActive = false
                _uiState.value = SplashUpdateUiState.UpdateRequiredBlocked(
                    reason = UpdateBlockReason.UPDATE_FAILED,
                    title = "Update Failed",
                    message = "The update installation failed. Please retry or update from Google Play Store.",
                    availableVersionCode = targetVersion.takeIf { it > 0 },
                    errorCode = event.installErrorCode,
                    canRetryInApp = true,
                    showOpenPlayStore = true
                )
            }

            else -> Unit
        }
    }

    fun onCleared() {
        dataSource.unregisterInstallStateListener()
    }
}
