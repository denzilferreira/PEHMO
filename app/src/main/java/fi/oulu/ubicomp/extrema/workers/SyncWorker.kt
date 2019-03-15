package fi.oulu.ubicomp.extrema.workers

import android.content.Context
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import org.json.JSONObject

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Home.EXTREMA_PREFS, 0)
        val db: ExtremaDatabase = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        val requestQueue = Volley.newRequestQueue(applicationContext)

        val pendingBluetooth = db.bluetoothDao().getPendingSync(prefs.getLong("bluetooth",0))
        if (pendingBluetooth.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (bluetoothRecord in pendingBluetooth) {
                val data = JSONObject()
                        .put("tableName", "bluetooth")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(bluetoothRecord))
                        .put("timestamp", System.currentTimeMillis())
                val serverRequest = JsonObjectRequest(Request.Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println(it.toString(5))
                        },
                        Response.ErrorListener {
                            println("Error ${it.networkResponse}")
                        }
                )
                requestQueue.add(serverRequest)
            }
            prefs.edit().putLong("bluetooth", pendingBluetooth.last().entryDate).apply()
        }

        val pendingLocation = db.bluetoothDao().getPendingSync(prefs.getLong("location",0))
        if (pendingLocation.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (locationRecord in pendingLocation) {
                val data = JSONObject()
                        .put("tableName", "location")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(locationRecord))
                        .put("timestamp", System.currentTimeMillis())
                val serverRequest = JsonObjectRequest(Request.Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println(it.toString(5))
                        },
                        Response.ErrorListener {
                            println("Error ${it.networkResponse}")
                        }
                )
                requestQueue.add(serverRequest)
            }
            prefs.edit().putLong("location", pendingLocation.last().entryDate).apply()
        }

        val pendingSurvey = db.bluetoothDao().getPendingSync(prefs.getLong("survey",0))
        if (pendingSurvey.isNotEmpty()) {
            val jsonBuilder = GsonBuilder()
            val jsonPost = jsonBuilder.create()
            for (surveyRecord in pendingSurvey) {
                val data = JSONObject()
                        .put("tableName", "diary")
                        .put("deviceId", prefs.getString(Home.UUID, ""))
                        .put("data", jsonPost.toJson(surveyRecord))
                        .put("timestamp", System.currentTimeMillis())
                val serverRequest = JsonObjectRequest(Request.Method.POST, Home.STUDY_URL, data,
                        Response.Listener {
                            println(it.toString(5))
                        },
                        Response.ErrorListener {
                            println("Error ${it.networkResponse}")
                        }
                )
                requestQueue.add(serverRequest)
            }
            prefs.edit().putLong("survey", pendingSurvey.last().entryDate).apply()
        }
        return Result.success()
    }
}