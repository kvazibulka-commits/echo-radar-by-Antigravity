package ru.echo.radar

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

/**
 * EchoBleManager
 * Решение аппаратных ограничений Android 13–15 по концепции DeepSeek:
 * 1. Manufacturer Specific Data (MSD) для передачи метаданных трека (ID, BPM, тональность) без сопряжения (Peer-to-Peer).
 * 2. Режим ADVERTISE_MODE_LOW_POWER и SCAN_MODE_LOW_POWER для экономии батареи ночью.
 * 3. Защита от лимита перезапусков Android 14 (не более 4 перезапусков за 30 сек).
 * 4. Безопасная проверка рантайм-разрешений BLUETOOTH_SCAN / ADVERTISE / CONNECT.
 */
object EchoBleManager {

    private const val TAG = "EchoBleManager"
    private const val ECHO_MANUFACTURER_ID = 0x0ECA // Уникальный идентификатор производителя "Echo"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var isAdvertising = false
    private var isScanning = false

    // Защита от троттлинга Android 14: не более 4 перезапусков за 30 секунд
    private val scanRestartTimestamps = mutableListOf<Long>()

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
            Log.w(TAG, "BLE initialization error: ${e.message}")
        }
    }

    /**
     * Проверка наличия необходимых разрешений для работы с BLE
     */
    fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val advGranted = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
            return scanGranted && advGranted
        }
        return true
    }

    /**
     * Запуск локального радио-маяка присутствия (Advertising)
     */
    @SuppressLint("MissingPermission")
    fun startAdvertising(context: Context, deviceId: String, bpm: Int, camelotNum: Int, isMinor: Boolean) {
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

            // Упаковка полезной нагрузки в Manufacturer Specific Data (7 байт)
            // [0..3] = 4 байта хеш deviceId
            // [4] = BPM (40..200)
            // [5] = Camelot (1..12)
            // [6] = 1 если Minor (A), 0 если Major (B)
            val devHash = deviceId.hashCode()
            val msdData = ByteBuffer.allocate(7).apply {
                putInt(devHash)
                put((bpm.coerceIn(40, 220)).toByte())
                put((camelotNum.coerceIn(1, 12)).toByte())
                put(if (isMinor) 1.toByte() else 0.toByte())
            }.array()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(ECHO_MANUFACTURER_ID, msdData)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "startAdvertising failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        } catch (_: Exception) {}
    }

    /**
     * Запуск фонового сканирования с соблюдением ограничений Android 13–15
     */
    @SuppressLint("MissingPermission")
    fun startScanning(context: Context) {
        if (!hasPermissions(context) || bluetoothAdapter?.isEnabled != true) return
        if (scanner == null) {
            scanner = bluetoothAdapter?.bluetoothLeScanner
        }
        if (scanner == null || isScanning) return

        // Проверка лимита перезапусков: не более 4 за последние 30 секунд
        val now = System.currentTimeMillis()
        scanRestartTimestamps.removeAll { now - it > 30000 }
        if (scanRestartTimestamps.size >= 4) {
            Log.w(TAG, "Троттлинг Android: пропуск перезапуска BLE сканера")
            return
        }
        scanRestartTimestamps.add(now)

        try {
            val filter = ScanFilter.Builder()
                .setManufacturerData(ECHO_MANUFACTURER_ID, byteArrayOf())
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build()

            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.i(TAG, "✓ BLE Сканер ЭХО активен (LOW_POWER)")
        } catch (e: Exception) {
            Log.w(TAG, "startScanning failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
        } catch (_: Exception) {}
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val msd = record.getManufacturerSpecificData(ECHO_MANUFACTURER_ID) ?: return
        if (msd.size >= 7) {
            val buffer = ByteBuffer.wrap(msd)
            val devHash = buffer.int
            val bpm = buffer.get().toInt() and 0xFF
            val camNum = buffer.get().toInt() and 0xFF
            val isMinor = buffer.get().toInt() == 1
            val camKey = "$camNum" + (if (isMinor) "A" else "B")

            val rssi = result.rssi
            // Расчет приблизительной дистанции по RSSI
            val distMeters = estimateDistanceByRssi(rssi)

            Log.d(TAG, "📡 Обнаружен BLE сигнал peer: hash=$devHash, BPM=$bpm, Key=$camKey, RSSI=$rssi, Dist=~$distMeters м")
        }
    }

    private fun estimateDistanceByRssi(rssi: Int): Int {
        // Упрощенная логарифмическая модель затухания BLE сигнала
        val txPower = -59 // калиброванный RSSI на расстоянии 1 метр
        if (rssi == 0) return 100
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0).toInt().coerceIn(1, 150)
        } else {
            (0.89976 * Math.pow(ratio, 7.7095) + 0.111).toInt().coerceIn(1, 150)
        }
    }
}
