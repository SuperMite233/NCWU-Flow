package cn.edu.ncwu.flow.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.edu.ncwu.flow.MainActivity
import cn.edu.ncwu.flow.R
import cn.edu.ncwu.flow.data.CampusRepository
import cn.edu.ncwu.flow.model.LoadResult
import java.util.concurrent.TimeUnit

enum class ScanCategory { ALL, ACTIVITIES_ONLY, COMPETITIONS_ONLY }

data class BackgroundSettings(
    val enabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val intervalMinutes: Long = 60,
    val category: ScanCategory = ScanCategory.ALL,
)

object BackgroundScanScheduler {
    private const val UNIQUE_WORK = "campus-information-scan"

    fun apply(context: Context, settings: BackgroundSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CampusScanWorker>(
            settings.intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(
                androidx.work.workDataOf(
                    "notifications" to settings.notificationsEnabled,
                    "category" to settings.category.name,
                ),
            )
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class CampusScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = CampusRepository(appContext)
    private val baseline = appContext.getSharedPreferences("scan_baseline", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val category = runCatching {
            ScanCategory.valueOf(inputData.getString("category") ?: ScanCategory.ALL.name)
        }.getOrDefault(ScanCategory.ALL)
        val notifications = inputData.getBoolean("notifications", true)
        val newTitles = mutableListOf<String>()

        if (category != ScanCategory.COMPETITIONS_ONLY) {
            when (val result = repository.loadActivities()) {
                is LoadResult.Success -> newTitles += findNew(
                    key = "activity_ids",
                    current = result.data.associate { it.id to it.name },
                )
                is LoadResult.Error -> if (result.message.contains("密码错误")) notifyAuthenticationProblem(result.message)
                else -> Unit
            }
        }
        if (category != ScanCategory.ACTIVITIES_ONLY) {
            when (val result = repository.loadCompetitions()) {
                is LoadResult.Success -> newTitles += findNew(
                    key = "competition_ids",
                    current = result.data
                        .filter { it.state.contains("进行") }
                        .associate { "${it.id}:${it.name}" to it.name },
                )
                is LoadResult.Error -> if (result.message.contains("密码错误")) notifyAuthenticationProblem(result.message)
                else -> Unit
            }
        }

        if (notifications && newTitles.isNotEmpty()) notifyNewItems(newTitles)
        return Result.success()
    }

    private fun findNew(key: String, current: Map<String, String>): List<String> {
        val previous = baseline.getStringSet(key, null)
        baseline.edit { putStringSet(key, current.keys) }
        if (previous == null) return emptyList()
        return (current.keys - previous).mapNotNull(current::get)
    }

    private fun notifyNewItems(titles: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "校园信息更新",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "第二课堂活动与竞赛新增提醒" },
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val preview = titles.take(3).joinToString("、")
        val content = if (titles.size > 3) "$preview 等 ${titles.size} 条" else preview
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("发现新的校园信息")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun notifyAuthenticationProblem(message: String) {
        if (!inputData.getBoolean("notifications", true)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "校园信息更新", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            AUTH_NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("校园网站自动登录失败")
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_ID = "campus_updates"
        private const val NOTIFICATION_ID = 1201
        private const val AUTH_NOTIFICATION_ID = 1202
    }
}
