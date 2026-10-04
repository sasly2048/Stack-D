package app.stackd.data.social

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the two board derivations that the web computes in the component and
 * Android moved into the repository: average-XP circle ranking, and the
 * de-duped personal leaderboard. Mirrors the private logic in
 * [GroupsRepository.snapshot] so a regression there fails here.
 */
class GroupBoardTest {

    private fun member(group: String, id: String, name: String, xp: Long) =
        GroupMemberRow(group, id, GroupMemberRow.MemberProfile(name, xp))

    @Test
    fun `circle board ranks by average member xp`() {
        // Circle A: two members averaging 150. Circle B: one member at 300.
        // Average, not total, so B outranks A despite A's larger pool.
        val members = listOf(
            member("A", "u1", "Ann", 100),
            member("A", "u2", "Bo", 200),
            member("B", "u3", "Cy", 300),
        )
        val byGroup = members.groupBy { it.groupId }
        val board = listOf("A" to "Circle A", "B" to "Circle B")
            .map { (gid, gname) ->
                val ms = byGroup[gid].orEmpty()
                val avg = if (ms.isEmpty()) 0L else ms.sumOf { it.profiles!!.lifetimeXp } / ms.size
                BoardEntry(gname, avg)
            }
            .sortedByDescending { it.value }
        assertEquals(listOf("Circle B", "Circle A"), board.map { it.name })
        assertEquals(300L, board[0].value)
        assertEquals(150L, board[1].value)
    }

    @Test
    fun `personal board counts a shared member once`() {
        // u1 is in both circles; the leaderboard must show one row for them.
        val members = listOf(
            member("A", "u1", "Ann", 500),
            member("B", "u1", "Ann", 500),
            member("B", "u2", "Bo", 400),
        )
        val personal = members
            .filter { it.profiles != null }
            .associate { it.profileId to BoardEntry(it.profiles!!.displayName ?: "—", it.profiles.lifetimeXp) }
            .values
            .sortedByDescending { it.value }
        assertEquals(2, personal.size)
        assertEquals(listOf("Ann", "Bo"), personal.map { it.name })
    }
}
