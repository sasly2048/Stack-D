package app.stackd.core.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/**
 * UI sound effects synthesized in code — no audio assets. Same semantic
 * vocabulary as web `src/lib/sfx.ts` (tap / select / open / success …), with
 * richer voices than raw oscillators:
 *  - BELL: two-operator FM with a decaying index plus an inharmonic partial —
 *    the glassy, premium chime used for musical moments.
 *  - PLUCK: Karplus-Strong string — warm, woody selection ticks.
 *  - CLICK: band-limited noise burst — a physical tap, not a beep.
 *  - PAD: detuned sine pair with a slow attack, for drones.
 * Musical kinds get a short Schroeder reverb tail for space; taps stay dry.
 * Each clip is peak-normalised per kind so loudness is designed, not accidental.
 *
 * Gated by the Sound preference ([enabled], mirrored from SettingsStore).
 */
object Sfx {
    enum class Kind { TAP, SELECT, OPEN, CLOSE, SUCCESS, ERROR, AUTH, XP, ACHIEVEMENT, NOTIFY, PURCHASE, CELEBRATE_PRO, CELEBRATE_ELITE, STARTUP }

    @Volatile var enabled: Boolean = true

    private enum class Voice { SINE, TRIANGLE, BELL, PLUCK, CLICK, PAD }

    /** f = Hz (cutoff for CLICK), t = start s, d = length s, g = relative gain, to = glide target Hz. */
    private class Note(val f: Double, val t: Double, val d: Double, val v: Voice, val g: Double = 1.0, val to: Double? = null)

    /** A designed sound: its notes, reverb send, and target peak (0..1). */
    private class Patch(val notes: List<Note>, val wet: Double, val peak: Double)

    private val PATCHES: Map<Kind, Patch> = mapOf(
        Kind.TAP to Patch(
            listOf(Note(4200.0, 0.0, 0.012, Voice.CLICK, 1.0), Note(2350.0, 0.0, 0.022, Voice.SINE, 0.25)),
            wet = 0.0, peak = 0.16,
        ),
        Kind.SELECT to Patch(
            listOf(Note(1174.66, 0.0, 0.16, Voice.PLUCK, 1.0), Note(5200.0, 0.0, 0.008, Voice.CLICK, 0.35)),
            wet = 0.06, peak = 0.22,
        ),
        Kind.OPEN to Patch(
            listOf(Note(783.99, 0.0, 0.22, Voice.BELL, 0.8), Note(1174.66, 0.055, 0.28, Voice.BELL, 1.0)),
            wet = 0.12, peak = 0.22,
        ),
        Kind.CLOSE to Patch(
            listOf(Note(1174.66, 0.0, 0.2, Voice.BELL, 0.9), Note(783.99, 0.055, 0.26, Voice.BELL, 0.8)),
            wet = 0.1, peak = 0.2,
        ),
        Kind.SUCCESS to Patch(
            listOf(Note(1046.5, 0.0, 0.34, Voice.BELL, 0.9), Note(1567.98, 0.09, 0.5, Voice.BELL, 1.0)),
            wet = 0.2, peak = 0.32,
        ),
        // A soft "uh-uh": two low, round thuds. Firm, never harsh.
        Kind.ERROR to Patch(
            listOf(
                Note(240.0, 0.0, 0.14, Voice.SINE, 1.0, 170.0),
                Note(2200.0, 0.0, 0.01, Voice.CLICK, 0.25),
                Note(200.0, 0.12, 0.18, Voice.SINE, 0.9, 140.0),
            ),
            wet = 0.05, peak = 0.3,
        ),
        Kind.AUTH to Patch(
            listOf(
                Note(659.25, 0.0, 0.4, Voice.BELL, 0.8),
                Note(987.77, 0.1, 0.45, Voice.BELL, 0.9),
                Note(1318.51, 0.2, 0.7, Voice.BELL, 1.0),
            ),
            wet = 0.22, peak = 0.32,
        ),
        Kind.XP to Patch(
            listOf(
                Note(1318.51, 0.0, 0.14, Voice.PLUCK, 0.8),
                Note(1760.0, 0.06, 0.16, Voice.PLUCK, 0.9),
                Note(2637.02, 0.12, 0.34, Voice.BELL, 0.7),
            ),
            wet = 0.15, peak = 0.28,
        ),
        Kind.ACHIEVEMENT to Patch(
            listOf(
                Note(1046.5, 0.0, 0.4, Voice.BELL, 0.8),
                Note(1318.51, 0.08, 0.42, Voice.BELL, 0.85),
                Note(1567.98, 0.16, 0.46, Voice.BELL, 0.9),
                Note(2093.0, 0.26, 0.8, Voice.BELL, 1.0),
                Note(3135.96, 0.3, 0.5, Voice.SINE, 0.15),
            ),
            wet = 0.28, peak = 0.4,
        ),
        Kind.NOTIFY to Patch(
            listOf(Note(1318.51, 0.0, 0.3, Voice.BELL, 0.9), Note(1760.0, 0.08, 0.42, Voice.BELL, 1.0)),
            wet = 0.15, peak = 0.26,
        ),
        Kind.PURCHASE to Patch(
            listOf(
                Note(880.0, 0.0, 0.5, Voice.BELL, 0.8),
                Note(1318.51, 0.07, 0.6, Voice.BELL, 1.0),
                Note(1760.0, 0.07, 0.6, Voice.BELL, 0.5),
                Note(2637.02, 0.14, 0.45, Voice.SINE, 0.12),
            ),
            wet = 0.25, peak = 0.34,
        ),
        // web playProSfx: two tones converging to a "signal lock".
        Kind.CELEBRATE_PRO to Patch(
            listOf(
                Note(587.33, 0.0, 0.3, Voice.BELL, 0.8),
                Note(880.0, 0.14, 0.7, Voice.BELL, 1.0),
                Note(1760.0, 0.16, 0.4, Voice.SINE, 0.25),
            ),
            wet = 0.3, peak = 0.5,
        ),
        // playEliteSfx: rising drone, A-major arpeggio, landing chord, sparkle tail.
        Kind.CELEBRATE_ELITE to Patch(
            buildList {
                add(Note(110.0, 0.0, 1.5, Voice.PAD, 0.9, 220.0))
                listOf(440.0, 554.37, 659.25, 880.0).forEachIndexed { i, f ->
                    add(Note(f, 0.25 + i * 0.16, 0.5, Voice.BELL, 0.8))
                }
                listOf(880.0, 1108.73, 1318.51).forEach { add(Note(it, 1.05, 1.1, Voice.BELL, 0.7)) }
                add(Note(2637.02, 1.1, 0.7, Voice.SINE, 0.15))
            },
            wet = 0.35, peak = 0.55,
        ),
        // App-open signature: a felt mallet thump, three phones landing on the
        // stack (rising A-major plucks), a warm bell bloom, an airy shimmer.
        Kind.STARTUP to Patch(
            buildList {
                add(Note(120.0, 0.0, 0.3, Voice.SINE, 1.0, 62.0))
                add(Note(500.0, 0.0, 0.02, Voice.CLICK, 0.5))
                listOf(659.25, 880.0, 1108.73).forEachIndexed { i, f ->
                    add(Note(f, 0.1 + i * 0.12, 0.3, Voice.PLUCK, 0.75))
                }
                listOf(440.0, 659.25, 880.0).forEach { add(Note(it, 0.46, 1.1, Voice.BELL, 0.55)) }
                add(Note(1760.0, 0.5, 0.7, Voice.SINE, 0.12))
                add(Note(2637.02, 0.58, 0.6, Voice.SINE, 0.07))
            },
            wet = 0.3, peak = 0.42,
        ),
    )

