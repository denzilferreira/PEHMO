package fi.oulu.ubicomp.extrema

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import org.altbeacon.beacon.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Home : AppCompatActivity(), BeaconConsumer {

    companion object {
        const val TAG = "EXTREMA"

        const val EXTREMA_PERMISSIONS = 12345
        const val EXTREMA_PREFS = "fi.oulu.ubicomp.extrema.prefs"
        const val UUID = "deviceId"
        const val STUDY_URL = "https://co2.awareframework.com:8443/insert"

        const val RuuviV2and4_LAYOUT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-21v"
        const val RuuviV3_LAYOUT = "x,m:0-2=990403,i:2-15,d:2-2,d:3-3,d:4-4,d:5-5,d:6-6,d:7-7,d:8-8,d:9-9,d:10-10,d:11-11,d:12-12,d:13-13,d:14-14,d:15-15"
        const val RuuviV5_LAYOUT = "x,m:0-2=990405,i:20-25,d:2-2,d:3-3,d:4-4,d:5-5,d:6-6,d:7-7,d:8-8,d:9-9,d:10-10,d:11-11,d:12-12,d:13-13,d:14-14,d:15-15,d:16-16,d:17-17,d:18-18,d:19-19,d:20-20,d:21-21,d:22-22,d:23-23,d:24-24,d:25-25"

        lateinit var ruuvi: Beacon
        lateinit var beaconConsumer: BeaconConsumer
    }

    lateinit var beaconManager: BeaconManager
    lateinit var rangeNotifier: RuuviRangeNotifier

    var db: ExtremaDatabase? = null
    var participantData: Participant? = null
    val region: Region = Region("fi.oulu.ubicomp.extrema", null, null, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(EXTREMA_PREFS, 0)
        if (!prefs.contains(UUID)) {
            prefs.edit().putString(UUID, java.util.UUID.randomUUID().toString()).apply()
        }

        setContentView(R.layout.activity_account)

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        beaconConsumer = this
        beaconManager = BeaconManager.getInstanceForApplication(applicationContext)
        rangeNotifier = RuuviRangeNotifier()

        btnSaveParticipant.setOnClickListener {
            if (participantName.text.isBlank() or participantId.text.isBlank() or participantEmail.text.isBlank()) {
                if (participantName.text.isBlank()) {
                    participantName.backgroundColor = Color.RED
                }
                if (participantId.text.isBlank()) {
                    participantId.backgroundColor = Color.RED
                }
                if (participantEmail.text.isBlank()) {
                    participantEmail.backgroundColor = Color.RED
                }
            } else {
                val participant = try {
                    Participant(null,
                            participantEmail = participantEmail.text.toString(),
                            participantName = participantName.text.toString(),
                            participantId = participantId.text.toString(),
                            ruuviTag = ruuvi?.bluetoothAddress ?: "",
                            onboardDate = System.currentTimeMillis()
                    )
                } catch (e: UninitializedPropertyAccessException) {
                    Participant(null,
                            participantEmail = participantEmail.text.toString(),
                            participantName = participantName.text.toString(),
                            participantId = participantId.text.toString(),
                            ruuviTag = "",
                            onboardDate = System.currentTimeMillis()
                    )
                }

                doAsync {
                    db?.participantDao()?.insert(participant)

                    Log.d(Home.TAG, participant.toString())

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
                            Response.ErrorListener {
                                println(it.toString())
                            }
                    )
                    requestQueue.add(serverRequest)

                    finish()

                    startActivity(Intent(applicationContext, ViewSurvey::class.java))
                    setSampling()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    permissions.plus(Manifest.permission.BLUETOOTH)
                    permissions.plus(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }

            ActivityCompat.requestPermissions(this, permissions, EXTREMA_PERMISSIONS)

        } else {
            doAsync {
                participantData = db?.participantDao()?.getParticipant()
                if (participantData != null) {
                    finish()
                    startActivity(Intent(applicationContext, ViewSurvey::class.java))
                    setSampling()
                } else {
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {

                        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
                        if (bluetoothAdapter?.isEnabled == false) {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            startActivityForResult(enableBtIntent, EXTREMA_PERMISSIONS)
                        }

                        beaconManager.backgroundMode = false
                        beaconManager.beaconParsers.clear()
                        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(RuuviV2and4_LAYOUT))
                        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(RuuviV3_LAYOUT))
                        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout(RuuviV5_LAYOUT))
                        beaconManager.backgroundScanPeriod = 5000
                        beaconManager.bind(beaconConsumer)
                        beaconManager.startRangingBeaconsInRegion(region)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        db?.close()
    }

    override fun onDestroy() {
        super.onDestroy()

        beaconManager?.removeRangeNotifier(rangeNotifier)
        beaconManager?.stopRangingBeaconsInRegion(region)
        beaconManager?.unbind(beaconConsumer)
        db?.close()
    }

    override fun onBeaconServiceConnect() {
        if (!beaconManager.rangingNotifiers.contains(rangeNotifier))
            beaconManager.addRangeNotifier(rangeNotifier)
        beaconManager.startRangingBeaconsInRegion(region)
    }


    private fun setSampling() {
        val locationTracking = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES).build() //Set location logging every 15 minutes
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("LOCATION_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, locationTracking)

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && !participantData?.ruuviTag.isNullOrBlank()) { //Set bluetooth scanning if available or makes sense
            val bluetoothTracking = PeriodicWorkRequestBuilder<BluetoothWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("BLUETOOTH_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, bluetoothTracking)
        }

        val surveyReminder = PeriodicWorkRequestBuilder<SurveyWorker>(30, TimeUnit.MINUTES).build() //check every 30 minutes if it's a good time to show the survey
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("SURVEY_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, surveyReminder)

        val dataSync = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build() //Set data sync to server every 15 minutes
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("SYNC_EXTREMA", ExistingPeriodicWorkPolicy.KEEP, dataSync)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            ActivityCompat.requestPermissions(this, permissions, EXTREMA_PERMISSIONS)
        }
    }

    class RuuviRangeNotifier : RangeNotifier {
        override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {
            if (beacons?.size ?: 0 > 0) {
                val closest = beacons?.maxBy { it.rssi }
                ruuvi = closest!!
            }
        }
    }
}
