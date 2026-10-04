package app.signull.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import app.signull.BuildConfig
import app.signull.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float, val downloadedBytes: Long) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val file: File) : UpdateState
    data class NeedsPermission(val release: AppRelease, val file: File) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val release: AppRelease?, val message: String, val openPage: Boolean = false) : UpdateState
}

/**
 * Checks GitHub Releases for a newer APK, downloads it, verifies it is a newer SigNull signed with
 * the same key, and hands it to Android's package installer.
 */
class Updater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: Version? = Version.parse(BuildConfig.VERSION_NAME)
    private var downloadJob: Job? = null

    /** Latest published release, or null when there is none. Throws on network errors. */
    suspend fun fetchLatest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/${BuildConfig.REPO_SLUG}/releases/latest")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "SigNull/${BuildConfig.VERSION_NAME}")
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> ReleaseParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun isNewer(release: AppRelease): Boolean = currentVersion == null || release.version > currentVersion

    fun check(manual: Boolean = true) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing || current is UpdateState.Checking) return
        scope.launch {
            if (manual) _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest() }
            settings.setLastUpdateCheck(System.currentTimeMillis())
            result
                .onSuccess { release ->
                    _state.value = if (release != null && isNewer(release)) {
                        UpdateState.Available(release)
                    } else {
                        UpdateState.UpToDate(System.currentTimeMillis())
                    }
                }
                .onFailure {
                    _state.value = if (manual) {
                        UpdateState.Failed(null, "Couldn't reach GitHub. Check your connection and try again.")
                    } else {
                        UpdateState.Idle
                    }
                }
        }
    }

    /** Quiet check at most every few hours, when automatic checks are on. */
    fun checkIfDue() {
        scope.launch {
            val prefs = settings.settings.first()
            if (!prefs.autoUpdate) return@launch
            if (System.currentTimeMillis() - prefs.lastUpdateCheck < CHECK_INTERVAL_MS) return@launch
            check(manual = false)
        }
    }

    fun download() {
        val release = (_state.value as? UpdateState.Available)?.release
            ?: (_state.value as? UpdateState.Failed)?.release
            ?: return
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            _state.value = UpdateState.Downloading(release, 0f, 0)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "SigNull-${release.tag}.apk")
            try {
                val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "SigNull/${BuildConfig.VERSION_NAME}")
                try {
                    if (connection.responseCode !in 200..299) throw IOException("Download failed (${connection.responseCode})")
                    val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPublish = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                val now = System.currentTimeMillis()
                                if (now - lastPublish > 80) {
                                    lastPublish = now
                                    _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f), done)
                                }
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                _state.value = verify(release, target)
            } catch (e: IOException) {
                target.delete()
                _state.value = UpdateState.Failed(release, "The download stopped: ${e.message ?: "network error"}. Try again.")
            }
        }
    }

    fun cancelDownload() {
        val release = (_state.value as? UpdateState.Downloading)?.release ?: return
        downloadJob?.cancel()
        _state.value = UpdateState.Available(release)
    }

    /** Settings screen where the user allows SigNull to install updates. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install() {
        val (release, file) = when (val s = _state.value) {
            is UpdateState.ReadyToInstall -> s.release to s.file
            is UpdateState.NeedsPermission -> s.release to s.file
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release, file)
            return
        }
        _state.value = UpdateState.Installing(release)
        scope.launch(Dispatchers.IO) {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(release, "Couldn't start the installer: ${e.message ?: "unknown error"}.")
            }
        }
    }

    /** Called by [InstallResultReceiver] when Android reports how the install went. */
    fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate(System.currentTimeMillis())
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { r ->
                val file = File(File(context.cacheDir, "updates"), "SigNull-${r.tag}.apk")
                if (file.exists()) UpdateState.ReadyToInstall(r, file) else UpdateState.Available(r)
            } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateState.Failed(
                release,
                SIGNATURE_MESSAGE,
                openPage = true,
            )
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed(release, "Not enough storage to install the update.")
            else -> UpdateState.Failed(release, "Android couldn't install the update${message?.let { ": $it" } ?: "."}")
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun verify(release: AppRelease, file: File): UpdateState {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return UpdateState.Failed(release, "The download is damaged. Try again.")
        if (archive.packageName != context.packageName) {
            return UpdateState.Failed(release, "That file isn't a SigNull? update.", openPage = true)
        }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return UpdateState.Failed(release, "This download isn't newer than the app you have.")
        }
        val theirs = signers(archive.signingInfo)
        val mine = signers(installed.signingInfo)
        if (theirs.isEmpty() || theirs.none { it in mine }) {
            return UpdateState.Failed(release, SIGNATURE_MESSAGE, openPage = true)
        }
        return UpdateState.ReadyToInstall(release, file)
    }

    private fun signers(info: android.content.pm.SigningInfo?): List<Signature> = when {
        info == null -> emptyList()
        info.hasMultipleSigners() -> info.apkContentsSigners.toList()
        else -> info.signingCertificateHistory.toList()
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val SIGNATURE_MESSAGE =
            "This update is signed with a different key than the app on your phone. Uninstall SigNull? once, " +
                "then install the update from GitHub. Later updates install right here."
    }
}
