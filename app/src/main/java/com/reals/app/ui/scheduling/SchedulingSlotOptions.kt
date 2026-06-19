package com.reals.app.ui.scheduling

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
    if (values.isEmpty()) return "Selecciona al menos un horario."
    if (values.size > 3) return "Podes elegir hasta 3 horarios."
    val parsed = values.map { value ->
        runCatching { OffsetDateTime.parse(value) }.getOrNull()
            ?: return "Hay un horario con formato invalido."
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
