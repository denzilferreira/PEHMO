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
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import fi.oulu.ubicomp.extrema.BuildConfig
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.services.Updater
import org.json.JSONObject

class UpdateWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val requestQueue = Volley.newRequestQueue(applicationContext)
        val serverRequest = object : JsonObjectRequest(Method.GET, "http://jenkins.awareframework.com/job/extrema/lastSuccessfulBuild/api/json", null,
                Response.Listener<JSONObject> {
                    if (it.getInt("id") > BuildConfig.VERSION_CODE) {
                        val relativePath = it.getJSONArray("artifacts").getJSONObject(0).getString("relativePath")
                        val changes = it.getJSONObject("changeSet").getJSONArray("items")
                        var changesStr = ""
                        if (changes.length() > 0) changesStr = changes.getJSONObject(0).getString("msg")

                        val fullPath = "http://jenkins.awareframework.com/job/extrema/lastSuccessfulBuild/artifact/$relativePath"

                        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val name = applicationContext.getString(R.string.app_name)
                            val descriptionText = applicationContext.getString(R.string.app_name)
                            val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                                description = descriptionText
                            }
                            notificationManager.createNotificationChannel(channel)
                        }

                        val updaterIntent = Intent(applicationContext, Updater::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        updaterIntent.putExtra("fullPath", fullPath)
                        updaterIntent.putExtra("changes", changesStr)

                        val pendingSurvey = PendingIntent.getService(applicationContext, 0, updaterIntent, 0)

                        val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                                .setSmallIcon(R.drawable.ic_stat_extrema_update)
                                .setContentTitle(applicationContext.getString(R.string.update_title))
                                .setContentText(applicationContext.getString(R.string.update_available))
                                .setContentIntent(pendingSurvey)
                                .setAutoCancel(true)
                                .setOnlyAlertOnce(true)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)

                        notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())
                    }
                },
                Response.ErrorListener {
                    //do nothing
                }) {
            override fun getHeaders(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["Content-Type"] = "application/json"
                return params
            }
        }
        requestQueue.add(serverRequest)

        return Result.success()
    }
}