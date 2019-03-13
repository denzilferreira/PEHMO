package fi.oulu.ubicomp.extrema

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import fi.oulu.ubicomp.extrema.views.ViewSurvey
import fi.oulu.ubicomp.extrema.workers.LocationWorker
import kotlinx.android.synthetic.main.activity_account.*
import org.jetbrains.anko.doAsync
import java.util.concurrent.TimeUnit

class Home : AppCompatActivity() {

    companion object {
        val TAG = "EXTREMA"
        val EXTREMA_PERMISSIONS = 12345
        var participantData: Participant? = null
    }

    var db: ExtremaDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_account)

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()

        btnSaveParticipant.setOnClickListener {
            val participant = Participant(null,
                    participantBirth = "${participantDoB.year}/${participantDoB.month}/${participantDoB.dayOfMonth}",
                    participantEmail = participantEmail.text.toString(),
                    participantName = participantName.text.toString(),
                    participantId = participantId.text.toString(),
                    onboardDate = System.currentTimeMillis()
            )

            doAsync {
                db?.participantDao()?.insert(participant)
                participantData = db?.participantDao()?.getParticipant()
                finish()
                startActivity(Intent(applicationContext, ViewSurvey::class.java))
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

                    startActivity(Intent(applicationContext, ViewSurvey::class.java))

                    val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
                    val locationTracking = PeriodicWorkRequestBuilder<LocationWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
                    WorkManager.getInstance().enqueueUniquePeriodicWork("LOCATION_EXTREMA", ExistingPeriodicWorkPolicy.REPLACE, locationTracking)

                    finish()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    EXTREMA_PERMISSIONS)
        }
    }
}
