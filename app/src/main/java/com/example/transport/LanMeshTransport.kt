package com.example.transport

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "LanMeshTransport"
private const val UDP_DISCOVERY_PORT = 45872
private const val DEFAULT_TCP_PORT = 45873

data class DiscoveredLanPeer(
    val nodeId: String,
    val deviceName: String,
    val ipAddress: String,
    val tcpPort: Int,
    val lastSeenMs: Long
)

class LanMeshTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val selfNodeId: String,
    private val getSelfName: () -> String,
    private val onPeerDiscovered: (DiscoveredLanPeer) -> Unit,
    private val onPacketReceived: (wireJson: String, transportLabel: String, senderInfo: String) -> Unit,
    private val onStatusChanged: (active: Boolean, localIp: String?, tcpPort: Int) -> Unit
) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var tcpJob: Job? = null
    private var udpListenJob: Job? = null
    private var udpBeaconJob: Job? = null
    private var localTcpPort: Int = DEFAULT_TCP_PORT

    fun start() {
        stop()

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("aether_mesh_lan")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock unavailable", e)
        }

        tcpJob = scope.launch(Dispatchers.IO) {
            try {
                val server = try {
                    ServerSocket(DEFAULT_TCP_PORT)
                } catch (_: Exception) {
                    ServerSocket(0)
                }
                serverSocket = server
                localTcpPort = server.localPort
                onStatusChanged(true, getPrimaryLocalIpv4(), localTcpPort)

                while (isActive && !server.isClosed) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "TCP accept error", e)
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleIncomingTcpClient(client)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TCP server", e)
                onStatusChanged(false, getPrimaryLocalIpv4(), 0)
            }
        }

        udpListenJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                }
                udpSocket = socket
                val buf = ByteArray(2048)

                while (isActive && !socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        val senderIp = packet.address?.hostAddress ?: continue
                        val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(raw)
                        if (json.optString("kind") == "AETHER_BEACON") {
                            val peerId = json.optString("nodeId")
                            if (peerId.isNotBlank() && peerId != selfNodeId) {
                                val peerName = json.optString("name", peerId)
                                val peerTcpPort = json.optInt("tcpPort", DEFAULT_TCP_PORT)
                                onPeerDiscovered(
                                    DiscoveredLanPeer(
                                        nodeId = peerId,
                                        deviceName = peerName,
                                        ipAddress = senderIp,
                                        tcpPort = peerTcpPort,
                                        lastSeenMs = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive && !socket.isClosed) {
                            Log.w(TAG, "UDP listen packet error", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start UDP discovery listener", e)
            }
        }

        udpBeaconJob = scope.launch(Dispatchers.IO) {
            delay(250)
            while (isActive) {
                sendDiscoveryBeacon()
                onStatusChanged(serverSocket != null, getPrimaryLocalIpv4(), localTcpPort)
                delay(1500)
            }
        }
    }

    fun sendDiscoveryBurst() {
        scope.launch(Dispatchers.IO) {
            sendDiscoveryBeacon()
            delay(350)
            sendDiscoveryBeacon()
        }
    }

    private fun sendDiscoveryBeacon() {
        val beaconBytes = JSONObject().apply {
            put("kind", "AETHER_BEACON")
            put("nodeId", selfNodeId)
            put("name", getSelfName())
            put("tcpPort", localTcpPort)
        }.toString().toByteArray(Charsets.UTF_8)

        val targets = mutableSetOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                for (ifa in nif.interfaceAddresses) {
                    val bcast = ifa.broadcast
                    if (bcast != null && ifa.address is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Enumerating broadcast addresses failed", e)
        }

        try {
            DatagramSocket().use { sender ->
                sender.broadcast = true
                for (addr in targets) {
                    try {
                        sender.send(DatagramPacket(beaconBytes, beaconBytes.size, addr, UDP_DISCOVERY_PORT))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sending UDP beacon failed", e)
        }
    }

    private fun handleIncomingTcpClient(client: Socket) {
        try {
            client.soTimeout = 4000
            val senderIp = client.inetAddress?.hostAddress ?: "LAN"
            BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8)).use { reader ->
                val line = reader.readLine()
                if (!line.isNullOrBlank()) {
                    onPacketReceived(line.trim(), "LAN-TCP", senderIp)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Incoming TCP read error", e)
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun sendEncryptedPacket(
        ipAddress: String,
        tcpPort: Int,
        wireJson: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ipAddress, tcpPort), 2500)
                socket.soTimeout = 3000
                BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)).use { writer ->
                    writer.write(wireJson)
                    writer.newLine()
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "TCP send to $ipAddress:$tcpPort failed: ${e.message}")
            false
        }
    }

    fun getPrimaryLocalIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) return host
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun stop() {
        tcpJob?.cancel()
        udpListenJob?.cancel()
        udpBeaconJob?.cancel()
        tcpJob = null
        udpListenJob = null
        udpBeaconJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {
        }
        udpSocket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
        multicastLock = null
    }
}
