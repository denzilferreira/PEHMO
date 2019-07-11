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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.R
import fi.oulu.ubicomp.extrema.database.*
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import org.json.JSONObject

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Home.EXTREMA_PREFS, 0)
        val db: ExtremaDatabase = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        val requestQueue = Volley.newRequestQueue(applicationContext)

        println("Sync started...")

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

        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, surveyIntent, 0)

        val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                .setSmallIcon(R.drawable.ic_stat_extrema_survey)
                .setContentTitle(applicationContext.getString(R.string.survey_notification_title))
                .setContentText("Syncing data to server...")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        val pendingParticipant: Array<Participant>
        if (prefs.getBoolean(Home.FORCE_SYNC, false)) {
            pendingParticipant = db.participantDao().getPendingSync(0)

            builder.setContentText("Syncing participant: ${pendingParticipant.size}")
            notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        } else {
            pendingParticipant = db.participantDao().getPendingSync(prefs.getLong("participant", 0))
        }

        if (pendingParticipant.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()

            for (participantRecord in pendingParticipant) {
                val data = JSONObject()
                        .put("tableName", "participant")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(participantRecord))
                        .put("timestamp", System.currentTimeMillis())

                val serverRequest = object : JsonObjectRequest(Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println("Sync OK [participant] ${it.toString(5)}")
                            prefs.edit().putLong("participant", participantRecord.onboardDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Sync OK [participant]")
                                prefs.edit().putLong("participant", participantRecord.onboardDate).apply()
                            }
                            if (it.networkResponse != null)
                                println("Error ${it.networkResponse.statusCode}")
                        }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("Content-Type", "application/json")
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            }
        } else {
            println("Nothing to sync [participant]")
        }

        val pendingBluetooth: Array<Bluetooth>
        if (prefs.getBoolean(Home.FORCE_SYNC, false)) {
            pendingBluetooth = db.bluetoothDao().getPendingSync(0)

            builder.setContentText("Syncing bluetooth: ${pendingBluetooth.size}")
            notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        } else {
            pendingBluetooth = db.bluetoothDao().getPendingSync(prefs.getLong("bluetooth", 0))
        }

        if (pendingBluetooth.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (bluetoothRecord in pendingBluetooth) {
                val data = JSONObject()
                        .put("tableName", "bluetooth")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(bluetoothRecord))
                        .put("timestamp", System.currentTimeMillis())

                val serverRequest = object : JsonObjectRequest(Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println("Sync OK [bluetooth] ${it.toString(5)}")
                            prefs.edit().putLong("bluetooth", bluetoothRecord.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Sync OK [bluetooth]")
                                prefs.edit().putLong("bluetooth", bluetoothRecord.entryDate).apply()
                            }
                            if (it.networkResponse != null)
                                println("Error ${it.networkResponse.statusCode}")
                        }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("Content-Type", "application/json")
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            }
        } else {
            println("Nothing to sync [bluetooth]")
        }

        val pendingLocation: Array<Location>
        if (prefs.getBoolean(Home.FORCE_SYNC, false)) {
            pendingLocation = db.locationDao().getPendingSync(0)

            builder.setContentText("Syncing locations: ${pendingLocation.size}")
            notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        } else {
            pendingLocation = db.locationDao().getPendingSync(prefs.getLong("location", 0))
        }

        if (pendingLocation.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (locationRecord in pendingLocation) {
                val data = JSONObject()
                        .put("tableName", "location")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(locationRecord))
                        .put("timestamp", System.currentTimeMillis())

                val serverRequest = object : JsonObjectRequest(Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println("Sync OK [locations] ${it.toString(5)}")
                            prefs.edit().putLong("location", locationRecord.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Sync OK [locations]")
                                prefs.edit().putLong("location", locationRecord.entryDate).apply()
                            }
                            if (it.networkResponse != null)
                                println("Error ${it.networkResponse.statusCode}")
                        }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("Content-Type", "application/json")
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            }
        } else {
            println("Nothing to sync [locations]")
        }

        val pendingSurvey: Array<Survey>
        if (prefs.getBoolean(Home.FORCE_SYNC, false)) {
            pendingSurvey = db.surveyDao().getPendingSync(0)

            builder.setContentText("Syncing surveys: ${pendingSurvey.size}")
            notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        } else {
            pendingSurvey = db.surveyDao().getPendingSync(prefs.getLong("survey", 0))
        }

        if (pendingSurvey.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (surveyRecord in pendingSurvey) {
                val data = JSONObject()
                        .put("tableName", "diary")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(surveyRecord))
                        .put("timestamp", System.currentTimeMillis())

                val serverRequest = object : JsonObjectRequest(Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println("Sync OK [survey] ${it.toString(5)}")
                            prefs.edit().putLong("survey", surveyRecord.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Sync OK [survey]")
                                prefs.edit().putLong("survey", surveyRecord.entryDate).apply()
                            }
                            if (it.networkResponse != null)
                                println("Error ${it.networkResponse.statusCode}")
                        }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("Content-Type", "application/json")
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            }
        } else {
            println("Nothing to sync [survey]")
        }

        db.close()

        println("Sync finished!")

        builder.setContentText("Syncing finished!")
        notificationManager.notify(Home.EXTREMA_PERMISSIONS, builder.build())

        if (prefs.getBoolean(Home.FORCE_SYNC, false))
            prefs.edit().putBoolean(Home.FORCE_SYNC, false).apply()

        return Result.success()
    }
}