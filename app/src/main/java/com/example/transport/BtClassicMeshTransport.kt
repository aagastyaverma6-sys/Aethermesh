package com.example.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.mesh.MeshRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

private const val TAG = "BtClassicMeshTransport"
val AETHER_BT_CLASSIC_UUID: UUID = UUID.fromString("0000AE76-0000-1000-8000-00805F9B34FB")

enum class BtClassicStatus {
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    BLUETOOTH_DISABLED,
    STANDBY,
    ACTIVE
}

data class DiscoveredBtClassicPeer(
    val nodeId: String,
    val deviceName: String,
    val btAddress: String,
    val rssiDbm: Int,
    val distanceMeters: Float,
    val isBonded: Boolean,
    val lastSeenMs: Long
)

class BtClassicMeshTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val selfNodeId: String,
    private val getSelfName: () -> String,
    private val onPeerDiscovered: (DiscoveredBtClassicPeer) -> Unit,
    private val onPacketReceived: (wireJson: String, transportLabel: String, senderInfo: String) -> Unit,
    private val onStatusChanged: (BtClassicStatus) -> Unit
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var receiverRegistered = false
    private var isEnabledByUser = false

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
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

    fun checkStatus(): BtClassicStatus {
        val hasBt = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val btAdapter = adapter
        if (!hasBt || btAdapter == null) return BtClassicStatus.UNSUPPORTED
        if (!hasRequiredPermissions()) return BtClassicStatus.PERMISSION_REQUIRED
        if (!btAdapter.isEnabled) return BtClassicStatus.BLUETOOTH_DISABLED
        return if (isEnabledByUser && acceptJob?.isActive == true) {
            BtClassicStatus.ACTIVE
        } else {
            BtClassicStatus.STANDBY
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        isEnabledByUser = true
        val btAdapter = adapter
        if (btAdapter == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            onStatusChanged(BtClassicStatus.UNSUPPORTED)
            return
        }
        if (!hasRequiredPermissions()) {
            onStatusChanged(BtClassicStatus.PERMISSION_REQUIRED)
            return
        }
        if (!btAdapter.isEnabled) {
            onStatusChanged(BtClassicStatus.BLUETOOTH_DISABLED)
            return
        }

        registerDiscoveryReceiver()
        startRfcommServer(btAdapter)
        probeBondedAndDiscover(btAdapter)
        onStatusChanged(BtClassicStatus.ACTIVE)
    }

    @SuppressLint("MissingPermission")
    fun rescan() {
        if (!isEnabledByUser) return
        val btAdapter = adapter ?: return
        if (!hasRequiredPermissions() || !btAdapter.isEnabled) {
            onStatusChanged(checkStatus())
            return
        }
        probeBondedAndDiscover(btAdapter)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        isEnabledByUser = false
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        try {
            if (hasRequiredPermissions() && adapter?.isDiscovering == true) {
                adapter?.cancelDiscovery()
            }
        } catch (_: Exception) {
        }
        unregisterDiscoveryReceiver()
        onStatusChanged(checkStatus())
    }

    @SuppressLint("MissingPermission")
    private fun startRfcommServer(btAdapter: BluetoothAdapter) {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                val server = btAdapter.listenUsingInsecureRfcommWithServiceRecord(
                    "AetherMeshClassic",
                    AETHER_BT_CLASSIC_UUID
                )
                serverSocket = server
                onStatusChanged(BtClassicStatus.ACTIVE)

                while (isActive) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "BT Classic RFCOMM accept ended: ${e.message}")
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleIncomingSocket(client)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open BT Classic RFCOMM server", e)
                onStatusChanged(checkStatus())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleIncomingSocket(socket: BluetoothSocket) {
        try {
            val remote = socket.remoteDevice
            val addr = remote?.address ?: "BT-CLASSIC"
            BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val clean = line.trim()
                    if (clean.isEmpty()) continue
                    if (clean.contains("\"AETHER_BT_HELLO\"")) {
                        runCatching {
                            val obj = JSONObject(clean)
                            val peerId = obj.optString("nodeId")
                            val peerName = obj.optString("name", peerId)
                            if (peerId.isNotBlank() && peerId != selfNodeId) {
                                onPeerDiscovered(
                                    DiscoveredBtClassicPeer(
                                        nodeId = peerId,
                                        deviceName = peerName,
                                        btAddress = addr,
                                        rssiDbm = -52,
                                        distanceMeters = 8f,
                                        isBonded = true,
                                        lastSeenMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    } else {
                        onPacketReceived(clean, "BT-CLASSIC", addr)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "BT Classic client socket closed: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun probeBondedAndDiscover(btAdapter: BluetoothAdapter) {
        // Check bonded phones / tablets / computers for live AetherMeshSPP servers
        scope.launch(Dispatchers.IO) {
            val bonded = try {
                btAdapter.bondedDevices.orEmpty()
            } catch (_: Exception) {
                emptySet()
            }
            for (device in bonded) {
                val major = device.bluetoothClass?.majorDeviceClass
                val isHandheldOrPc = major == BluetoothClass.Device.Major.PHONE ||
                    major == BluetoothClass.Device.Major.COMPUTER ||
                    major == BluetoothClass.Device.Major.UNCATEGORIZED
                if (!isHandheldOrPc) continue
                probeDeviceHandshake(device, isBonded = true, rssiDbm = -54)
            }
        }

        // Kick off active Classic discovery burst for nearby phones/tablets
        try {
            if (btAdapter.isDiscovering) {
                btAdapter.cancelDiscovery()
            }
            btAdapter.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "BT Classic startDiscovery failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun probeDeviceHandshake(
        device: BluetoothDevice,
        isBonded: Boolean,
        rssiDbm: Int
    ) {
        val helloJson = JSONObject().apply {
            put("kind", "AETHER_BT_HELLO")
            put("nodeId", selfNodeId)
            put("name", getSelfName())
        }.toString()

        var sock: BluetoothSocket? = null
        try {
            sock = device.createInsecureRfcommSocketToServiceRecord(AETHER_BT_CLASSIC_UUID)
            sock.connect()
            val writer = BufferedWriter(OutputStreamWriter(sock.outputStream, Charsets.UTF_8))
            writer.write(helloJson)
            writer.newLine()
            writer.flush()

            val nodeId = "BTC-${device.address.takeLast(5).replace(":", "")}"
            val name = try { device.name } catch (_: Exception) { null } ?: nodeId
            onPeerDiscovered(
                DiscoveredBtClassicPeer(
                    nodeId = nodeId,
                    deviceName = name,
                    btAddress = device.address,
                    rssiDbm = rssiDbm,
                    distanceMeters = MeshRouter.estimateDistanceMetersFromRssi(rssiDbm),
                    isBonded = isBonded,
                    lastSeenMs = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {
            // Remote device does not currently expose AetherMeshClassic SPP
        } finally {
            try {
                sock?.close()
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendEncryptedPacket(
        btAddress: String,
        wireJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        val btAdapter = adapter ?: return@withContext false
        if (!hasRequiredPermissions() || !btAdapter.isEnabled) return@withContext false

        val device = try {
            btAdapter.getRemoteDevice(btAddress)
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        var sock: BluetoothSocket? = null
        try {
            if (btAdapter.isDiscovering) {
                btAdapter.cancelDiscovery()
            }
            sock = device.createInsecureRfcommSocketToServiceRecord(AETHER_BT_CLASSIC_UUID)
            sock.connect()
            BufferedWriter(OutputStreamWriter(sock.outputStream, Charsets.UTF_8)).use { writer ->
                writer.write(wireJson)
                writer.newLine()
                writer.flush()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "BT Classic RFCOMM send to $btAddress failed: ${e.message}")
            false
        } finally {
            try {
                sock?.close()
            } catch (_: Exception) {
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_FOUND) return
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device == null) return
            val major = try { device.bluetoothClass?.majorDeviceClass } catch (_: Exception) { null }
            if (major != BluetoothClass.Device.Major.PHONE &&
                major != BluetoothClass.Device.Major.COMPUTER &&
                major != BluetoothClass.Device.Major.UNCATEGORIZED
            ) {
                return
            }
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-62).toShort()).toInt()
            scope.launch(Dispatchers.IO) {
                probeDeviceHandshake(
                    device = device,
                    isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                    rssiDbm = rssi
                )
            }
        }
    }

    private fun registerDiscoveryReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            context,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterDiscoveryReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }
}
