package fi.oulu.ubicomp.extrema.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import fi.oulu.ubicomp.extrema.workers.*
import org.jetbrains.anko.doAsync
import java.util.concurrent.TimeUnit

class Pehmo : Service() {

    companion object {
        val PEHMO_FOREGROUND = 110411
    }

    override fun onCreate() {
        super.onCreate()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.app_name)
            val descriptionText = applicationContext.getString(R.string.app_name)
            val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        val foregroundIntent = PendingIntent.getActivity(applicationContext, 0,
                Intent(applicationContext, ViewSurvey::class.java), 0)

        val notification = NotificationCompat.Builder(applicationContext, "EXTREMA")
        notification.setSmallIcon(R.drawable.ic_stat_pehmo_foreground)
        notification.setOngoing(true)
        notification.setOnlyAlertOnce(true)
        notification.setContentIntent(foregroundIntent)
        notification.setPriority(NotificationCompat.PRIORITY_MIN)
        notification.setContentTitle(getString(R.string.app_name))
        notification.setContentText(getString(R.string.pehmo_foreground))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            notification.setChannelId("EXTREMA")

        startForeground(PEHMO_FOREGROUND, notification.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        doAsync {
            val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                    .addMigrations(Home.MIGRATION_1_2, Home.MIGRATION_2_3)
                    .build()

            val participantData = db.participantDao().getParticipant().first()

            val locationTracking = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES).build() //Set location logging every 15 minutes
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("LOCATION_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, locationTracking)

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && !participantData.ruuviTag.isNullOrBlank()) { //Set bluetooth scanning if available or makes sense
                val bluetoothTracking = PeriodicWorkRequestBuilder<BluetoothWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("BLUETOOTH_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, bluetoothTracking)
            }

            val batteryMonitor = PeriodicWorkRequestBuilder<BatteryWorker>(15, TimeUnit.MINUTES).build() //check battery every 15 minutes
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("BATTERY_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, batteryMonitor)

            val surveyReminder = PeriodicWorkRequestBuilder<SurveyWorker>(15, TimeUnit.MINUTES).build() //check every 15 minutes if it's a good time to show the survey
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("SURVEY_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, surveyReminder)

            val dataSync = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build() //Set data sync to server every 15 minutes
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("SYNC_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, dataSync)

            db.close()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}