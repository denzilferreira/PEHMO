package fi.oulu.ubicomp.extrema.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import java.util.*

class SurveyWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {

        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) != 21) return Result.success()

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY,0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()
        val surveysToday = db.surveyDao().getToday(today.timeInMillis)
        if (surveysToday.isNotEmpty()) return Result.success()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.app_name)
            val descriptionText = applicationContext.getString(R.string.app_name)
            val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val surveyIntent = Intent(applicationContext, ViewSurvey::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingSurvey = PendingIntent.getActivity(applicationContext, 0, surveyIntent, 0)

        val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                .setSmallIcon(R.drawable.ic_stat_extrema_survey)
                .setContentTitle(applicationContext.getString(R.string.survey_notification_title))
                .setContentText(applicationContext.getString(R.string.survey_notification_text))
                .setContentIntent(pendingSurvey)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        return Result.success()
    }
}