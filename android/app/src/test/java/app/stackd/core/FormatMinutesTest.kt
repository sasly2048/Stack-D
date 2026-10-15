package app.stackd.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatMinutesTest {
    @Test
    fun `whole hours drop the zero minutes`() {
        assertEquals("45m", formatMinutes(45))
        assertEquals("1h", formatMinutes(60))
        assertEquals("2h 10m", formatMinutes(130))
    }
}
