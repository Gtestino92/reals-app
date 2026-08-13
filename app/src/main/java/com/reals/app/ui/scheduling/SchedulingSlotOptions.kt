package com.reals.app.ui.scheduling

import com.reals.app.domain.model.ProposalStatus
import com.reals.app.domain.model.SchedulingAvailability
import com.reals.app.domain.model.SchedulingProposal
import com.reals.app.domain.model.SchedulingUnavailableWindow
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

internal data class SchedulingMinuteOption(
    val minute: Int,
    val timeValid: Boolean,
    val conflicting: Boolean,
) {
    val selectable: Boolean get() = timeValid && !conflicting
}

internal enum class SchedulingPickerOptionEmphasis {
    Enabled,
    Disabled,
    Blocked,
}

internal fun schedulingPickerOptionEmphasis(
    optionEnabled: Boolean,
    optionBlocked: Boolean,
): SchedulingPickerOptionEmphasis =
    when {
        optionBlocked -> SchedulingPickerOptionEmphasis.Blocked
        optionEnabled -> SchedulingPickerOptionEmphasis.Enabled
        else -> SchedulingPickerOptionEmphasis.Disabled
    }

internal enum class SchedulingProposalTimeAvailability {
    Future,
    Expired,
    Conflicting,
    Invalid,
}

