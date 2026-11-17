package com.example

import com.example.audio.encodeToBinary
import com.example.audio.encodeToMorse
import com.example.crypto.TacticalCrypto
import com.example.mesh.MeshNode
import com.example.mesh.MeshRouter
import com.example.mesh.NodeRole
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AetherMeshLogicTest {

    @Test
    fun aes128WireEnvelopeRoundtrip() {
        val payload = JSONObject().apply {
            put("type", "P2P_CHAT")
            put("src", "AETH-1111")
            put("src_name", "Phone")
            put("msg", "HELLO TABLET")
        }

        val encrypted = TacticalCrypto.encryptJsonPayload(
            packetId = "MSG-101",
            packetType = "P2P_MSG",
            sourceNode = "AETH-1111",
            targetNode = "AETH-2222",
            jsonPayload = payload,
            timestamp = "06:10:00"
        )

        val wireJson = encrypted.toWireJson(ttl = 3, visited = listOf("AETH-1111"))
        val parsed = TacticalCrypto.parseAndDecryptWireEnvelope(wireJson, useWrongKeyForTest = false)
        assertNotNull(parsed)
        assertTrue(parsed!!.inspection.decryptionVerified)
        assertEquals("HELLO TABLET", parsed.decryptedObject!!.getString("msg"))

        val badParsed = TacticalCrypto.parseAndDecryptWireEnvelope(wireJson, useWrongKeyForTest = true)
        assertNotNull(badParsed)
        assertFalse(badParsed!!.inspection.decryptionVerified)
    }

    @Test
    fun adaptiveRoutingReroutesAcrossRealPeersWhenPrimaryOffline() {
        val self = MeshRouter.buildSelfNode("AETH-0000", "Phone")
        val relayA = MeshNode("AETH-AAAA", "TabletA", NodeRole.REAL_PEER, 1.0f, 0.0f, isOnline = true)
        val relayB = MeshNode("AETH-BBBB", "TabletB", NodeRole.REAL_PEER, 0.5f, 1.1f, isOnline = true)
        val gateway = MeshNode("AETH-CCCC", "RescueTab", NodeRole.REAL_PEER, 2.0f, 0.8f, isOnline = true)

        val nodes = listOf(self, relayA, relayB, gateway)
        val links = MeshRouter.buildLinks(nodes, rangeKm = 1.8f)
        val primary = MeshRouter.findShortestPath(nodes, links, "AETH-0000", "AETH-CCCC")
        assertNotNull(primary)
        assertTrue(primary!!.path.contains("AETH-AAAA"))

        // Take relayA offline and verify adaptive reroute via relayB
        val afterOutage = nodes.map { if (it.id == "AETH-AAAA") it.copy(isOnline = false) else it }
        val brokenAt = MeshRouter.isRouteBroken(primary.path, afterOutage, 1.8f)
        assertEquals("AETH-AAAA", brokenAt)

        val newLinks = MeshRouter.buildLinks(afterOutage, rangeKm = 1.8f)
        val reroute = MeshRouter.findShortestPath(afterOutage, newLinks, "AETH-0000", "AETH-CCCC")
        assertNotNull(reroute)
        assertFalse(reroute!!.path.contains("AETH-AAAA"))
        assertTrue(reroute.path.contains("AETH-BBBB"))
    }

    @Test
    fun fskAndRssiDistanceEstimationWork() {
        assertEquals("01010011 01001111 01010011", encodeToBinary("SOS"))
        assertEquals("... --- ...", encodeToMorse("SOS"))
        val nearMeters = MeshRouter.estimateDistanceMetersFromRssi(-59)
        val farMeters = MeshRouter.estimateDistanceMetersFromRssi(-85)
        assertTrue(farMeters > nearMeters)
    }
}
