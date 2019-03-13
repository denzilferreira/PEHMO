package fi.oulu.ubicomp.extrema.workers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import androidx.work.Worker
import androidx.work.WorkerParameters


class BluetoothWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    lateinit var bluetoothAdapter : BluetoothAdapter
    lateinit var bleHandler : Handler
//
    override fun doWork(): Result {

        bleHandler = Handler()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (!bluetoothAdapter.isEnabled) {
                val btEnable = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applicationContext.startActivity(btEnable)
            } else {
                val scanSettings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                        .build()


            }
        }
        return Result.success()
    }
//
//    fun scanRunnable() = Runnable {
//        val scanner = bluetoothAdapter.bluetoothLeScanner
//        if (scanner != null) {
//            bleHandler.postDelayed(stopScan(), 3000)
//            scanner.startScan(null, scanSettings, scanCallback)
//        }
//    }
//
//    fun stopScan() = Runnable {
//        val scanner = bluetoothAdapter.bluetoothLeScanner
//        if (scanner != null) {
//            scanner.stopScan(scanCallback)
//        }
//    }
}