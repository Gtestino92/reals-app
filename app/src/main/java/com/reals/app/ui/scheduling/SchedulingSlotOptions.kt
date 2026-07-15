package com.reals.app.ui.scheduling

import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingProposal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_SCHEDULING_LEAD_TIME_MINUTES = 20L

internal data class SchedulingDayOption(
    val date: LocalDate,
    val label: String,
)

internal data class SchedulingSlotSelection(
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
)

internal enum class SchedulingProposalTimeAvailability {
    Future,
    Expired,
    Invalid,
}

internal data class SchedulingReceivedProposalReviewItem(
    val proposal: SchedulingProposal,
    val timeAvailability: SchedulingProposalTimeAvailability,
) {
    val acceptanceAvailable: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Future
    val expired: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Expired
    val unavailable: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Invalid
}

internal data class SchedulingReceivedProposalReviewState(
    val items: List<SchedulingReceivedProposalReviewItem>,
) {
    val hasAcceptableProposal: Boolean get() = items.any { it.acceptanceAvailable }
    val allExpired: Boolean get() = items.isNotEmpty() && items.all { it.expired }
    val noneAcceptable: Boolean get() = items.isNotEmpty() && !hasAcceptableProposal
    val resolutionByRejectionAvailable: Boolean get() = items.isNotEmpty()
}

internal data class NumberedSchedulingProposal<T>(
    val number: Int,
    val item: T,
)

internal const val EXPIRED_SELECTED_SLOT_MESSAGE =
    "Uno o más horarios elegidos ya pasaron. Quitalos o elegi otro horario."

internal fun schedulingProposalTimeAvailability(
    proposedDateTime: String,
    nowMillis: Long,
): SchedulingProposalTimeAvailability {
    val proposedInstant = runCatching { OffsetDateTime.parse(proposedDateTime).toInstant() }
        .getOrNull()
        ?: return SchedulingProposalTimeAvailability.Invalid
    val nowInstant = Instant.ofEpochMilli(nowMillis)
    return if (proposedInstant.isAfter(nowInstant)) {
        SchedulingProposalTimeAvailability.Future
    } else {
        SchedulingProposalTimeAvailability.Expired
    }
}

internal fun schedulingReceivedProposalReviewState(
    partnerProposals: List<SchedulingProposal>,
    nowMillis: Long,
): SchedulingReceivedProposalReviewState {
    val items = partnerProposals
        .filter { it.status == ProposalStatus.Pending }
        .map { proposal ->
            SchedulingReceivedProposalReviewItem(
                proposal = proposal,
                timeAvailability = schedulingProposalTimeAvailability(
                    proposedDateTime = proposal.proposedDateTime,
                    nowMillis = nowMillis,
                ),
            )
        }
    return SchedulingReceivedProposalReviewState(items)
}

internal fun schedulingPendingProposalPresentationItems(
    proposals: List<SchedulingProposal>,
): List<NumberedSchedulingProposal<SchedulingProposal>> =
    proposals
        .filter { it.status == ProposalStatus.Pending }
        .mapIndexed { index, proposal ->
            NumberedSchedulingProposal(
                number = index + 1,
                item = proposal,
            )
        }

internal fun schedulingReceivedProposalPresentationItems(
    reviewState: SchedulingReceivedProposalReviewState,
): List<NumberedSchedulingProposal<SchedulingReceivedProposalReviewItem>> =
    reviewState.items.mapIndexed { index, item ->
        NumberedSchedulingProposal(
            number = index + 1,
            item = item,
        )
    }

internal fun centeredWheelFirstVisibleIndex(
    selectedIndex: Int,
    optionCount: Int,
    visibleItemCount: Int = 3,
): Int {
    if (selectedIndex < 0 || optionCount <= 0) return 0
    val safeVisibleItemCount = visibleItemCount.coerceAtLeast(1)
    val maxFirstVisibleIndex = (optionCount - safeVisibleItemCount).coerceAtLeast(0)
    return (selectedIndex - safeVisibleItemCount / 2)
        .coerceIn(0, maxFirstVisibleIndex)
}

internal fun schedulingDayOptions(
    now: OffsetDateTime,
    locale: Locale = Locale.getDefault(),
): List<SchedulingDayOption> {
    val today = now.toLocalDate()
    val dayFormatter = DateTimeFormatter.ofPattern("EEE d", locale)
    return (0L..6L).map { offset ->
        val date = today.plusDays(offset)
        val label = when (offset) {
            0L -> "Hoy"
            1L -> "Mañana"
            else -> date.format(dayFormatter)
                .replace(".", "")
                .replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                }
        }
        SchedulingDayOption(date = date, label = label)
    }
}

