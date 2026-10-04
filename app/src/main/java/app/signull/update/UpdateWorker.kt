package app.signull.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.signull.MainActivity
import app.signull.R
import app.signull.SigNullApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Background check for new releases. Posts one notification per new version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SigNullApplication).container
        val release = runCatching { container.updater.fetchLatest() }.getOrElse { return Result.retry() }
            ?: return Result.success()
        container.settings.setLastUpdateCheck(System.currentTimeMillis())
        if (!container.updater.isNewer(release)) return Result.success()
        val prefs = container.settings.settings.first()
        if (prefs.notifiedVersion == release.tag) return Result.success()
        if (UpdateNotifications.show(applicationContext, release)) container.settings.setNotifiedVersion(release.tag)
        return Result.success()
    }
}

object UpdateScheduler {
    private const val WORK_NAME = "signull-update-check"

    fun apply(context: Context, enabled: Boolean) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

object UpdateNotifications {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 4101
    const val EXTRA_OPEN_UPDATE = "app.signull.OPEN_UPDATE"

    /** Returns false when notifications are off or not permitted. */
    fun show(context: Context, release: AppRelease): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        if (!granted || !manager.areNotificationsEnabled()) return false
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a new SigNull? version is on GitHub"
            },
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_signull)
            .setContentTitle("SigNull? ${release.version} is available")
            .setContentText("Tap to download and install it.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
    }
}
