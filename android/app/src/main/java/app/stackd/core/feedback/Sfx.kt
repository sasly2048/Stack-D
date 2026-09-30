package app.stackd.core.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * UI sound effects synthesized in code — a port of web `src/lib/sfx.ts`, same
 * note tables, no audio assets. Quiet and short (< ~0.6s) so it reads as
 * tactile. Gated by the Sound preference ([enabled], mirrored from
 * SettingsStore by StackdApplication).
 */
object Sfx {
    enum class Kind { TAP, SELECT, OPEN, CLOSE, SUCCESS, ERROR, AUTH, XP, ACHIEVEMENT, NOTIFY, PURCHASE }

    @Volatile var enabled: Boolean = true

    private enum class Wave { SINE, TRIANGLE, SAWTOOTH, SQUARE }

    /** f = Hz, t = start (s), d = duration (s), g = peak gain, to = glide target Hz. */
    private class Note(val f: Double, val t: Double, val d: Double, val w: Wave = Wave.SINE, val g: Double = 0.06, val to: Double? = null)

    private val NOTES: Map<Kind, List<Note>> = mapOf(
        Kind.TAP to listOf(Note(660.0, 0.0, 0.05, Wave.SINE, 0.04)),
        Kind.SELECT to listOf(Note(880.0, 0.0, 0.06, Wave.TRIANGLE, 0.05)),
        Kind.OPEN to listOf(Note(520.0, 0.0, 0.09, Wave.SINE, 0.05, 720.0)),
        Kind.CLOSE to listOf(Note(620.0, 0.0, 0.09, Wave.SINE, 0.045, 440.0)),
        Kind.SUCCESS to listOf(Note(660.0, 0.0, 0.09, Wave.TRIANGLE, 0.06), Note(990.0, 0.07, 0.16, Wave.SINE, 0.06)),
        Kind.ERROR to listOf(Note(300.0, 0.0, 0.12, Wave.SAWTOOTH, 0.05, 180.0), Note(220.0, 0.1, 0.14, Wave.SQUARE, 0.04)),
        Kind.AUTH to listOf(Note(523.0, 0.0, 0.1, Wave.SINE, 0.05), Note(784.0, 0.09, 0.22, Wave.SINE, 0.06)),
        Kind.XP to listOf(Note(1046.0, 0.0, 0.12, Wave.TRIANGLE, 0.05, 1568.0)),
        Kind.ACHIEVEMENT to listOf(
            Note(659.0, 0.0, 0.1, Wave.TRIANGLE, 0.06),
            Note(880.0, 0.08, 0.1, Wave.TRIANGLE, 0.06),
            Note(1318.0, 0.16, 0.26, Wave.SINE, 0.06),
        ),
        Kind.NOTIFY to listOf(Note(880.0, 0.0, 0.07, Wave.SINE, 0.045), Note(1174.0, 0.06, 0.12, Wave.SINE, 0.045)),
        Kind.PURCHASE to listOf(Note(587.0, 0.0, 0.09, Wave.TRIANGLE, 0.05), Note(880.0, 0.07, 0.14, Wave.SINE, 0.055)),
    )

    private const val RATE = 22_050
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = HashMap<Kind, ShortArray>()

    /** Play a UI sound. Silent if sound is off or audio fails. */
    fun play(kind: Kind) {
        if (!enabled) return
        worker.execute {
            runCatching {
                val pcm = cache.getOrPut(kind) { render(NOTES.getValue(kind)) }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Static tracks don't self-release; wait out the clip (worker thread).
                Thread.sleep(pcm.size * 1000L / RATE + 50)
                track.release()
            }
        }
    }

    /** Mixes notes with the web's envelope: 12ms exponential attack, exponential decay to d. */
    private fun render(notes: List<Note>): ShortArray {
        val total = notes.maxOf { it.t + it.d + 0.03 }
        val out = DoubleArray((total * RATE).toInt())
        val floor = 0.0001
        for (n in notes) {
            val start = (n.t * RATE).toInt()
            val len = (n.d * RATE).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val s = i.toDouble() / RATE
                val freq = n.to?.let { n.f * exp(ln(it / n.f) * (s / n.d)) } ?: n.f
                phase += freq / RATE
                val p = phase - phase.toLong()
                val wave = when (n.w) {
                    Wave.SINE -> sin(2 * PI * p)
                    Wave.TRIANGLE -> 1 - 4 * abs(p - 0.5)
                    Wave.SAWTOOTH -> 2 * p - 1
                    Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                }
                val env = if (s < 0.012) {
                    floor * exp(ln(n.g / floor) * (s / 0.012))
                } else {
                    n.g * exp(ln(floor / n.g) * ((s - 0.012) / (n.d - 0.012)))
                }
                val idx = start + i
                if (idx < out.size) out[idx] += wave * env
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }
}
