package fi.oulu.ubicomp.extrema

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.SpinnerAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import fi.oulu.ubicomp.extrema.services.Pehmo
import fi.oulu.ubicomp.extrema.views.ViewAccount
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import fi.oulu.ubicomp.extrema.workers.SyncWorker
import kotlinx.android.synthetic.main.activity_account.*
import org.altbeacon.beacon.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList

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

        var participantData: Participant? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE participant ADD COLUMN country TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `battery` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT, `participantId` TEXT NOT NULL, `entryDate` INTEGER NOT NULL, `batteryPercent` REAL NOT NULL, `batteryTemperature` REAL NOT NULL, `batteryStatus` TEXT NOT NULL)")
            }
        }
    }

    lateinit var beaconManager: BeaconManager
    lateinit var rangeNotifier: RuuviRangeNotifier

    var db: ExtremaDatabase? = null
    val region: Region = Region("fi.oulu.ubicomp.extrema", null, null, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(EXTREMA_PREFS, 0)
        if (!prefs.contains(UUID)) {
            prefs.edit().putString(UUID, java.util.UUID.randomUUID().toString()).apply()
        }

        setContentView(R.layout.activity_account)

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        participantCountry.adapter = getCountries()

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
                            onboardDate = System.currentTimeMillis(),
                            participantCountry = participantCountry.selectedItem.toString()
                    )
                } catch (e: UninitializedPropertyAccessException) {
                    Participant(null,
                            participantEmail = participantEmail.text.toString(),
                            participantName = participantName.text.toString(),
                            participantId = participantId.text.toString(),
                            ruuviTag = "",
                            onboardDate = System.currentTimeMillis(),
                            participantCountry = participantCountry.selectedItem.toString()
                    )
                }

                doAsync {
                    db?.participantDao()?.insert(participant)
                    db?.close()

                    val sync = OneTimeWorkRequest.Builder(SyncWorker::class.java).build()
                    WorkManager.getInstance(applicationContext).enqueue(sync)

                    uiThread {
                        toast(getString(R.string.participant_created) + " " + getString(R.string.welcome) + " ${participant.participantName}!").show()
                    }

                    finish()

                    startService(Intent(applicationContext, Pehmo::class.java))
                    startActivity(Intent(applicationContext, ViewSurvey::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val permissions: MutableList<String> = ArrayList()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                beaconConsumer = this

                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {

                    permissions.add(Manifest.permission.BLUETOOTH)
                    permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }

            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), EXTREMA_PERMISSIONS)

        } else {
            doAsync {
                participantData = db?.participantDao()?.getParticipant()
                if (participantData != null) {
                    finish()
                    startService(Intent(applicationContext, Pehmo::class.java))
                    startActivity(Intent(applicationContext, ViewSurvey::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                } else {
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {

                        beaconManager = BeaconManager.getInstanceForApplication(applicationContext)
                        rangeNotifier = RuuviRangeNotifier()

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

    override fun onDestroy() {
        super.onDestroy()
        if (::beaconManager.isInitialized) {
            beaconManager?.removeRangeNotifier(rangeNotifier)
            beaconManager?.stopRangingBeaconsInRegion(region)
            beaconManager?.unbind(beaconConsumer)
        }

        startService(Intent(applicationContext, Pehmo::class.java))
    }

    override fun onBeaconServiceConnect() {
        if (!beaconManager.rangingNotifiers.contains(rangeNotifier))
            beaconManager.addRangeNotifier(rangeNotifier)

        beaconManager.startRangingBeaconsInRegion(region)
    }

    private fun getCountries(): SpinnerAdapter {
        val locales = Locale.getAvailableLocales()
        val countries = ArrayList<String>()
        for (country in locales) {
            if (!countries.contains(country.displayCountry) && country.displayCountry.isNotEmpty())
                countries.add(country.displayCountry)
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER)
        return ArrayAdapter(applicationContext, android.R.layout.simple_spinner_item, countries)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            ActivityCompat.requestPermissions(this, permissions, EXTREMA_PERMISSIONS)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.sync, menu)
        menu?.removeItem(R.id.menu_account)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                startActivity(Intent(applicationContext, ViewAccount::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
            R.id.menu_sync -> {
                val sync = OneTimeWorkRequest.Builder(SyncWorker::class.java).build()
                WorkManager.getInstance(applicationContext).enqueue(sync)
                toast(getString(R.string.sync))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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
