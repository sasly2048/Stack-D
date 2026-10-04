package app.stackd.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards RFC-4180 quoting on the CSV export. A wrong escape here silently
 * corrupts a user's exported focus history (shifted columns, broken rows), so
 * the quoting rules are worth pinning: quote only when needed, double inner
 * quotes.
 */
class EscapeCsvTest {

    @Test
    fun `plain values pass through unquoted`() {
        assertEquals("flow", escapeCsv("flow"))
        assertEquals("42", escapeCsv("42"))
        assertEquals("", escapeCsv(""))
    }

    @Test
    fun `commas force quoting`() {
        assertEquals("\"a,b\"", escapeCsv("a,b"))
    }

    @Test
    fun `inner quotes are doubled and the field quoted`() {
        assertEquals("\"she said \"\"hi\"\"\"", escapeCsv("she said \"hi\""))
    }

    @Test
    fun `newlines and carriage returns force quoting`() {
        assertEquals("\"line1\nline2\"", escapeCsv("line1\nline2"))
        assertEquals("\"a\rb\"", escapeCsv("a\rb"))
    }

    @Test
    fun `a lone quote still triggers quoting`() {
        assertEquals("\"\"\"\"", escapeCsv("\""))
    }
}
