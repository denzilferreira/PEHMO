package fi.oulu.ubicomp.extrema.workers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import fi.oulu.ubicomp.extrema.Home
import fi.oulu.ubicomp.extrema.database.Bluetooth
import fi.oulu.ubicomp.extrema.database.ExtremaDatabase
import fi.oulu.ubicomp.extrema.database.Participant
import org.jetbrains.anko.doAsync


class BluetoothWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    private lateinit var bluetoothAdapter : BluetoothAdapter
    private lateinit var bleHandler : Handler
    private lateinit var scanSettings : ScanSettings

    private lateinit var db: ExtremaDatabase
    private lateinit var participantData: Participant

    override fun doWork(): Result {

        db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                .addMigrations(Home.MIGRATION_1_2)
                .build()

        participantData = db.participantDao().getParticipant()

        val mHandlerThread = HandlerThread("EXTREMA-BLUETOOTH")
        mHandlerThread.start()

        bleHandler = Handler(mHandlerThread.looper)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (!bluetoothAdapter.isEnabled) {
                val btEnable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(btEnable)
            } else {
                scanSettings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                        .build()
                bleHandler.post(scanRunnable())
            }
        }
        return Result.success()
    }

    fun scanRunnable() = Runnable {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner != null) {
            bleHandler.postDelayed(stopScan(), 10000)
            scanner.startScan(scanCallback)
            scanner.startScan(null, scanSettings, scanCallback)
        }
    }

    fun stopScan() = Runnable {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner != null) {
            scanner.stopScan(scanCallback)
        }
    }

    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            val btDevice : BluetoothDevice = result!!.device
            if ( ! btDevice.address.equals(participantData.ruuviTag, ignoreCase = true)) return

            val bluetoothData = Bluetooth(null,
                    participantId = participantData.participantId,
                    entryDate = System.currentTimeMillis(),
                    macAddress = btDevice.address,
                    btName = btDevice.name,
                    btRSSI = result.rssi
            )

            doAsync {
                db.bluetoothDao().insert(bluetoothData)
                Log.d(Home.TAG, bluetoothData.toString())
            }
        }
    }

    override fun onStopped() {
        super.onStopped()
        db.close()
    }
}