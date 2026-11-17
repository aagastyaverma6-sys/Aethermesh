package com.example.transport

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

private const val TAG = "WifiP2pMeshTransport"

enum class WifiP2pStatus {
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    WIFI_DISABLED,
    STANDBY,
    DISCOVERING,
    CONNECTED_GROUP_OWNER,
    CONNECTED_CLIENT
}

data class DiscoveredWifiDirectPeer(
    val nodeId: String,
    val deviceName: String,
    val deviceAddress: String,
    val isConnected: Boolean,
    val isInvited: Boolean,
    val groupOwnerAddress: String?,
    val lastSeenMs: Long
)

class WifiP2pMeshTransport(
    private val context: Context,
    private val onPeersUpdated: (List<DiscoveredWifiDirectPeer>) -> Unit,
    private val onGroupConnectionUpdated: (groupFormed: Boolean, isGroupOwner: Boolean, groupOwnerIp: String?) -> Unit,
    private val onStatusChanged: (WifiP2pStatus) -> Unit,
    private val onEventLog: (tag: String, message: String, isError: Boolean) -> Unit
) {
    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var isEnabledByUser = false
    private var isP2pHardwareEnabled = true
    private var currentGroupOwnerIp: String? = null
    private var currentIsGroupOwner: Boolean = false
    private var currentGroupFormed: Boolean = false

    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkStatus(): WifiP2pStatus {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        if (!hasFeature || manager == null) return WifiP2pStatus.UNSUPPORTED
        if (!hasRequiredPermissions()) return WifiP2pStatus.PERMISSION_REQUIRED
        if (!isP2pHardwareEnabled) return WifiP2pStatus.WIFI_DISABLED
        if (currentGroupFormed) {
            return if (currentIsGroupOwner) {
                WifiP2pStatus.CONNECTED_GROUP_OWNER
            } else {
                WifiP2pStatus.CONNECTED_CLIENT
            }
        }
        return if (isEnabledByUser) WifiP2pStatus.DISCOVERING else WifiP2pStatus.STANDBY
    }

    @SuppressLint("MissingPermission")
    fun start() {
        isEnabledByUser = true
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        if (!hasFeature || manager == null) {
            onStatusChanged(WifiP2pStatus.UNSUPPORTED)
            return
        }
        if (!hasRequiredPermissions()) {
            onStatusChanged(WifiP2pStatus.PERMISSION_REQUIRED)
            return
        }
        if (channel == null) {
            channel = manager.initialize(context, Looper.getMainLooper(), null)
        }
        registerReceiver()
        discoverPeers()
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) {
            onStatusChanged(WifiP2pStatus.PERMISSION_REQUIRED)
            return
        }
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatusChanged(checkStatus())
                onEventLog("WIFI_P2P", "Wi-Fi Direct peer discovery started.", false)
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverPeers failed reason=$reason")
                onStatusChanged(checkStatus())
                onEventLog("WIFI_P2P", "Wi-Fi Direct discovery failed (code $reason). Ensure Wi-Fi is ON.", true)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createGroup() {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) {
            onStatusChanged(WifiP2pStatus.PERMISSION_REQUIRED)
            return
        }
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onEventLog("WIFI_P2P", "Created Wi-Fi Direct Group Owner node for peer devices.", false)
                mgr.requestConnectionInfo(ch, connectionInfoListener)
            }

            override fun onFailure(reason: Int) {
                onEventLog("WIFI_P2P", "Wi-Fi Direct group creation failed (code $reason).", true)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String) {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermissions()) {
            onStatusChanged(WifiP2pStatus.PERMISSION_REQUIRED)
            return
        }
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onEventLog("WIFI_P2P", "Initiated Wi-Fi Direct link to $deviceAddress...", false)
            }

            override fun onFailure(reason: Int) {
                onEventLog("WIFI_P2P", "Wi-Fi Direct connect failed (code $reason).", true)
            }
        })
    }

    fun stop() {
        isEnabledByUser = false
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null && hasRequiredPermissions()) {
            try {
                mgr.stopPeerDiscovery(ch, null)
                if (currentGroupFormed) {
                    mgr.removeGroup(ch, null)
                }
            } catch (_: Exception) {
            }
        }
        currentGroupFormed = false
        currentGroupOwnerIp = null
        currentIsGroupOwner = false
        unregisterReceiver()
        onPeersUpdated(emptyList())
        onGroupConnectionUpdated(false, false, null)
        onStatusChanged(checkStatus())
    }

    @SuppressLint("MissingPermission")
    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val now = System.currentTimeMillis()
        val mapped = peerList.deviceList.map { device ->
            val shortMac = device.deviceAddress.takeLast(5).replace(":", "").uppercase(Locale.US)
            DiscoveredWifiDirectPeer(
                nodeId = "WFD-$shortMac",
                deviceName = device.deviceName.ifBlank { "Wi-Fi Direct $shortMac" },
                deviceAddress = device.deviceAddress,
                isConnected = device.status == WifiP2pDevice.CONNECTED,
                isInvited = device.status == WifiP2pDevice.INVITED,
                groupOwnerAddress = if (device.status == WifiP2pDevice.CONNECTED) currentGroupOwnerIp else null,
                lastSeenMs = now
            )
        }
        onPeersUpdated(mapped)
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info: WifiP2pInfo? ->
        if (info == null) return@ConnectionInfoListener
        val goIp = info.groupOwnerAddress?.hostAddress
        currentGroupFormed = info.groupFormed
        currentIsGroupOwner = info.isGroupOwner
        currentGroupOwnerIp = goIp
        onGroupConnectionUpdated(info.groupFormed, info.isGroupOwner, goIp)
        onStatusChanged(checkStatus())
        if (info.groupFormed) {
            onEventLog(
                "WIFI_P2P",
                "Wi-Fi Direct link UP (${if (info.isGroupOwner) "GROUP OWNER" else "CLIENT"} @ ${goIp ?: "p2p0"}).",
                false
            )
        }
    }

    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isP2pHardwareEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                    onStatusChanged(checkStatus())
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    if (hasRequiredPermissions()) {
                        manager?.requestPeers(ch, peerListListener)
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    if (hasRequiredPermissions()) {
                        manager?.requestConnectionInfo(ch, connectionInfoListener)
                        manager?.requestPeers(ch, peerListListener)
                    }
                }
            }
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            p2pReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(p2pReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }
}
