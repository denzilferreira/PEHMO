package fi.oulu.ubicomp.extrema

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.*
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import fi.oulu.ubicomp.extrema.workers.BluetoothWorker
import fi.oulu.ubicomp.extrema.workers.LocationWorker
import fi.oulu.ubicomp.extrema.workers.SurveyWorker
import fi.oulu.ubicomp.extrema.workers.SyncWorker
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

class Home : AppCompatActivity() {

    companion object {
        const val TAG = "EXTREMA"
        const val EXTREMA_PERMISSIONS = 12345
        const val EXTREMA_PREFS = "fi.oulu.ubicomp.extrema.prefs"
        const val UUID = "deviceId"
        const val STUDY_URL = "https://co2.awareframework.com:8443/insert"
        var participantData: Participant? = null
    }

    var db: ExtremaDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(EXTREMA_PREFS, 0)
        if (!prefs.contains(UUID)) {
            prefs.edit().putString(UUID, java.util.UUID.randomUUID().toString()).apply()
        }

        setContentView(R.layout.activity_account)

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        btnSaveParticipant.setOnClickListener {
            val participant = Participant(null,
                    participantEmail = participantEmail.text.toString(),
                    participantName = participantName.text.toString(),
                    participantId = participantId.text.toString(),
                    ruuviTag = ruuviTag.text.toString(),
                    onboardDate = System.currentTimeMillis()
            )

            doAsync {
                db?.participantDao()?.insert(participant)
                participantData = db?.participantDao()?.getParticipant()

                val jsonBuilder = GsonBuilder()
                val jsonPost = jsonBuilder.create()

                val requestQueue = Volley.newRequestQueue(applicationContext)

                val data = JSONObject()
                        .put("tableName", "participant")
                        .put("deviceId", prefs.getString(UUID, ""))
                        .put("data", jsonPost.toJson(participantData))
                        .put("timestamp", System.currentTimeMillis())

                val serverRequest = JsonObjectRequest(Request.Method.POST, STUDY_URL, data,
                    Response.Listener {
                        println(it.toString(5))
                    },
                    Response.ErrorListener {}
                )
                requestQueue.add(serverRequest)

                finish()
                startActivity(Intent(applicationContext, ViewSurvey::class.java))
                startSensing()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    EXTREMA_PERMISSIONS)
        } else {
            doAsync {
                participantData = db?.participantDao()?.getParticipant()
                if (participantData != null) {
                    finish()
                    startActivity(Intent(applicationContext, ViewSurvey::class.java))
                    startSensing()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        db?.close()
    }

    fun startSensing() {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()

        //Set location logging every 15 minutes
        val locationTracking = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
        WorkManager.getInstance().enqueueUniquePeriodicWork("LOCATION_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, locationTracking)

        //Set bluetooth scanning if available or makes sense
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (!participantData?.ruuviTag.isNullOrBlank()) {
                val bluetoothTracking = PeriodicWorkRequestBuilder<BluetoothWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
                WorkManager.getInstance().enqueueUniquePeriodicWork("BLUETOOTH_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, bluetoothTracking)
            }
        }

        //Set daily survey reminder at 7pm
        val hourOfDay = 19 //7pm
        val repeatInterval = 1L //once a day
        val flexTime = calculateFlex(hourOfDay, repeatInterval)
        val surveyReminder = PeriodicWorkRequest.Builder(SurveyWorker::class.java, repeatInterval, TimeUnit.DAYS, flexTime, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance().enqueueUniquePeriodicWork("SURVEY_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, surveyReminder)

        //Set data sync to server every 30 minutes
        val constraintsSync = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build() //only sync when on WiFi
        val dataSync = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraintsSync).build()
        WorkManager.getInstance().enqueueUniquePeriodicWork("SYNC_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, dataSync)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    EXTREMA_PERMISSIONS)
        }
    }

    fun calculateFlex(hour: Int, repeat: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        val calendarNow = Calendar.getInstance()
        if (calendarNow.timeInMillis < calendar.timeInMillis) {
            calendarNow.timeInMillis = calendarNow.timeInMillis + TimeUnit.DAYS.toMillis(repeat)
        }

        val delta = calendarNow.timeInMillis - calendar.timeInMillis
        return if (delta > PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS) delta else PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS
    }
}
