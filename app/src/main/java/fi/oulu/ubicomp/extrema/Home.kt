package fi.oulu.ubicomp.extrema

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SpinnerAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
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

        const val ACTION_RUUVITAG = "ACTION_RUUVITAG"
        const val EXTRA_RUUVITAG = "EXTRA_RUUVITAG"

        const val PEHMO_PERMISSIONS = 12345
        const val PEHMO_LOCATION = 123456
        const val PEHMO_SURVEY = 1234
        const val PEHMO_BATTERY = 123
        const val PEHMO_BLUETOOTH = 12
        const val EXTREMA_PREFS = "fi.oulu.ubicomp.extrema.prefs"
        const val UUID = "deviceId"
        const val STUDY_URL = "https://pehmo.awareframework.com:8080/index.php/1/4lph4num3ric"

        const val RuuviV2and4_LAYOUT = "s:0-1=feaa,m:2-2=10,p:3-3:-41,i:4-21v"
        const val RuuviV3_LAYOUT = "x,m:0-2=990403,i:2-15,d:2-2,d:3-3,d:4-4,d:5-5,d:6-6,d:7-7,d:8-8,d:9-9,d:10-10,d:11-11,d:12-12,d:13-13,d:14-14,d:15-15"
        const val RuuviV5_LAYOUT = "x,m:0-2=990405,i:20-25,d:2-2,d:3-3,d:4-4,d:5-5,d:6-6,d:7-7,d:8-8,d:9-9,d:10-10,d:11-11,d:12-12,d:13-13,d:14-14,d:15-15,d:16-16,d:17-17,d:18-18,d:19-19,d:20-20,d:21-21,d:22-22,d:23-23,d:24-24,d:25-25"

        val region: Region = Region("fi.oulu.ubicomp.extrema", null, null, null)

        lateinit var ruuvi: Beacon
        lateinit var countries: ArrayAdapter<String>
        lateinit var ruuviTxt: EditText
    }

    lateinit var beaconManager: BeaconManager
    lateinit var beaconConsumer: BeaconConsumer
    lateinit var rangeNotifier: RuuviRangeNotifier

    val ruuviReceiver = RuuviReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(EXTREMA_PREFS, 0)
        if (!prefs.contains(UUID)) {
            prefs.edit().putString(UUID, java.util.UUID.randomUUID().toString()).apply()
        }

        setContentView(R.layout.activity_account)
        countries = getCountries()
        participantCountry.adapter = countries as SpinnerAdapter

        ruuviTxt = ruuviStatus

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
                            timestamp = System.currentTimeMillis(),
                            participantCountry = participantCountry.selectedItem.toString()
                    )
                } catch (e: UninitializedPropertyAccessException) {
                    Participant(null,
                            participantEmail = participantEmail.text.toString(),
                            participantName = participantName.text.toString(),
                            participantId = participantId.text.toString(),
                            ruuviTag = "",
                            timestamp = System.currentTimeMillis(),
                            participantCountry = participantCountry.selectedItem.toString()
                    )
                }

                doAsync {
                    val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                            .build()

                    db.participantDao().insert(participant)
                    db.close()

                    val sync = OneTimeWorkRequest.Builder(SyncWorker::class.java).build()
                    WorkManager.getInstance(applicationContext).enqueue(sync)

                    uiThread {
                        toast(getString(R.string.participant_created) + " " + getString(R.string.welcome) + " ${participant.participantName}!")
                                .apply { duration = Toast.LENGTH_LONG }
                                .show()
                    }

                    finish()

                    startService(Intent(applicationContext, Pehmo::class.java))
                    startActivity(Intent(applicationContext, ViewSurvey::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
        }

        registerReceiver(ruuviReceiver, IntentFilter(ACTION_RUUVITAG))
    }

    class RuuviReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(ACTION_RUUVITAG)) {
                ruuviTxt.setText(intent?.getStringExtra(EXTRA_RUUVITAG) ?: "")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        checkDoze(applicationContext)
        checkLocation(applicationContext)

        val permissions: MutableList<String> = ArrayList()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.BLUETOOTH)
                    permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
                }
            }

            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PEHMO_PERMISSIONS)

        } else {
            doAsync {
                val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                        .build()

                val participantData = db.participantDao().getParticipant()
                if (participantData.isNotEmpty()) {

                    db.close()

                    finish()
                    startService(Intent(applicationContext, Pehmo::class.java))
                    startActivity(Intent(applicationContext, ViewSurvey::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

                } else {
                    if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                        beaconConsumer = this@Home
                        beaconManager = BeaconManager.getInstanceForApplication(applicationContext)
                        rangeNotifier = RuuviRangeNotifier(applicationContext)

                        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
                        if (bluetoothAdapter?.isEnabled == false) {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            startActivityForResult(enableBtIntent, PEHMO_BLUETOOTH)
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

                    uiThread {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                        fusedClient.lastLocation.addOnCompleteListener { task ->
                            val location : Location? = task.result
                            if (location != null) {
                                val geocoder = Geocoder(applicationContext, Locale.getDefault())
                                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                participantCountry.setSelection(countries.getPosition(addresses.first().countryName), true)
                                participantCountry.dispatchSetSelected(true)
                            }
                        }
                    }

                    db.close()
                }
            }
        }
    }

    fun checkDoze(context: Context): Boolean {
        var isIgnore = true

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isIgnore = powerManager.isIgnoringBatteryOptimizations(context.packageName)

            if (!isIgnore) {
                val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = applicationContext.getString(R.string.app_name)
                    val descriptionText = applicationContext.getString(R.string.app_name)
                    val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = descriptionText
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingBattery = PendingIntent.getActivity(applicationContext, 0, batteryIntent, PendingIntent.FLAG_UPDATE_CURRENT)
                val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                        .setSmallIcon(R.drawable.ic_stat_pehmo_battery)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.battery_ignore))
                        .setContentIntent(pendingBattery)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)

                notificationManager.notify(Home.PEHMO_BATTERY, builder.build())
            }
        }
        return isIgnore
    }

    fun checkLocation(context : Context) : Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isLocationEnabled) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = applicationContext.getString(R.string.app_name)
                val descriptionText = applicationContext.getString(R.string.app_name)
                val channel = NotificationChannel("EXTREMA", name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }

            val locationIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingLocation = PendingIntent.getActivity(applicationContext, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val builder = NotificationCompat.Builder(applicationContext, "EXTREMA")
                    .setSmallIcon(R.drawable.ic_stat_pehmo_location)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.location_disable))
                    .setContentIntent(pendingLocation)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

            notificationManager.notify(Home.PEHMO_LOCATION, builder.build())
        }

        return isLocationEnabled
    }

    override fun onDestroy() {
        super.onDestroy()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (::beaconManager.isInitialized) {
                beaconManager.removeRangeNotifier(rangeNotifier)
                beaconManager.stopRangingBeaconsInRegion(region)
                beaconManager.unbind(beaconConsumer)
            }
        }
        unregisterReceiver(ruuviReceiver)
        startService(Intent(applicationContext, Pehmo::class.java))
    }

    override fun onBeaconServiceConnect() {
        if (!beaconManager.rangingNotifiers.contains(rangeNotifier))
            beaconManager.addRangeNotifier(rangeNotifier)

        beaconManager.startRangingBeaconsInRegion(region)
    }

    private fun getCountries(): ArrayAdapter<String> {
        val locales = Locale.getAvailableLocales()
        val countries = ArrayList<String>()
        for (country in locales) {
            if (!countries.contains(country.displayCountry) && country.displayCountry.isNotEmpty())
                countries.add(country.displayCountry)
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER)
        return ArrayAdapter(applicationContext, R.layout.country_spinner_item, countries)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            ActivityCompat.requestPermissions(this, permissions, PEHMO_PERMISSIONS)
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
                toast(getString(R.string.sync)).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    class RuuviRangeNotifier(context: Context) : RangeNotifier {
        var mContext = context
        override fun didRangeBeaconsInRegion(beacons: MutableCollection<Beacon>?, region: Region?) {
            if (beacons?.size ?: 0 > 0) {
                val closest = beacons?.maxBy { it.rssi }
                ruuvi = closest!!
                mContext.sendBroadcast(Intent(ACTION_RUUVITAG).putExtra(EXTRA_RUUVITAG, ruuvi.bluetoothAddress))
            }
        }
    }
}
