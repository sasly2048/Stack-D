package app.stackd.core.feedback

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Every designed sound renders to sane PCM: audible, not clipped, not silent,
 * and short enough to stay out of the way. Also exports WAVs to
 * build/sfx/ so the palette can be auditioned off-device.
 */
class SfxRenderTest {
    @Test
    fun everyKindRendersCleanly() {
        val dir = File("build/sfx").apply { mkdirs() }
        for (kind in Sfx.Kind.entries) {
            val pcm = Sfx.pcm(kind)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            val seconds = pcm.size.toDouble() / Sfx.SAMPLE_RATE
            assertTrue("$kind silent", peak > 2000)
            assertTrue("$kind clipped ($peak)", peak < 32000)
            val limit = when (kind) {
                Sfx.Kind.TAP, Sfx.Kind.SELECT -> 1.0
                Sfx.Kind.CELEBRATE_ELITE -> 3.5
                Sfx.Kind.STARTUP -> 2.5
                else -> 2.2
            }
            assertTrue("$kind too long (${"%.2f".format(seconds)}s)", seconds < limit)
            writeWav(File(dir, "${kind.name.lowercase()}.wav"), pcm)
        }
    }

    @Test
    fun tapIsQuieterThanCelebration() {
        fun peak(k: Sfx.Kind) = Sfx.pcm(k).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak(Sfx.Kind.TAP) < peak(Sfx.Kind.SUCCESS))
        assertTrue(peak(Sfx.Kind.SUCCESS) < peak(Sfx.Kind.CELEBRATE_ELITE))
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val rate = Sfx.SAMPLE_RATE
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        pcm.forEach { b.putShort(it) }
        f.writeBytes(b.array())
    }
}
