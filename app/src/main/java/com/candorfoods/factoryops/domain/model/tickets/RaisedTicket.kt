package com.candorfoods.factoryops.domain.model.tickets

import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A maintenance complaint raised over WhatsApp (asset, area, optional photo,
 * problem) that an it_technician works from the app.
 *
 * The app never creates one — WhatsApp is the only intake. It can only be taken
 * (acknowledged) and closed (resolved with a corrective action).
 */
enum class TicketStatus(val backendValue: String, val label: String) {
    OPEN("open", "Open"),
    ACKNOWLEDGED("acknowledged", "In progress"),
    RESOLVED("resolved", "Resolved");

    companion object {
        /**
         * Never throws on an unrecognised value: the backend owns this vocabulary
         * and adding a status there must not crash an older app build.
         */
        fun of(raw: String?): TicketStatus =
            entries.firstOrNull { it.backendValue.equals(raw?.trim(), ignoreCase = true) } ?: OPEN
    }
}

/** One technician's delivery + acknowledgment record for a ticket (wa_receives). */
data class TicketRecipient(
    val id: Int,
    val name: String?,
    val ackStatus: String,
    val doneBy: String?,
    val doneAt: String?,
    /** What this person actually did to fix it. Null until they close the ticket. */
    val correctiveAction: String?,
    val receivedAt: String?,
)

data class RaisedTicket(
    val id: Int,
    val ticketNo: String,
    val asset: String?,
    val area: String?,
    val description: String?,
    /** Public S3 URL, or null when the reporter skipped the photo. */
    val photoUrl: String?,
    val status: TicketStatus,
    /** Technician who took it, by name. Null while unclaimed. */
    val assignedTo: String?,
    val raisedBy: String?,
    val createdAt: String?,
    val closedAt: String?,
    val recipients: List<TicketRecipient> = emptyList(),
) {
    val isOpen: Boolean get() = status == TicketStatus.OPEN
    val isResolved: Boolean get() = status == TicketStatus.RESOLVED

    /** "Packing Machine 3 — 1st Floor", collapsing gracefully when either is blank. */
    val assetWithArea: String
        get() = listOfNotNull(asset?.takeIf { it.isNotBlank() }, area?.takeIf { it.isNotBlank() })
            .joinToString(" — ")
            .ifBlank { "Unspecified asset" }

    /** The closing note, from whoever actually resolved it. */
    val correctiveAction: String?
        get() = recipients.firstOrNull { !it.correctiveAction.isNullOrBlank() }?.correctiveAction

    val raisedAtDisplay: String get() = formatStamp(createdAt)
    val closedAtDisplay: String get() = formatStamp(closedAt)

    /** "just now" / "2h ago" / "3d ago" — how long this has been waiting. */
    fun age(now: OffsetDateTime = OffsetDateTime.now()): String {
        val started = parse(createdAt) ?: return ""
        val d = Duration.between(started, now)
        return when {
            d.isNegative -> "just now"          // clock skew between server and device
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()}m ago"
            d.toDays() < 1 -> "${d.toHours()}h ago"
            else -> "${d.toDays()}d ago"
        }
    }

    private companion object {
        /**
         * The backend already converts to IST, so the offset is parsed and used
         * as-is. Re-converting to the device zone would show a phone set to UTC
         * a time 5.5 hours off from the one on the WhatsApp confirmation.
         *
         * Locale is pinned rather than left to the device: the default renders
         * the AM/PM marker inconsistently ("pm" on some builds, "PM" on others)
         * and a phone set to a non-English locale would show month names the
         * ticket number and WhatsApp message don't match.
         */
        val DISPLAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

        fun parse(raw: String?): OffsetDateTime? = raw
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

        fun formatStamp(raw: String?): String = parse(raw)?.format(DISPLAY) ?: "—"
    }
}
