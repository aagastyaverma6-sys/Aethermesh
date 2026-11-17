package com.example.crypto

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPacket(
    val packetId: String,
    val packetType: String,
    val sourceNode: String,
    val targetNode: String,
    val cipherBytes: ByteArray,
    val ivBytes: ByteArray,
    val timestamp: String
) {
    fun toWireJson(ttl: Int = 3, visited: List<String> = listOf(sourceNode)): String {
        return JSONObject().apply {
            put("wire", "AETHER_AES128_V1")
            put("packetId", packetId)
            put("packetType", packetType)
            put("sourceNode", sourceNode)
            put("targetNode", targetNode)
            put("ivHex", TacticalCrypto.bytesToCompactHex(ivBytes))
            put("cipherHex", TacticalCrypto.bytesToCompactHex(cipherBytes))
            put("ttl", ttl)
            put("visited", org.json.JSONArray(visited))
            put("ts", timestamp)
        }.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPacket) return false
        return packetId == other.packetId &&
            cipherBytes.contentEquals(other.cipherBytes) &&
            ivBytes.contentEquals(other.ivBytes)
    }

    override fun hashCode(): Int {
        var result = packetId.hashCode()
        result = 31 * result + cipherBytes.contentHashCode()
        result = 31 * result + ivBytes.contentHashCode()
        return result
    }
}

data class WireEnvelopeResult(
    val packet: EncryptedPacket,
    val ttl: Int,
    val visited: List<String>,
    val decryptedObject: JSONObject?,
    val inspection: EncryptedPacketInspection
)

data class EncryptedPacketInspection(
    val packetId: String,
    val packetType: String,
    val sourceNode: String,
    val targetNode: String,
    val rawCiphertextHex: String,
    val ivHex: String,
    val decryptedJson: String,
    val decryptionVerified: Boolean,
    val statusDetail: String,
    val timestamp: String,
    val keyFingerprint: String
)

object TacticalCrypto {
    // 16-byte (128-bit) shared tactical mesh key for AES-128-CBC
    private val sharedKeyBytes = "AETHER-TAC-K128!".toByteArray(StandardCharsets.UTF_8)
    private val wrongTestKeyBytes = "ROGUE-INTRUDER99".toByteArray(StandardCharsets.UTF_8)
    private val random = SecureRandom()

    const val KEY_FINGERPRINT = "AES-128-CBC // KEY#TAC-01 [AETH..K128]"

    fun encryptJsonPayload(
        packetId: String,
        packetType: String,
        sourceNode: String,
        targetNode: String,
        jsonPayload: JSONObject,
        timestamp: String
    ): EncryptedPacket {
        val ivBytes = ByteArray(16)
        random.nextBytes(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(sharedKeyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(ivBytes))

        val plainBytes = jsonPayload.toString().toByteArray(StandardCharsets.UTF_8)
        val cipherBytes = cipher.doFinal(plainBytes)

        return EncryptedPacket(
            packetId = packetId,
            packetType = packetType,
            sourceNode = sourceNode,
            targetNode = targetNode,
            cipherBytes = cipherBytes,
            ivBytes = ivBytes,
            timestamp = timestamp
        )
    }

    fun parseAndDecryptWireEnvelope(
        wireJsonString: String,
        useWrongKeyForTest: Boolean = false
    ): WireEnvelopeResult? {
        return try {
            val env = JSONObject(wireJsonString)
            if (env.optString("wire") != "AETHER_AES128_V1") return null

            val ivBytes = compactHexToBytes(env.getString("ivHex"))
            val cipherBytes = compactHexToBytes(env.getString("cipherHex"))
            val packet = EncryptedPacket(
                packetId = env.getString("packetId"),
                packetType = env.getString("packetType"),
                sourceNode = env.getString("sourceNode"),
                targetNode = env.getString("targetNode"),
                cipherBytes = cipherBytes,
                ivBytes = ivBytes,
                timestamp = env.optString("ts", "")
            )
            val ttl = env.optInt("ttl", 0)
            val visitedArr = env.optJSONArray("visited")
            val visited = buildList {
                if (visitedArr != null) {
                    for (i in 0 until visitedArr.length()) {
                        add(visitedArr.optString(i))
                    }
                }
            }
            val inspection = inspectAndDecrypt(packet, useWrongKeyForTest)
            val decryptedObj = if (inspection.decryptionVerified) {
                JSONObject(inspection.decryptedJson)
            } else null

            WireEnvelopeResult(
                packet = packet,
                ttl = ttl,
                visited = visited,
                decryptedObject = decryptedObj,
                inspection = inspection
            )
        } catch (_: Exception) {
            null
        }
    }

    fun inspectAndDecrypt(
        packet: EncryptedPacket,
        useWrongKeyForTest: Boolean = false
    ): EncryptedPacketInspection {
        val keyToUse = if (useWrongKeyForTest) wrongTestKeyBytes else sharedKeyBytes
        val rawHex = formatHexBlocks(packet.ivBytes + packet.cipherBytes)
        val ivHex = formatHexBlocks(packet.ivBytes)

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyToUse, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(packet.ivBytes))
            val decryptedBytes = cipher.doFinal(packet.cipherBytes)
            val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)
            val prettyJson = JSONObject(decryptedText).toString(2)

            EncryptedPacketInspection(
                packetId = packet.packetId,
                packetType = packet.packetType,
                sourceNode = packet.sourceNode,
                targetNode = packet.targetNode,
                rawCiphertextHex = rawHex,
                ivHex = ivHex,
                decryptedJson = prettyJson,
                decryptionVerified = true,
                statusDetail = "AES-128 DECRYPTED & VERIFIED AT ${packet.targetNode}",
                timestamp = packet.timestamp,
                keyFingerprint = KEY_FINGERPRINT
            )
        } catch (e: Exception) {
            EncryptedPacketInspection(
                packetId = packet.packetId,
                packetType = packet.packetType,
                sourceNode = packet.sourceNode,
                targetNode = packet.targetNode,
                rawCiphertextHex = rawHex,
                ivHex = ivHex,
                decryptedJson = "{\n  \"error\": \"PKCS5_PADDING_OR_KEY_MISMATCH\",\n  \"detail\": \"${e.javaClass.simpleName}: Unable to decrypt with mismatched tactical key\"\n}",
                decryptionVerified = false,
                statusDetail = "DECRYPTION FAILED — KEY MISMATCH / TAMPER DETECTED",
                timestamp = packet.timestamp,
                keyFingerprint = if (useWrongKeyForTest) "AES-128-CBC // WRONG_KEY_SIM" else KEY_FINGERPRINT
            )
        }
    }

    fun bytesToCompactHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun compactHexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    fun formatHexBlocks(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        bytes.forEachIndexed { index, byte ->
            sb.append(String.format("%02X", byte.toInt() and 0xFF))
            if (index < bytes.lastIndex) {
                sb.append(if ((index + 1) % 8 == 0) "\n" else " ")
            }
        }
        return sb.toString()
    }
}
