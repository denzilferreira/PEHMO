package fi.oulu.ubicomp.extrema.workers

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.PermissionChecker
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import org.jetbrains.anko.doAsync

class LocationWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams), LocationListener {

    override fun onLocationChanged(location: Location?) {
        latestLocation = location
        if (applicationContext.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED) {
            val satelliteCount = latestLocation?.extras?.getInt("satellites")
            val locationData = fi.oulu.ubicomp.extrema.database.Location(
                    null,
                    entryDate = System.currentTimeMillis(),
                    participantId = participantData?.participantId,
                    latitude = latestLocation?.latitude,
                    longitude = latestLocation?.longitude,
                    accuracy = latestLocation?.accuracy,
                    speed = latestLocation?.speed,
                    source = latestLocation?.provider,
                    satellites = satelliteCount,
                    isIndoors = (satelliteCount == 0)
            )

            doAsync {
                db?.locationDao()?.insert(locationData)
                Log.d(Home.TAG, locationData.toString())
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String?) {}
    override fun onProviderDisabled(provider: String?) {}

    var db: ExtremaDatabase? = null
    var participantData: Participant? = null
    lateinit var locationManager: LocationManager
    var latestLocation: Location? = null

    override fun doWork(): Result {
        if (applicationContext.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED) {
            db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema").build()
            participantData = db?.participantDao()?.getParticipant()

            locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val mHandlerThread = HandlerThread("EXTREMA-LOCATION")
            mHandlerThread.start()

            locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, this, mHandlerThread.looper)
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, mHandlerThread.looper)
        }

        return Result.success()
    }

    override fun onStopped() {
        super.onStopped()
        db?.close()
    }
}