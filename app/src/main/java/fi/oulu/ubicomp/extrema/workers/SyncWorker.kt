package fi.oulu.ubicomp.extrema.workers

import android.content.Context
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import org.jetbrains.anko.doAsync
import org.json.JSONObject

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Home.EXTREMA_PREFS, 0)
        val requestQueue = Volley.newRequestQueue(applicationContext)

        println("Sync started...")

        doAsync {
            val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                    .addMigrations(Home.MIGRATION_1_2, Home.MIGRATION_2_3)
                    .build()

            val createParticipant = object : StringRequest(Method.POST, "${Home.STUDY_URL}/participant/create_table",null,
                    Response.ErrorListener {
                        if (it.networkResponse == null) {
                            println("Response null [participant create table]")
                            println("Error: ${it.message}")
                        }
                    }){}
            requestQueue.add(createParticipant)

            val pendingParticipant = db.participantDao().getPendingSync(prefs.getLong("participant", 0))
            if (pendingParticipant.isNotEmpty()) {

                val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/participant/insert",
                        Response.Listener {
                            println("Sync OK [participant]: ${pendingParticipant.size}")
                            prefs.edit().putLong("participant", pendingParticipant.maxBy { it.onboardDate }!!.onboardDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [participant]")
                                println("Error: ${it.message}")
                            }
                        }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("device_id", prefs.getString(Home.UUID, "")!!)
                        params.put("data", GsonBuilder().create().toJson(pendingParticipant))
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            } else {
                println("Nothing to sync [participant]")
            }

            val pendingBluetooth = db.bluetoothDao().getPendingSync(prefs.getLong("bluetooth", 0))
            if (pendingBluetooth.isNotEmpty()) {
                val createBluetooth = object : StringRequest(Method.POST, "${Home.STUDY_URL}/bluetooth/create_table", null,
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [bluetooth create table]")
                                println("Error: ${it.message}")
                            }
                        }){}
                requestQueue.add(createBluetooth)

                val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/bluetooth/insert",
                        Response.Listener {
                            println("Sync OK [bluetooth]: ${pendingBluetooth.size}")
                            prefs.edit().putLong("bluetooth", pendingBluetooth.maxBy { it.entryDate }!!.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [bluetooth]")
                                println("Error: ${it.message}")
                            }
                        }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("device_id", prefs.getString(Home.UUID, "")!!)
                        params.put("data", GsonBuilder().create().toJson(pendingBluetooth))
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            } else {
                println("Nothing to sync [bluetooth]")
            }

            val pendingLocation = db.locationDao().getPendingSync(prefs.getLong("location", 0))
            if (pendingLocation.isNotEmpty()) {
                val createLocation = object : StringRequest(Method.POST, "${Home.STUDY_URL}/location/create_table", null,
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [location create table]")
                                println("Error: ${it.message}")
                            }
                        }){}
                requestQueue.add(createLocation)

                val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/location/insert",
                        Response.Listener {
                            println("Sync OK [location]: ${pendingLocation.size}")
                            prefs.edit().putLong("location", pendingLocation.maxBy { it.entryDate }!!.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [location]")
                                println("Error: ${it.message}")
                            }
                        }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("device_id", prefs.getString(Home.UUID, "")!!)
                        params.put("data", GsonBuilder().create().toJson(pendingLocation))
                        return params
                    }
                }
                requestQueue.add(serverRequest)
            } else {
                println("Nothing to sync [locations]")
            }

            val pendingSurvey = db.surveyDao().getPendingSync(prefs.getLong("survey", 0))
            if (pendingSurvey.isNotEmpty()) {

                val createSurvey = object : StringRequest(Method.POST, "${Home.STUDY_URL}/survey/create_table", null,
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [survey create table]")
                                println("Error: ${it.message}")
                            }
                        }) {}
                requestQueue.add(createSurvey)

                val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/survey/insert",
                        Response.Listener {
                            println("Sync OK [survey]: ${pendingSurvey.size}")
                            prefs.edit().putLong("survey", pendingSurvey.maxBy { it.entryDate }!!.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [survey]")
                                println("Error: ${it.message}")
                            }
                        }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("device_id", prefs.getString(Home.UUID, "")!!)
                        params.put("data", GsonBuilder().create().toJson(pendingSurvey))
                        return params
                    }
                }
                requestQueue.add(serverRequest)

            } else {
                println("Nothing to sync [survey]")
            }

            val pendingBattery = db.batteryDao().getPendingSync(prefs.getLong("battery", 0))
            if (pendingBattery.isNotEmpty()) {

                val createBattery = object : StringRequest(Method.POST, "${Home.STUDY_URL}/battery/create_table", null,
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [battery create table]")
                                println("Error: ${it.message}")
                            }
                        }) {}
                requestQueue.add(createBattery)

                val serverRequest = object : StringRequest(Method.POST, "${Home.STUDY_URL}/battery/insert",
                        Response.Listener {
                            println("Sync OK [battery]: ${pendingBattery.size}")
                            prefs.edit().putLong("battery", pendingBattery.maxBy { it.entryDate }!!.entryDate).apply()
                        },
                        Response.ErrorListener {
                            if (it.networkResponse == null) {
                                println("Response null [battery]")
                                println("Error: ${it.message}")
                            }
                        }
                ) {
                    override fun getParams(): MutableMap<String, String> {
                        val params = HashMap<String, String>()
                        params.put("device_id", prefs.getString(Home.UUID, "")!!)
                        params.put("data", GsonBuilder().create().toJson(pendingBattery))
                        return params
                    }
                }
                requestQueue.add(serverRequest)

            } else {
                println("Nothing to sync [battery]")
            }
            println("Sync finished!")

            db.close()
        }

        return Result.success()
    }
}
