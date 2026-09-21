package app.stackd.feature.trust

import app.stackd.core.parseIsoMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATE: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

private val DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

/** Local date, matching the web's `toLocaleDateString()` on the Trust list. */
internal fun trustDate(iso: String): String =
    parseIsoMillis(iso)?.let { DATE.format(Instant.ofEpochMilli(it)) } ?: ""

/** Date + time, matching the web's `toLocaleString()` on the moderation rows. */
internal fun trustDateTime(iso: String): String =
    parseIsoMillis(iso)?.let { DATE_TIME.format(Instant.ofEpochMilli(it)) } ?: ""
