package fi.oulu.ubicomp.extrema.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.views.ViewSurvey

class SurveyWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    val EXTREMA_SURVEY = 123456

    override fun doWork(): Result {
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

        var builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                .setSmallIcon(R.drawable.ic_stat_extrema_survey)
                .setContentTitle(applicationContext.getString(R.string.survey_notification_title))
                .setContentText(applicationContext.getString(R.string.survey_notification_text))
                .setContentIntent(pendingSurvey)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(EXTREMA_SURVEY, builder.build())

        return Result.success()
    }
}