internal data class SchedulingReceivedProposalReviewItem(
    val proposal: SchedulingProposal,
    val timeAvailability: SchedulingProposalTimeAvailability,
) {
    val acceptanceAvailable: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Future
    val expired: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Expired
    val conflicting: Boolean get() = timeAvailability == SchedulingProposalTimeAvailability.Conflicting
    val unavailable: Boolean get() =
        timeAvailability == SchedulingProposalTimeAvailability.Invalid ||
            timeAvailability == SchedulingProposalTimeAvailability.Conflicting
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

internal const val CONFLICTING_SLOT_MESSAGE =
    "Ese horario se superpone con otra cita confirmada."
internal const val CONFLICTING_SELECTED_SLOT_MESSAGE =
    "Uno o más horarios elegidos se superponen con otra cita confirmada. Quitalos o elegí otro horario."

internal fun schedulingSlotConflictPolicy(
    candidate: String,
    availability: SchedulingAvailability?,
): Boolean {
    val candidateInstant = runCatching { OffsetDateTime.parse(candidate).toInstant() }
        .getOrNull()
        ?: return false
    return schedulingSlotConflictPolicy(candidateInstant, availability)
}

internal fun schedulingSlotConflictPolicy(
    candidate: OffsetDateTime,
    availability: SchedulingAvailability?,
): Boolean = schedulingSlotConflictPolicy(candidate.toInstant(), availability)

internal fun schedulingAvailabilityHasValidUnavailableWindows(
    availability: SchedulingAvailability?,
): Boolean {
    if (availability == null) return false
    return availability.unavailableWindows.any { window ->
        window.validWindowInstants() != null
    }
}

private fun schedulingSlotConflictPolicy(
    candidateInstant: Instant,
    availability: SchedulingAvailability?,
): Boolean {
    if (availability == null) return false
    return availability.unavailableWindows.any { window ->
        window.contains(candidateInstant)
    }
}

private fun SchedulingUnavailableWindow.contains(candidateInstant: Instant): Boolean {
    val (start, end) = validWindowInstants() ?: return false
    return !candidateInstant.isBefore(start) && !candidateInstant.isAfter(end)
}

private fun SchedulingUnavailableWindow.validWindowInstants(): Pair<Instant, Instant>? {
    val start = startsAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return null
    val end = endsAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: return null
    if (start.isAfter(end)) return null
    return start to end
}

internal fun schedulingProposalTimeAvailability(
    proposedDateTime: String,
    nowMillis: Long,
    availability: SchedulingAvailability? = null,
): SchedulingProposalTimeAvailability {
    val proposedInstant = runCatching { OffsetDateTime.parse(proposedDateTime).toInstant() }
        .getOrNull()
        ?: return SchedulingProposalTimeAvailability.Invalid
    val nowInstant = Instant.ofEpochMilli(nowMillis)
    return when {
        !proposedInstant.isAfter(nowInstant) -> SchedulingProposalTimeAvailability.Expired
        schedulingSlotConflictPolicy(proposedInstant, availability) -> SchedulingProposalTimeAvailability.Conflicting
        else -> SchedulingProposalTimeAvailability.Future
    }
}

internal fun schedulingReceivedProposalReviewState(
    partnerProposals: List<SchedulingProposal>,
    nowMillis: Long,
    availability: SchedulingAvailability? = null,
): SchedulingReceivedProposalReviewState {
    val items = partnerProposals
        .filter { it.status == ProposalStatus.Pending }
        .map { proposal ->
            SchedulingReceivedProposalReviewItem(
                proposal = proposal,
                timeAvailability = schedulingProposalTimeAvailability(
                    proposedDateTime = proposal.proposedDateTime,
                    nowMillis = nowMillis,
                    availability = availability,
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
    availability: SchedulingAvailability? = null,
): List<Int> = (0..23).filter { hour ->
    availableSchedulingMinutes(date, hour, now, zoneId, availability).isNotEmpty()
}

internal fun availableSchedulingMinutes(
    date: LocalDate,
    hour: Int,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    availability: SchedulingAvailability? = null,
): List<Int> = schedulingMinuteOptions(date, hour, now, zoneId, availability)
    .filter { it.selectable }
    .map { it.minute }

internal fun visibleSchedulingHours(
    date: LocalDate,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<Int> = (0..23).filter { hour ->
    schedulingMinuteOptions(date, hour, now, zoneId).any { it.timeValid }
}

internal fun schedulingMinuteOptions(
    date: LocalDate,
    hour: Int,
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    availability: SchedulingAvailability? = null,
): List<SchedulingMinuteOption> = listOf(0, 30).map { minute ->
    val minimumAllowedInstant = now.plusMinutes(MIN_SCHEDULING_LEAD_TIME_MINUTES).toInstant()
    val candidate = buildSchedulingSlot(
        SchedulingSlotSelection(
            date = date,
            hour = hour,
            minute = minute,
        ),
        zoneId,
    )
    val candidateInstant = candidate.toInstant()
    val timeValid = candidateInstant.isAfter(now.toInstant()) && !candidateInstant.isBefore(minimumAllowedInstant)
    SchedulingMinuteOption(
        minute = minute,
        timeValid = timeValid,
        conflicting = timeValid && schedulingSlotConflictPolicy(candidate, availability),
    )
}

internal fun firstAvailableSchedulingSelection(
    now: OffsetDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
    availability: SchedulingAvailability? = null,
): SchedulingSlotSelection? {
    return schedulingDayOptions(now).firstNotNullOfOrNull { day ->
        visibleSchedulingHours(day.date, now, zoneId).firstNotNullOfOrNull { hour ->
            availableSchedulingMinutes(day.date, hour, now, zoneId, availability).firstOrNull()?.let { minute ->
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
    availability: SchedulingAvailability? = null,
): SchedulingSlotSelection {
    val dayOptions = schedulingDayOptions(now)
    val selectedLocalDate = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val validSelectedDate = selectedLocalDate?.takeIf { date ->
        dayOptions.any { it.date == date } &&
            availableSchedulingHours(date, now, zoneId, availability).isNotEmpty()
    }
    val dateWasPreserved = validSelectedDate != null
    val date = validSelectedDate ?: dayOptions.firstNotNullOfOrNull { day ->
        day.date.takeIf { availableSchedulingHours(it, now, zoneId, availability).isNotEmpty() }
    } ?: now.toLocalDate()
    val hours = availableSchedulingHours(date, now, zoneId, availability)
    val hour = if (dateWasPreserved) {
        selectedHour.takeIf { it in hours } ?: hours.firstOrNull() ?: selectedHour
    } else {
        hours.firstOrNull() ?: selectedHour
    }
    val minutes = availableSchedulingMinutes(date, hour, now, zoneId, availability)
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
    availability: SchedulingAvailability? = null,
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
    if (parsed.any { schedulingSlotConflictPolicy(it, availability) }) {
        return CONFLICTING_SELECTED_SLOT_MESSAGE
    }
    return null
}

internal fun validateCurrentSelectedSlots(
    values: List<String>,
    now: OffsetDateTime,
    availability: SchedulingAvailability? = null,
): String? {
    if (values.isEmpty()) return validateSelectedSlots(values, now, availability)
    val parsed = values.map { value ->
        runCatching { OffsetDateTime.parse(value) }.getOrNull()
    }
    if (parsed.all { it != null } && parsed.any { !it!!.isAfter(now) }) {
        return EXPIRED_SELECTED_SLOT_MESSAGE
    }
    if (parsed.all { it != null } && parsed.any { schedulingSlotConflictPolicy(it!!, availability) }) {
        return CONFLICTING_SELECTED_SLOT_MESSAGE
    }
    return validateSelectedSlots(values, now, availability)
}

internal fun canSubmitSelectedSlots(
    values: List<String>,
    now: OffsetDateTime,
    availability: SchedulingAvailability? = null,
): Boolean {
    return values.isNotEmpty() && validateCurrentSelectedSlots(values, now, availability) == null
}

internal fun previousEnabledOptionIndex(
    selectedIndex: Int,
    optionCount: Int,
    isOptionEnabled: (Int) -> Boolean,
): Int? = (selectedIndex - 1 downTo 0).firstOrNull(isOptionEnabled)

internal fun nextEnabledOptionIndex(
    selectedIndex: Int,
    optionCount: Int,
    isOptionEnabled: (Int) -> Boolean,
): Int? = (selectedIndex + 1 until optionCount).firstOrNull(isOptionEnabled)
