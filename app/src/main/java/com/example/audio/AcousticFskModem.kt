package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "AcousticFskModem"

data class AcousticModemState(
    val message: String = "TRAPPED 2ND FLOOR NEED MEDIC",
    val isHighBandUltrasonic: Boolean = false,
    val isTransmitting: Boolean = false,
    val bitStream: String = encodeToBinary("TRAPPED 2ND FLOOR NEED MEDIC"),
    val morseCode: String = encodeToMorse("TRAPPED 2ND FLOOR NEED MEDIC"),
    val activeBitIndex: Int = -1,
    val progress: Float = 0f,
    val waveformSamples: List<Float> = List(64) { 0f },
    val statusText: String = "MODEM STANDBY // FSK READY"
) {
    val spaceFreqHz: Int get() = if (isHighBandUltrasonic) 6800 else 1200
    val markFreqHz: Int get() = if (isHighBandUltrasonic) 8200 else 2200
}

private val MORSE_MAP = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
    'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
    'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
    'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
    'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
    'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
    '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
    '9' to "----."
)

fun encodeToBinary(text: String): String {
    if (text.isBlank()) return ""
    return text.uppercase().take(48).map { ch ->
        ch.code.and(0xFF).toString(2).padStart(8, '0')
    }.joinToString(" ")
}

fun encodeToMorse(text: String): String {
    if (text.isBlank()) return ""
    return text.uppercase().take(48).mapNotNull { ch ->
        when {
            ch == ' ' -> "/"
            MORSE_MAP.containsKey(ch) -> MORSE_MAP[ch]
            else -> null
        }
    }.joinToString(" ")
}

class AcousticFskModem(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AcousticModemState())
    val state: StateFlow<AcousticModemState> = _state.asStateFlow()

    private var txJob: Job? = null

    fun updateMessage(newText: String) {
        val sanitized = newText.take(64)
        _state.update {
            it.copy(
                message = sanitized,
                bitStream = encodeToBinary(sanitized),
                morseCode = encodeToMorse(sanitized)
            )
        }
    }

    fun setHighBandMode(highBand: Boolean) {
        _state.update {
            it.copy(
                isHighBandUltrasonic = highBand,
                statusText = if (highBand) {
                    "CARRIER: 6.8/8.2 kHz NEAR-ULTRASONIC"
                } else {
                    "CARRIER: 1.2/2.2 kHz AUDIBLE FSK"
                }
            )
        }
    }

    fun transmitPulse(onComplete: (() -> Unit)? = null) {
        val currentMsg = _state.value.message.trim()
        if (currentMsg.isEmpty()) return

        stop()

        val rawBits = currentMsg.uppercase().take(48).flatMap { ch ->
            val code = ch.code and 0xFF
            (7 downTo 0).map { shift -> ((code shr shift) and 1) == 1 }
        }
        val spaceHz = _state.value.spaceFreqHz.toDouble()
        val markHz = _state.value.markFreqHz.toDouble()

        _state.update {
            it.copy(
                isTransmitting = true,
                activeBitIndex = 0,
                progress = 0f,
                statusText = "TX FSK ACTIVE // ${spaceHz.toInt()}/${markHz.toInt()} Hz"
            )
        }

        txJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val bitDurationMs = 16 // ~62.5 bps crisp audible FSK telemetry
            val samplesPerBit = (sampleRate * bitDurationMs) / 1000
            var audioTrack: AudioTrack? = null

            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(samplesPerBit * 4)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack unavailable, running visual FSK synthesis fallback", e)
                audioTrack = null
            }

            try {
                var phase = 0.0
                val pcmBuffer = ShortArray(samplesPerBit)
                val totalBits = rawBits.size.coerceAtLeast(1)

                for (bitIndex in rawBits.indices) {
                    if (!isActive) break
                    val bit = rawBits[bitIndex]
                    val freq = if (bit) markHz else spaceHz
                    val phaseInc = 2.0 * PI * freq / sampleRate

                    val visualSamples = ArrayList<Float>(64)
                    val stride = (samplesPerBit / 64).coerceAtLeast(1)

                    for (i in 0 until samplesPerBit) {
                        val env = when {
                            i < 24 -> i / 24.0
                            i > samplesPerBit - 24 -> (samplesPerBit - i) / 24.0
                            else -> 1.0
                        }
                        val sampleFloat = (sin(phase) * env * 0.65).toFloat()
                        pcmBuffer[i] = (sampleFloat * Short.MAX_VALUE).toInt().toShort()
                        phase += phaseInc
                        if (phase > 2.0 * PI) phase -= 2.0 * PI
                        if (i % stride == 0 && visualSamples.size < 64) {
                            visualSamples.add(sampleFloat)
                        }
                    }

                    audioTrack?.write(pcmBuffer, 0, pcmBuffer.size)
                    if (audioTrack == null) {
                        delay(bitDurationMs.toLong())
                    }

                    _state.update {
                        it.copy(
                            activeBitIndex = bitIndex,
                            progress = (bitIndex + 1).toFloat() / totalBits.toFloat(),
                            waveformSamples = if (visualSamples.size >= 8) visualSamples else it.waveformSamples
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {
                }
                _state.update {
                    it.copy(
                        isTransmitting = false,
                        activeBitIndex = -1,
                        progress = 0f,
                        waveformSamples = List(64) { 0f },
                        statusText = "TX COMPLETE // ${rawBits.size} BITS SENT"
                    )
                }
                onComplete?.invoke()
            }
        }
    }

    fun stop() {
        txJob?.cancel()
        txJob = null
        _state.update {
            it.copy(
                isTransmitting = false,
                activeBitIndex = -1,
                progress = 0f,
                waveformSamples = List(64) { 0f },
                statusText = "MODEM STANDBY // FSK READY"
            )
        }
    }
}
