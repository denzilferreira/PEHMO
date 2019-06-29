package fi.oulu.ubicomp.extrema.services

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.koushikdutta.async.future.FutureCallback
import com.koushikdutta.ion.Ion
import fi.oulu.ubicomp.extrema.R
import java.io.File
import kotlin.random.Random

class Updater : IntentService("Updater") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {

            val fullPath = intent.getStringExtra("fullPath")
            val changes = intent.getStringExtra("changes")

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = applicationContext.getString(R.string.app_name)
                val descriptionText = applicationContext.getString(R.string.app_name)
                val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                    .setSmallIcon(R.drawable.ic_stat_extrema_update)
                    .setContentTitle(applicationContext.getString(R.string.update_title))
                    .setContentText(changes)
                    .setAutoCancel(true)
                    .setProgress(0, 0, true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

            val notId = Random(System.currentTimeMillis()).nextInt()

            notificationManager.notify(notId, builder.build())

            Ion.getDefault(applicationContext).conscryptMiddleware.enable(false)
            Ion.with(applicationContext).load(fullPath).noCache()
                    .write(File(applicationContext.filesDir, "extrema-cerh.apk"))
                    .setCallback(object : FutureCallback<File> {
                        override fun onCompleted(e: Exception?, result: File?) {
                            if (e != null) {
                                println("Error downloading: ${e.message}")
                            }
                            if (result != null) {

                                println("Downloaded: ${result.canonicalPath}")

                                notificationManager.cancel(notId)

                                val promptInstall = Intent(Intent.ACTION_VIEW)
                                promptInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                promptInstall.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    promptInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    promptInstall.setDataAndType(
                                            FileProvider.getUriForFile(applicationContext, packageName + ".provider.storage", result),
                                            "application/vnd.android.package-archive")
                                } else {
                                    promptInstall.setDataAndType(Uri.fromFile(result), "application/vnd.android.package-archive")
                                }

                                applicationContext.startActivity(promptInstall)
                            }
                        }
                    })
        }
    }
}