package app.stackd.feature.trust

import app.stackd.data.trust.HostReport
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the open/all filter on the moderation dashboard. */
class ModerationFilterTest {

    private fun report(id: String, status: String) = HostReport(
        id = id, kind = "spam", reason = null, status = status,
        createdAt = "2026-08-30T10:00:00Z",
        targetUserId = null, targetRoomId = "r1", roomCode = "ABC",
        reporterName = "R", targetName = null,
    )

    private val reports = listOf(
        report("1", "open"),
        report("2", "resolved"),
        report("3", "open"),
        report("4", "dismissed"),
    )

    @Test
    fun `open filter shows only open reports`() {
        val s = ModerationUiState(reports = reports, filter = "open")
        assertEquals(listOf("1", "3"), s.visible.map { it.id })
    }

    @Test
    fun `all filter shows everything`() {
        val s = ModerationUiState(reports = reports, filter = "all")
        assertEquals(listOf("1", "2", "3", "4"), s.visible.map { it.id })
    }

    @Test
    fun `report kinds fit the 40-char kind column`() {
        // fileReport truncates to 40; every offered kind must survive intact.
        REPORT_KINDS.forEach { assertEquals(it, it.take(40)) }
    }
}
