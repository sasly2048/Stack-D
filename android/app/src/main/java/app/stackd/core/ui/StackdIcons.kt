package app.stackd.core.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Award
import com.composables.icons.lucide.Blocks
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CalendarRange
import com.composables.icons.lucide.CalendarX
import com.composables.icons.lucide.ChartNoAxesColumn
import com.composables.icons.lucide.ChartSpline
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleUserRound
import com.composables.icons.lucide.Compass
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Dna
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Fingerprint
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gem
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.Handshake
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.House
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Medal
import com.composables.icons.lucide.Network
import com.composables.icons.lucide.Orbit
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Puzzle
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Rewind
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.SearchX
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.UserRoundPlus
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.UsersRound
import com.composables.icons.lucide.Vault
import com.composables.icons.lucide.Webhook
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap

/**
 * The app's icon set — Lucide, the same set the web renders via lucide-react,
 * so a glyph means the same thing (and has the same 2px rounded stroke) on
 * both. Property names mirror the Material names they replaced to keep the
 * swap mechanical; outlined vs filled is gone because Lucide is stroke-only —
 * selection is carried by colour and the tab capsule instead.
 */
object StackdIcons {
    val AccountTree: ImageVector get() = Lucide.Network
    val Add: ImageVector get() = Lucide.Plus
    val AutoAwesome: ImageVector get() = Lucide.Sparkles
    val Bolt: ImageVector get() = Lucide.Zap
    val CalendarMonth: ImageVector get() = Lucide.CalendarDays
    val CardGiftcard: ImageVector get() = Lucide.Gift
    val Close: ImageVector get() = Lucide.X
    val Diamond: ImageVector get() = Lucide.Gem
    val EmojiEvents: ImageVector get() = Lucide.Trophy
    val EventBusy: ImageVector get() = Lucide.CalendarX
    val Extension: ImageVector get() = Lucide.Puzzle
    val FileDownload: ImageVector get() = Lucide.Download
    val Fingerprint: ImageVector get() = Lucide.Fingerprint
    val Flag: ImageVector get() = Lucide.Flag
    val GridView: ImageVector get() = Lucide.LayoutGrid
    val Group: ImageVector get() = Lucide.Users
    val Groups: ImageVector get() = Lucide.Users
    val Handshake: ImageVector get() = Lucide.Handshake
    val History: ImageVector get() = Lucide.History
    val Home: ImageVector get() = Lucide.House
    val HourglassEmpty: ImageVector get() = Lucide.Hourglass
    val HourglassTop: ImageVector get() = Lucide.Hourglass
    val Insights: ImageVector get() = Lucide.ChartSpline
    val Inventory2: ImageVector get() = Lucide.Archive
    val Leaderboard: ImageVector get() = Lucide.ChartNoAxesColumn
    val LocalFireDepartment: ImageVector get() = Lucide.Flame
    val MilitaryTech: ImageVector get() = Lucide.Medal
    val MoreHoriz: ImageVector get() = Lucide.Ellipsis
    val People: ImageVector get() = Lucide.UsersRound
    val Person: ImageVector get() = Lucide.CircleUserRound
    val PersonAdd: ImageVector get() = Lucide.UserPlus
    val PersonAddAlt: ImageVector get() = Lucide.UserPlus
    val PlayArrow: ImageVector get() = Lucide.Play
    val Refresh: ImageVector get() = Lucide.RefreshCw
    val Replay: ImageVector get() = Lucide.RotateCcw
    val SearchOff: ImageVector get() = Lucide.SearchX
    val Sensors: ImageVector get() = Lucide.Radio
    val Shield: ImageVector get() = Lucide.Shield
    val VerifiedUser: ImageVector get() = Lucide.ShieldCheck
    val Webhook: ImageVector get() = Lucide.Webhook
    val WorkspacePremium: ImageVector get() = Lucide.Crown
    val Activity: ImageVector get() = Lucide.Activity
    val Award: ImageVector get() = Lucide.Award
    val ChevronRight: ImageVector get() = Lucide.ChevronRight

    // Menu grid — one cohesive, rounded Lucide family (newer "-Round" people
    // glyphs, single-concept objects), so the sheet doesn't mix eras.
    val Atlas: ImageVector get() = Lucide.Compass
    val Target: ImageVector get() = Lucide.Target
    val CalendarRange: ImageVector get() = Lucide.CalendarRange
    val Rewind: ImageVector get() = Lucide.Rewind
    val Dna: ImageVector get() = Lucide.Dna
    val UserRoundPlus: ImageVector get() = Lucide.UserRoundPlus
    val Orbit: ImageVector get() = Lucide.Orbit
    val UsersRound: ImageVector get() = Lucide.UsersRound
    val Vault: ImageVector get() = Lucide.Vault
    val Blocks: ImageVector get() = Lucide.Blocks
}
