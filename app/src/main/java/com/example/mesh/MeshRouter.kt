package com.example.mesh

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object MeshRouter {

    const val RADAR_RADIUS_KM = 5.0f

    fun buildSelfNode(
        selfId: String,
        selfName: String,
        batteryPct: Int = 100
    ): MeshNode = MeshNode(
        id = selfId,
        label = "$selfName [YOU]",
        role = NodeRole.SELF,
        xKm = 0.0f,
        yKm = 0.0f,
        isOnline = true,
        batteryPct = batteryPct,
        signalDbm = -30,
        transport = "HOST",
        distanceMeters = 0f
    )

    /**
     * Converts a real BLE RSSI value (dBm) into an approximate distance in meters.
     */
    fun estimateDistanceMetersFromRssi(rssiDbm: Int, txPowerAt1m: Int = -59): Float {
        val ratio = (txPowerAt1m - rssiDbm) / 22.0
        return 10.0.pow(ratio).toFloat().coerceIn(0.5f, 4500f)
    }

    /**
     * Deterministically places a real discovered peer onto the radar grid using its node ID bearing
     * and real estimated distance (scaled visibly within the 5 km tactical radar).
     */
    fun computePeerRadarPosition(
        nodeId: String,
        distanceMeters: Float
    ): Pair<Float, Float> {
        val hash = abs(nodeId.hashCode())
        val angleRad = ((hash % 360) * Math.PI / 180.0)
        // Map 0.5m..200m+ real proximity cleanly onto 0.9km..3.8km visual radar ring so nodes remain tappable
        val visualRadiusKm = when {
            distanceMeters <= 0f -> 1.45f
            distanceMeters < 10f -> 0.95f + (distanceMeters / 10f) * 0.55f
            distanceMeters < 80f -> 1.50f + ((distanceMeters - 10f) / 70f) * 1.15f
            else -> (2.65f + (distanceMeters / 1000f)).coerceAtMost(4.2f)
        }
        val x = (cos(angleRad) * visualRadiusKm).toFloat()
        val y = (sin(angleRad) * visualRadiusKm).toFloat()
        return x to y
    }

    fun distanceKm(a: MeshNode, b: MeshNode): Float {
        return hypot(a.xKm - b.xKm, a.yKm - b.yKm)
    }

    fun buildLinks(
        nodes: List<MeshNode>,
        rangeKm: Float,
        activeRoutePath: List<String> = emptyList(),
        isReroutedRoute: Boolean = false
    ): List<MeshLink> {
        val routeEdges = activeRoutePath.zipWithNext().map { (u, v) ->
            if (u < v) u to v else v to u
        }.toSet()

        val result = mutableListOf<MeshLink>()
        for (i in nodes.indices) {
            val a = nodes[i]
            if (!a.isOnline) continue
            for (j in i + 1 until nodes.size) {
                val b = nodes[j]
                if (!b.isOnline) continue
                val dist = distanceKm(a, b)
                if (dist <= rangeKm) {
                    val key = if (a.id < b.id) a.id to b.id else b.id to a.id
                    val onRoute = key in routeEdges
                    val latency = (dist * 12f + 4f).roundToInt().coerceAtLeast(3)
                    result.add(
                        MeshLink(
                            fromId = a.id,
                            toId = b.id,
                            distanceKm = dist,
                            latencyMs = latency,
                            isActiveRoute = onRoute,
                            isRerouteHighlight = onRoute && isReroutedRoute
                        )
                    )
                }
            }
        }
        return result
    }

    fun findShortestPath(
        nodes: List<MeshNode>,
        links: List<MeshLink>,
        sourceId: String,
        targetId: String
    ): RouteResult? {
        val onlineIds = nodes.asSequence().filter { it.isOnline }.map { it.id }.toSet()
        if (sourceId !in onlineIds || targetId !in onlineIds || sourceId == targetId) return null

        val adj = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        for (link in links) {
            if (link.fromId in onlineIds && link.toId in onlineIds) {
                adj.getOrPut(link.fromId) { mutableListOf() }.add(link.toId to link.latencyMs)
                adj.getOrPut(link.toId) { mutableListOf() }.add(link.fromId to link.latencyMs)
            }
        }

        val dist = mutableMapOf<String, Int>().withDefault { Int.MAX_VALUE }
        val prev = mutableMapOf<String, String>()
        val pq = PriorityQueue<Pair<String, Int>>(compareBy { it.second })

        dist[sourceId] = 0
        pq.add(sourceId to 0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break
            if (d > dist.getValue(u)) continue
            if (u == targetId) break

            for ((v, weight) in adj[u].orEmpty()) {
                val alt = d + weight + 4
                if (alt < dist.getValue(v)) {
                    dist[v] = alt
                    prev[v] = u
                    pq.add(v to alt)
                }
            }
        }

        if (targetId !in prev) return null

        val path = mutableListOf<String>()
        var curr: String? = targetId
        while (curr != null) {
            path.add(curr)
            curr = prev[curr]
        }
        path.reverse()

        if (path.firstOrNull() != sourceId) return null

        val totalLatency = dist.getValue(targetId)
        return RouteResult(
            path = path,
            totalLatencyMs = totalLatency,
            hopCount = (path.size - 1).coerceAtLeast(1)
        )
    }

    fun isRouteBroken(
        previousPath: List<String>,
        nodes: List<MeshNode>,
        rangeKm: Float
    ): String? {
        if (previousPath.size < 2) return null
        val nodeMap = nodes.associateBy { it.id }
        for (id in previousPath) {
            val node = nodeMap[id]
            if (node == null || !node.isOnline) {
                return id
            }
        }
        for ((uId, vId) in previousPath.zipWithNext()) {
            val u = nodeMap[uId] ?: return uId
            val v = nodeMap[vId] ?: return vId
            if (distanceKm(u, v) > rangeKm) {
                return "$uId<->$vId"
            }
        }
        return null
    }

    fun computeEfficiencyPct(
        hopCount: Int,
        totalLatencyMs: Int,
        delivered: Boolean
    ): Int {
        if (!delivered || hopCount <= 0) return 0
        val hopPenalty = (hopCount - 1) * 7
        val latencyPenalty = (totalLatencyMs / 7)
        return (100 - hopPenalty - latencyPenalty).coerceIn(18, 99)
    }

    fun countConnectedPeers(
        nodes: List<MeshNode>,
        links: List<MeshLink>,
        sourceId: String
    ): Int {
        val adj = mutableMapOf<String, MutableList<String>>()
        for (link in links) {
            adj.getOrPut(link.fromId) { mutableListOf() }.add(link.toId)
            adj.getOrPut(link.toId) { mutableListOf() }.add(link.fromId)
        }
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        if (nodes.any { it.id == sourceId && it.isOnline }) {
            visited.add(sourceId)
            queue.add(sourceId)
        }
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            for (next in adj[curr].orEmpty()) {
                if (visited.add(next)) {
                    queue.add(next)
                }
            }
        }
        return (visited.size - 1).coerceAtLeast(0)
    }
}