internal fun availableSchedulingHours(
    date: LocalDate,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Int> = (0..23).filter { hour ->
    availableSchedulingMinutes(date, hour, now, zoneId).isNotEmpty()
}

internal fun availableSchedulingMinutes(
    date: LocalDate,
    hour: Int,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Int> = listOf(0, 30).filter { minute ->
    val minimumAllowedInstant = now.plusMinutes(MIN_SCHEDULING_LEAD_TIME_MINUTES).toInstant()
    buildSchedulingSlot(
        SchedulingSlotSelection(
            date = date,
            hour = hour,
            minute = minute,
        ),
        zoneId,
    )
        .toInstant()
        .let { candidateInstant ->
            candidateInstant.isAfter(now.toInstant()) && !candidateInstant.isBefore(minimumAllowedInstant)
        }
}

internal fun firstAvailableSchedulingSelection(
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): SchedulingSlotSelection? {
    return schedulingDayOptions(now).firstNotNullOfOrNull { day ->
        availableSchedulingHours(day.date, now, zoneId).firstNotNullOfOrNull { hour ->
            availableSchedulingMinutes(day.date, hour, now, zoneId).firstOrNull()?.let { minute ->
                SchedulingSlotSelection(
                    date = day.date,
                    hour = hour,
                    minute = minute,
                )
            }
        }
    }
}

internal fun correctedSchedulingPickerSelection(
    selectedDate: String,
    selectedHour: Int,
    selectedMinute: Int,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): SchedulingSlotSelection {
    val dayOptions = schedulingDayOptions(now)
    val selectedLocalDate = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val validSelectedDate = selectedLocalDate?.takeIf { date ->
        dayOptions.any { it.date == date } &&
            availableSchedulingHours(date, now, zoneId).isNotEmpty()
    }
    val dateWasPreserved = validSelectedDate != null
    val date = validSelectedDate ?: dayOptions.firstNotNullOfOrNull { day ->
        day.date.takeIf { availableSchedulingHours(it, now, zoneId).isNotEmpty() }
    } ?: now.toLocalDate()
    val hours = availableSchedulingHours(date, now, zoneId)
    val hour = if (dateWasPreserved) {
        selectedHour.takeIf { it in hours } ?: hours.firstOrNull() ?: selectedHour
    } else {
        hours.firstOrNull() ?: selectedHour
    }
    val minutes = availableSchedulingMinutes(date, hour, now, zoneId)
    val minute = if (dateWasPreserved) {
        selectedMinute.takeIf { it in minutes } ?: minutes.firstOrNull() ?: selectedMinute
    } else {
        minutes.firstOrNull() ?: selectedMinute
    }
    return SchedulingSlotSelection(
        date = date,
        hour = hour,
        minute = minute,
    )
}

internal fun buildSchedulingSlot(
    selection: SchedulingSlotSelection,
    zoneId: ZoneId = ZoneId.systemDefault(),
): OffsetDateTime {
    return ZonedDateTime.of(
        selection.date,
        LocalTime.of(selection.hour, selection.minute),
        zoneId,
    )
        .withSecond(0)
        .withNano(0)
        .toOffsetDateTime()
}

internal fun validateSelectedSlots(
    values: List<String>,
    now: OffsetDateTime = OffsetDateTime.now(),
): String? {
    if (values.isEmpty()) return "Seleccioná al menos un horario."
    if (values.size > 3) return "Podés elegir hasta 3 horarios."
    val parsed = values.map { value ->
        runCatching { OffsetDateTime.parse(value) }.getOrNull()
            ?: return "Hay un horario con formato inválido."
    }
    if (parsed.distinctBy { it.toInstant() }.size != parsed.size) {
        return "Los horarios no pueden repetirse."
    }
    if (parsed.any { !it.isAfter(now) }) {
        return "Todos los horarios tienen que ser futuros."
    }
    val minimumAllowedInstant = now.plusMinutes(MIN_SCHEDULING_LEAD_TIME_MINUTES).toInstant()
    if (parsed.any { it.toInstant().isBefore(minimumAllowedInstant) }) {
        return "Los horarios tienen que tener al menos 20 minutos de margen."
    }
    if (parsed.any { it.minute !in listOf(0, 30) || it.second != 0 || it.nano != 0 }) {
        return "Los horarios tienen que estar alineados a media hora."
    }
    return null
}

internal fun validateCurrentSelectedSlots(
    values: List<String>,
    now: OffsetDateTime,
): String? {
    if (values.isEmpty()) return validateSelectedSlots(values, now)
    val parsed = values.map { value ->
        runCatching { OffsetDateTime.parse(value) }.getOrNull()
    }
    if (parsed.all { it != null } && parsed.any { !it!!.isAfter(now) }) {
        return EXPIRED_SELECTED_SLOT_MESSAGE
    }
    return validateSelectedSlots(values, now)
}

internal fun canSubmitSelectedSlots(
    values: List<String>,
    now: OffsetDateTime,
): Boolean {
    return values.isNotEmpty() && validateCurrentSelectedSlots(values, now) == null
}
