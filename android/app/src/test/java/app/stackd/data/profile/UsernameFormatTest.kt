package app.stackd.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the client-side username format rules — the portable subset of the
 * web's validation (the server blocklist is deliberately out of scope). A pass
 * returns null; every rejection carries the message the profile screen shows.
 */
class UsernameFormatTest {

    private fun reject(s: String) = validateUsernameFormat(s)?.message

    @Test
    fun `accepts a well-formed handle`() {
        assertNull(validateUsernameFormat("raghav"))
        assertNull(validateUsernameFormat("A1_b-c"))
        assertNull(validateUsernameFormat("abc")) // exactly the 3-char floor
        assertNull(validateUsernameFormat("a234567890123456789")) // exactly 20
    }

    @Test
    fun `enforces length bounds`() {
        assertEquals("Usernames are at least 3 characters.", reject("ab"))
        assertEquals("Usernames are at most 20 characters.", reject("a2345678901234567890x")) // 21
    }

    @Test
    fun `must start with a letter`() {
        val msg = "Start with a letter; use letters, numbers, _ or - only."
        assertEquals(msg, reject("1abc"))
        assertEquals(msg, reject("_abc"))
        assertEquals(msg, reject("-abc"))
    }

    @Test
    fun `rejects disallowed characters`() {
        val msg = "Start with a letter; use letters, numbers, _ or - only."
        assertEquals(msg, reject("bad name")) // space
        assertEquals(msg, reject("emoji😀x"))
        assertEquals(msg, reject("dot.dot"))
        assertEquals(msg, reject("a@b"))
    }

    @Test
    fun `length bound is checked before the pattern`() {
        // A 2-char string that also fails the pattern reports too-short first,
        // matching the web's rule order.
        assertEquals("Usernames are at least 3 characters.", reject("_"))
    }
}
