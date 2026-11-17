package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.mesh.MeshRouter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BleMeshTransport"

val AETHER_BLE_SERVICE_UUID: UUID = UUID.fromString("0000AE74-0000-1000-8000-00805F9B34FB")
val AETHER_BLE_PACKET_CHAR_UUID: UUID = UUID.fromString("0000AE75-0000-1000-8000-00805F9B34FB")

data class DiscoveredBlePeer(
    val nodeId: String,
    val deviceName: String,
    val bleAddress: String,
    val rssiDbm: Int,
    val distanceMeters: Float,
    val isCodedPhy: Boolean,
    val lastSeenMs: Long
)

enum class BleRadioStatus {
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    ACTIVE
}

data class BleCapabilities(
    val bleSupported: Boolean,
    val codedPhySupported: Boolean,
    val extendedAdvSupported: Boolean,
    val hypRangeActive: Boolean
)

class BleMeshTransport(
    private val context: Context,
    private val selfNodeId: String,
    private val getSelfName: () -> String,
    private val onPeerDiscovered: (DiscoveredBlePeer) -> Unit,
    private val onPacketReceived: (wireJson: String, transportLabel: String, senderInfo: String) -> Unit,
    private val onStatusChanged: (BleRadioStatus, BleCapabilities) -> Unit
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var isRunning = false
    private var hypRangeEnabled = false
    private var codedAdvController: CodedPhyAdvertisingHelper? = null

    private val incomingChunkBuffers = ConcurrentHashMap<String, StringBuilder>()

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getCapabilities(): BleCapabilities {
        val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val btAdapter = adapter
        val codedSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) {
            runCatching { btAdapter.isLeCodedPhySupported }.getOrDefault(false)
        } else {
            false
        }
        val extAdvSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && btAdapter != null) {
            runCatching { btAdapter.isLeExtendedAdvertisingSupported }.getOrDefault(false)
        } else {
            false
        }
        return BleCapabilities(
            bleSupported = hasBleFeature && btAdapter != null,
            codedPhySupported = codedSupported,
            extendedAdvSupported = extAdvSupported,
            hypRangeActive = hypRangeEnabled && codedSupported && extAdvSupported && isRunning
        )
    }

    fun checkStatus(): BleRadioStatus {
        val hasBleFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val btAdapter = adapter
        if (!hasBleFeature || btAdapter == null) return BleRadioStatus.UNSUPPORTED
        if (!hasRequiredPermissions()) return BleRadioStatus.PERMISSION_REQUIRED
        if (!btAdapter.isEnabled) return BleRadioStatus.BLUETOOTH_DISABLED
        return BleRadioStatus.ACTIVE
    }

    fun setHypRangeEnabled(enabled: Boolean) {
        if (hypRangeEnabled == enabled) return
        hypRangeEnabled = enabled
        if (isRunning) {
            rescan()
        } else {
            onStatusChanged(checkStatus(), getCapabilities())
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val status = checkStatus()
        onStatusChanged(status, getCapabilities())
        if (status != BleRadioStatus.ACTIVE) {
            return
        }
        if (isRunning) return

        val btAdapter = adapter ?: return
        isRunning = true

        // 1. Start GATT Server for receiving AES-128 encrypted mesh packets
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                AETHER_BLE_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val rxChar = BluetoothGattCharacteristic(
                AETHER_BLE_PACKET_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(rxChar)
            gattServer?.addService(service)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open BLE GATT server", e)
        }

        // 2. Start BLE Advertising (HypRange BLE 5+ Coded PHY S=8 when requested & supported, else BLE 1M)
        advertiser = btAdapter.bluetoothLeAdvertiser
        val caps = getCapabilities()
        val payloadTag = "${selfNodeId.take(8)}|${getSelfName().take(12)}".toByteArray(Charsets.UTF_8)
        var startedExtended = false

        if (hypRangeEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && caps.codedPhySupported && caps.extendedAdvSupported) {
            val adv = advertiser
            if (adv != null) {
                val helper = CodedPhyAdvertisingHelper(selfNodeId)
                startedExtended = helper.startCodedAdvertising(adv, payloadTag)
                if (startedExtended) {
                    codedAdvController = helper
                }
            }
        }

        if (!startedExtended) {
            try {
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build()

                val advData = AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(AETHER_BLE_SERVICE_UUID))
                    .setIncludeDeviceName(false)
                    .build()

                val scanResponse = AdvertiseData.Builder()
                    .addServiceData(ParcelUuid(AETHER_BLE_SERVICE_UUID), payloadTag)
                    .build()

                advertiser?.startAdvertising(settings, advData, scanResponse, advertiseCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Legacy BLE Advertising failed to start", e)
            }
        }

        // 3. Start BLE Scanner (supports Coded PHY + 1M beacons simultaneously on API 26+)
        try {
            scanner = btAdapter.bluetoothLeScanner
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(AETHER_BLE_SERVICE_UUID))
                .build()
            val scanSettingsBuilder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (hypRangeEnabled || caps.codedPhySupported)) {
                scanSettingsBuilder
                    .setLegacy(false)
                    .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            }
            scanner?.startScan(listOf(filter), scanSettingsBuilder.build(), scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "BLE Scanner failed to start", e)
        }

        onStatusChanged(checkStatus(), getCapabilities())
    }

    @SuppressLint("MissingPermission")
    fun rescan() {
        stop()
        start()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        isRunning = false
        try {
            if (hasRequiredPermissions()) {
                scanner?.stopScan(scanCallback)
                advertiser?.stopAdvertising(advertiseCallback)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    advertiser?.let { codedAdvController?.stopCodedAdvertising(it) }
                }
                gattServer?.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping BLE transport", e)
        }
        codedAdvController = null
        scanner = null
        advertiser = null
        gattServer = null
        incomingChunkBuffers.clear()
        onStatusChanged(checkStatus(), getCapabilities())
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE 1M Advertising active for $selfNodeId")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE Advertising error code=$errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val rawServiceData = result.scanRecord?.getServiceData(ParcelUuid(AETHER_BLE_SERVICE_UUID))
            val tag = rawServiceData?.toString(Charsets.UTF_8)
            val parts = tag?.split("|")
            val nodeId = parts?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "BLE-${device.address.takeLast(5)}"
            if (nodeId == selfNodeId) return

            val friendlyName = parts?.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?: (try { device.name } catch (_: Exception) { null })
                ?: nodeId

            val isCodedPhy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.primaryPhy == BluetoothDevice.PHY_LE_CODED ||
                    result.secondaryPhy == BluetoothDevice.PHY_LE_CODED
            } else {
                false
            }

            val distMeters = MeshRouter.estimateDistanceMetersFromRssi(
                rssiDbm = result.rssi,
                isCodedPhy = isCodedPhy
            )
            onPeerDiscovered(
                DiscoveredBlePeer(
                    nodeId = nodeId,
                    deviceName = friendlyName,
                    bleAddress = device.address,
                    rssiDbm = result.rssi,
                    distanceMeters = distMeters,
                    isCodedPhy = isCodedPhy,
                    lastSeenMs = System.currentTimeMillis()
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed errorCode=$errorCode")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == AETHER_BLE_PACKET_CHAR_UUID && value != null) {
                val chunk = String(value, Charsets.UTF_8)
                val buf = incomingChunkBuffers.getOrPut(device.address) { StringBuilder() }
                buf.append(chunk)

                val full = buf.toString()
                val newlineIdx = full.indexOf('\n')
                if (newlineIdx >= 0) {
                    val frame = full.substring(0, newlineIdx).trim()
                    val remainder = full.substring(newlineIdx + 1)
                    buf.clear()
                    buf.append(remainder)
                    if (frame.isNotEmpty()) {
                        val label = if (hypRangeEnabled) "HYPRANGE-GATT" else "BLE-GATT"
                        onPacketReceived(frame, label, device.address)
                    }
                } else if (buf.length > 8192) {
                    buf.clear()
                }
            }
            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendEncryptedPacket(
        bleAddress: String,
        wireJson: String,
        preferCodedPhy: Boolean = hypRangeEnabled
    ): Boolean = withContext(Dispatchers.IO) {
        if (checkStatus() != BleRadioStatus.ACTIVE) return@withContext false
        val device = try {
            adapter?.getRemoteDevice(bleAddress)
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        val payloadBytes = ("$wireJson\n").toByteArray(Charsets.UTF_8)
        val completion = CompletableDeferred<Boolean>()
        var negotiatedChunkSize = 20
        var offset = 0
        var activeGatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!completion.isCompleted) completion.complete(false)
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && mtu > 23) {
                    negotiatedChunkSize = (mtu - 3).coerceAtMost(240)
                }
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!completion.isCompleted) completion.complete(false)
                    return
                }
                val char = gatt.getService(AETHER_BLE_SERVICE_UUID)
                    ?.getCharacteristic(AETHER_BLE_PACKET_CHAR_UUID)
                if (char == null) {
                    if (!completion.isCompleted) completion.complete(false)
                    return
                }
                writeNextChunk(gatt, char)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!completion.isCompleted) completion.complete(false)
                    return
                }
                if (offset >= payloadBytes.size) {
                    if (!completion.isCompleted) completion.complete(true)
                } else {
                    writeNextChunk(gatt, characteristic)
                }
            }

            private fun writeNextChunk(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
                val end = (offset + negotiatedChunkSize).coerceAtMost(payloadBytes.size)
                val slice = payloadBytes.copyOfRange(offset, end)
                offset = end
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val code = gatt.writeCharacteristic(
                        char,
                        slice,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    if (code != BluetoothGatt.GATT_SUCCESS && !completion.isCompleted) {
                        completion.complete(false)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    char.value = slice
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    val ok = gatt.writeCharacteristic(char)
                    if (!ok && !completion.isCompleted) {
                        completion.complete(false)
                    }
                }
            }
        }

        try {
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && preferCodedPhy && getCapabilities().codedPhySupported) {
                device.connectGatt(
                    context,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_CODED_MASK
                )
            } else {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            }
            val result = withTimeoutOrNull(8500L) { completion.await() } ?: false
            result
        } catch (e: Exception) {
            Log.w(TAG, "BLE GATT send to $bleAddress failed", e)
            false
        } finally {
            try {
                activeGatt?.disconnect()
                activeGatt?.close()
            } catch (_: Exception) {
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private class CodedPhyAdvertisingHelper(
    private val selfNodeId: String
) {
    private val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?,
            txPower: Int,
            status: Int
        ) {
            Log.d(TAG, "HypRange Coded PHY AdvertisingSet status=$status txPower=$txPower node=$selfNodeId")
        }
    }

    @SuppressLint("MissingPermission")
    fun startCodedAdvertising(
        advertiser: BluetoothLeAdvertiser,
        payloadTag: ByteArray
    ): Boolean {
        val advData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(AETHER_BLE_SERVICE_UUID))
            .addServiceData(ParcelUuid(AETHER_BLE_SERVICE_UUID), payloadTag)
            .setIncludeDeviceName(false)
            .build()

        // Try full Coded PHY primary + secondary first for maximum HypRange
        return try {
            val params = AdvertisingSetParameters.Builder()
                .setLegacyMode(false)
                .setConnectable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .setPrimaryPhy(BluetoothDevice.PHY_LE_CODED)
                .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
                .build()
            advertiser.startAdvertisingSet(params, advData, null, null, null, callback)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Primary Coded PHY rejected, trying 1M primary + Coded secondary", e)
            try {
                val fallbackParams = AdvertisingSetParameters.Builder()
                    .setLegacyMode(false)
                    .setConnectable(true)
                    .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                    .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                    .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
                    .setSecondaryPhy(BluetoothDevice.PHY_LE_CODED)
                    .build()
                advertiser.startAdvertisingSet(fallbackParams, advData, null, null, null, callback)
                true
            } catch (e2: Exception) {
                Log.w(TAG, "Extended Coded PHY advertising unsupported, falling back to legacy BLE", e2)
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopCodedAdvertising(advertiser: BluetoothLeAdvertiser) {
        try {
            advertiser.stopAdvertisingSet(callback)
        } catch (_: Exception) {
        }
    }
}
