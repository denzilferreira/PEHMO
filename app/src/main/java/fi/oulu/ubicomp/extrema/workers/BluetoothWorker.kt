package fi.oulu.ubicomp.extrema.workers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
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
import org.jetbrains.anko.doAsync

class BluetoothWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleHandler: Handler
    private lateinit var scanner: BluetoothLeScanner

    override fun doWork(): Result {

        val mHandlerThread = HandlerThread("EXTREMA-BLUETOOTH")
        mHandlerThread.start()

        bleHandler = Handler(mHandlerThread.looper)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (!bluetoothAdapter.isEnabled) {
                val btEnable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(btEnable)
            } else {
                scanner = bluetoothAdapter.bluetoothLeScanner
                bleHandler.post(scanRunnable())
            }
        }
        return Result.success()
    }

    private fun scanRunnable() = Runnable {
        scanner.flushPendingScanResults(scanCallback)
        bleHandler.postDelayed(stopScan(), 10000)
        scanner.startScan(null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
                scanCallback)
    }

    private fun stopScan() = Runnable {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            doAsync {
                val db = Room.databaseBuilder(applicationContext, ExtremaDatabase::class.java, "extrema")
                        .addMigrations(Home.MIGRATION_1_2, Home.MIGRATION_2_3)
                        .build()

                val participantData = db.participantDao().getParticipant()
                val btDevice: BluetoothDevice = result!!.device
                if (btDevice.address.equals(participantData.first().ruuviTag, ignoreCase = true)) {
                    val bluetoothData = Bluetooth(null,
                            participantId = participantData.first().participantId,
                            entryDate = System.currentTimeMillis(),
                            macAddress = btDevice.address,
                            btName = btDevice.name,
                            btRSSI = result.rssi
                    )
                    db.bluetoothDao().insert(bluetoothData)
                    Log.d(Home.TAG, bluetoothData.toString())
                }
                db.close()
            }
        }
    }
}