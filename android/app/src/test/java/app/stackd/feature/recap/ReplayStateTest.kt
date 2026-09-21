package app.stackd.feature.recap

import app.stackd.data.recap.ReplayEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the replay scrubber's reveal logic: cursor 0 means "show all" (the
 * resting state), any positive cursor reveals exactly that many events, and
 * progress tracks the fraction played.
 */
class ReplayStateTest {

    private fun ev(at: String) = ReplayEvent(at = at, kind = "session", label = "x")
    private val events = listOf(ev("a"), ev("b"), ev("c"), ev("d"))

    @Test
    fun `cursor zero shows every event`() {
        val s = ReplayUiState(events = events, cursor = 0)
        assertEquals(4, s.visible.size)
        assertEquals(0f, s.progress, 0f)
    }

    @Test
    fun `positive cursor reveals that many`() {
        val s = ReplayUiState(events = events, cursor = 2)
        assertEquals(listOf("a", "b"), s.visible.map { it.at })
        assertEquals(0.5f, s.progress, 0.0001f)
    }

    @Test
    fun `fully played reveals all with full progress`() {
        val s = ReplayUiState(events = events, cursor = 4)
        assertEquals(4, s.visible.size)
        assertEquals(1f, s.progress, 0f)
    }

    @Test
    fun `empty day has zero progress and nothing visible`() {
        val s = ReplayUiState(events = emptyList(), cursor = 0)
        assertEquals(0, s.visible.size)
        assertEquals(0f, s.progress, 0f)
    }
}
