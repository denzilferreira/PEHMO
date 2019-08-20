package fi.oulu.ubicomp.extrema.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.database.Battery
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import org.jetbrains.anko.doAsync

class BatteryWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    private lateinit var db: ExtremaDatabase
    private lateinit var participantData: Participant

    override fun doWork(): Result {

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                .addMigrations(Home.MIGRATION_1_2, Home.MIGRATION_2_3)
                .build()

        participantData = db.participantDao().getParticipant()

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { intentFilter ->
            applicationContext.registerReceiver(null, intentFilter)
        }

        val batteryTemp: Double = batteryStatus.let { intent ->
            val temp: Int? = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999)
            temp!!.toDouble() / 10
        }

        val batteryPercent: Double = batteryStatus.let { intent ->
            val level: Int? = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int? = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level!!/scale!!.toDouble() * 100
        }

        val batteryCurrentStatus: String = batteryStatus.let { intent ->
            val status: Int? = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> "Unknown"
                else -> ""
            }
        }

        val batteryNow = Battery(uid = null,
                participantId = participantData.participantId,
                entryDate = System.currentTimeMillis(),
                batteryPercent = batteryPercent,
                batteryTemperature = batteryTemp ?: -999.9,
                batteryStatus = batteryCurrentStatus
        )

        doAsync {
            db.batteryDao().insert(batteryNow)

            println(batteryNow.toString())
        }

        db.close()

        return Result.success()
    }
}