    private const val RATE = 44_100
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val cache = ConcurrentHashMap<Kind, ShortArray>()

    /** Pre-render every clip off the main thread so the first tap isn't late. */
    fun warm() {
        worker.execute { (listOf(Kind.STARTUP, Kind.TAP) + Kind.entries).forEach { k -> cache.getOrPut(k) { render(PATCHES.getValue(k)) } } }
    }

    /** Play a UI sound. Silent if sound is off or audio fails. */
    fun play(kind: Kind) {
        if (!enabled) return
        worker.execute {
            runCatching {
                val pcm = cache.getOrPut(kind) { render(PATCHES.getValue(kind)) }
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
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Release later instead of sleeping here, so back-to-back taps
                // never queue behind a ringing clip.
                worker.schedule({ runCatching { track.release() } }, pcm.size * 1000L / RATE + 80, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Rendered PCM (mono, 16-bit, [SAMPLE_RATE]) — for the render test and WAV export. */
    internal fun pcm(kind: Kind): ShortArray = render(PATCHES.getValue(kind))
    internal const val SAMPLE_RATE = RATE

    private fun render(patch: Patch): ShortArray {
        val tail = if (patch.wet > 0) 0.7 else 0.02
        val total = patch.notes.maxOf { it.t + it.d } + tail
        val out = DoubleArray((total * RATE).toInt())
        patch.notes.forEach { voice(it, out) }
        val mixed = if (patch.wet > 0) reverb(out, patch.wet) else out
        // Peak-normalise to the designed level, then a 6ms fade to kill clicks.
        val peak = mixed.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        val k = patch.peak / peak
        val fade = (0.006 * RATE).toInt()
        return ShortArray(mixed.size) { i ->
            val edge = if (i > mixed.size - fade) (mixed.size - i).toDouble() / fade else 1.0
            (mixed[i] * k * edge * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private val rng = java.util.Random(7) // deterministic: same sound every launch

    private fun voice(n: Note, out: DoubleArray) {
        val start = (n.t * RATE).toInt()
        val len = (n.d * RATE).toInt()
        fun env(s: Double, attack: Double): Double =
            if (s < attack) s / attack else exp(-5.5 * (s - attack) / (n.d - attack))
        fun freqAt(s: Double) = n.to?.let { n.f * exp(ln(it / n.f) * (s / n.d)) } ?: n.f
        when (n.v) {
            Voice.SINE, Voice.TRIANGLE -> {
                var ph = 0.0
                for (i in 0 until len) {
                    val s = i.toDouble() / RATE
                    ph += freqAt(s) / RATE
                    val p = ph - ph.toLong()
                    val w = if (n.v == Voice.SINE) sin(2 * PI * p) else 1 - 4 * abs(p - 0.5)
                    add(out, start + i, w * env(s, 0.006) * n.g)
                }
            }
            Voice.PAD -> {
                var a = 0.0; var b = 0.0
                for (i in 0 until len) {
                    val s = i.toDouble() / RATE
                    val f = freqAt(s)
                    a += f * 0.996 / RATE; b += f * 1.004 / RATE
                    val w = 0.5 * (sin(2 * PI * a) + sin(2 * PI * b))
                    val e = minOf(1.0, s / 0.18) * exp(-1.6 * s / n.d)
                    add(out, start + i, w * e * n.g)
                }
            }
            Voice.BELL -> {
                // FM: modulator at 2x, index decays fast (bright strike -> pure tone),
                // plus a quieter inharmonic partial (x2.76) that dies quicker: glass.
                var pc = 0.0; var pm = 0.0; var pp = 0.0
                for (i in 0 until len) {
                    val s = i.toDouble() / RATE
                    val f = freqAt(s)
                    pc += f / RATE; pm += f * 2.0 / RATE; pp += f * 2.76 / RATE
                    val index = 2.4 * exp(-s / (n.d * 0.18))
                    val body = sin(2 * PI * pc + index * sin(2 * PI * pm))
                    val glass = 0.18 * sin(2 * PI * pp) * exp(-s / (n.d * 0.12))
                    add(out, start + i, (body + glass) * env(s, 0.003) * n.g)
                }
            }
            Voice.PLUCK -> {
                val period = max(2, (RATE / n.f).toInt())
                val buf = DoubleArray(period) { rng.nextDouble() * 2 - 1 }
                // Ring time ≈ d: per-sample loss chosen so energy falls ~40dB over d.
                val loss = exp(ln(0.01) / (n.d * n.f))
                var idx = 0
                for (i in 0 until len) {
                    val nxt = (idx + 1) % period
                    val y = buf[idx]
                    buf[idx] = (y + buf[nxt]) * 0.5 * loss
                    idx = nxt
                    val s = i.toDouble() / RATE
                    add(out, start + i, y * minOf(1.0, s / 0.002) * n.g)
                }
            }
            Voice.CLICK -> {
                // One-pole low-passed noise burst; f is the cutoff.
                val a = 1 - exp(-2 * PI * n.f / RATE)
                var lp = 0.0
                for (i in 0 until len) {
                    lp += a * ((rng.nextDouble() * 2 - 1) - lp)
                    val s = i.toDouble() / RATE
                    add(out, start + i, lp * exp(-s / (n.d * 0.3)) * n.g * 2.0)
                }
            }
        }
    }

    private fun add(out: DoubleArray, i: Int, v: Double) {
        if (i in out.indices) out[i] += v
    }

    /** Small Schroeder/Freeverb-style room: 4 damped combs into 2 allpasses. */
    private fun reverb(dry: DoubleArray, wet: Double): DoubleArray {
        val combs = intArrayOf(1116, 1188, 1277, 1356)
        val wetBuf = DoubleArray(dry.size)
        for (d in combs) {
            val line = DoubleArray(d)
            var idx = 0
            var store = 0.0
            for (i in dry.indices) {
                val y = line[idx]
                store = y * 0.7 + store * 0.3 // damping
                line[idx] = dry[i] + store * 0.8
                wetBuf[i] += y
                idx = (idx + 1) % d
            }
        }
        for (d in intArrayOf(556, 441)) {
            val line = DoubleArray(d)
            var idx = 0
            for (i in wetBuf.indices) {
                val b = line[idx]
                val x = wetBuf[i]
                line[idx] = x + b * 0.5
                wetBuf[i] = b - x
                idx = (idx + 1) % d
            }
        }
        return DoubleArray(dry.size) { dry[it] + wetBuf[it] * wet * 0.25 }
    }
}
