package com.example.mesh

import androidx.compose.runtime.Immutable

enum class NodeRole {
    SELF,
    REAL_PEER,
    GATEWAY
}

@Immutable
data class MeshNode(
    val id: String,
    val label: String,
    val role: NodeRole,
    val xKm: Float,
    val yKm: Float,
    val isOnline: Boolean = true,
    val batteryPct: Int = 100,
    val signalDbm: Int = -50,
    val transport: String = "LOCAL",
    val ipAddress: String? = null,
    val tcpPort: Int? = null,
    val bleAddress: String? = null,
    val distanceMeters: Float = 0f,
    val lastSeenMs: Long = 0L
)

@Immutable
data class MeshLink(
    val fromId: String,
    val toId: String,
    val distanceKm: Float,
    val latencyMs: Int,
    val isActiveRoute: Boolean = false,
    val isRerouteHighlight: Boolean = false
)

enum class PinCategory(val label: String, val shortCode: String) {
    BLOCKED_ROAD("Blocked Road", "BLK-RD"),
    MEDICAL_AID("Medical Aid", "MED-AID"),
    SAFE_SHELTER("Safe Shelter", "SHELTER"),
    CLEAN_WATER("Clean Water", "H2O-OK")
}

@Immutable
data class TacticalPin(
    val id: String,
    val category: PinCategory,
    val xKm: Float,
    val yKm: Float,
    val authorNode: String,
    val timestamp: String,
    val propagatedCount: Int
)

enum class LogSeverity {
    INFO,
    SUCCESS,
    WARNING,
    CRITICAL,
    REROUTE
}

@Immutable
data class TerminalLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String,
    val severity: LogSeverity
)

@Immutable
data class P2pCommsMessage(
    val id: Long,
    val senderId: String,
    val senderName: String,
    val body: String,
    val transport: String,
    val isOutgoing: Boolean,
    val isSos: Boolean,
    val encryptedVerified: Boolean,
    val timestamp: String
)

@Immutable
data class PacketPulseState(
    val isActive: Boolean = false,
    val path: List<String> = emptyList(),
    val currentSegmentIndex: Int = 0,
    val segmentProgress: Float = 0f,
    val packetId: String = "",
    val isRerouted: Boolean = false
)

@Immutable
data class RouteResult(
    val path: List<String>,
    val totalLatencyMs: Int,
    val hopCount: Int
)

@Immutable
data class MeshEfficiencySample(
    val sequence: Int,
    val hopCount: Int,
    val latencyMs: Int,
    val efficiencyPct: Int,
    val delivered: Boolean
)
