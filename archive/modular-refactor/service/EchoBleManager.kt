package ru.echo.radar.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.UUID

object EchoBleManager {

    private const val TAG = "EchoBleManager"
    private const val ECHO_MANUFACTURER_ID = 0x0ECA

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var isAdvertising = false
    private var isScanning = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.i(TAG, "✓ BLE Маяк ЭХО успешно запущен в эфире")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w(TAG, "✗ Ошибка запуска BLE рекламы: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "✗ Ошибка BLE сканирования: $errorCode")
            isScanning = false
        }
    }

    fun init(context: Context) {
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bm?.adapter
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            scanner = bluetoothAdapter?.bluetoothLeScanner
        } catch (e: Exception) {
            Log.w(TAG, "BLE init error: ${e.message}")
        }
    }

    fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val advGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            return scanGranted && advGranted
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(context: Context, deviceId: String, bpm: Int = 120, camelotNum: Int = 8, isMinor: Boolean = true) {
        if (!hasPermissions(context) || bluetoothAdapter?.isEnabled != true) return
        if (advertiser == null) {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        }
        if (advertiser == null || isAdvertising) return

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            val buffer = ByteBuffer.allocate(10)
            buffer.put(0xE0.toByte()) // Echo protocol v1
            val idHash = deviceId.hashCode()
            buffer.putInt(idHash)
            buffer.put((bpm and 0xFF).toByte())
            val keyByte = ((camelotNum and 0x0F) or (if (isMinor) 0x10 else 0x00)).toByte()
            buffer.put(keyByte)
            buffer.put(0x01.toByte())

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(ECHO_MANUFACTURER_ID, buffer.array())
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start advertising: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising || advertiser == null) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop advertising: ${e.message}")
        } finally {
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context) {
        if (!hasPermissions(context) || bluetoothAdapter?.isEnabled != true) return
        if (scanner == null) {
            scanner = bluetoothAdapter?.bluetoothLeScanner
        }
        if (scanner == null || isScanning) return

        try {
            val filter = ScanFilter.Builder()
                .setManufacturerData(ECHO_MANUFACTURER_ID, byteArrayOf(0xE0.toByte()), byteArrayOf(0xF0.toByte()))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start scanning: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning || scanner == null) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop scanning: ${e.message}")
        } finally {
            isScanning = false
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val msd = record.getManufacturerSpecificData(ECHO_MANUFACTURER_ID) ?: return
        if (msd.isEmpty() || msd[0] != 0xE0.toByte()) return
        Log.d(TAG, "Discovered nearby peer with RSSI: ${result.rssi}")
    }
}
