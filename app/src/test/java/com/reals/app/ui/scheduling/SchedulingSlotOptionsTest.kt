package com.reals.app.ui.scheduling

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulingSlotOptionsTest {
    private val zoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    private val now = OffsetDateTime.parse("2026-06-18T10:31:00-03:00")

    @Test
    fun `day options include today and next six days`() {
        val options = schedulingDayOptions(now, Locale.forLanguageTag("es-AR"))

        assertEquals(7, options.size)
        assertEquals(LocalDate.parse("2026-06-18"), options.first().date)
        assertEquals(LocalDate.parse("2026-06-24"), options.last().date)
        assertEquals("Hoy", options[0].label)
        assertEquals("Mañana", options[1].label)
    }

    @Test
    fun `available hours hide elapsed hours for today`() {
        val hours = availableSchedulingHours(now.toLocalDate(), now, zoneId)

        assertEquals((11..23).toList(), hours)
    }

    @Test
    fun `available hours allow full range for future days`() {
        val hours = availableSchedulingHours(now.toLocalDate().plusDays(1), now, zoneId)

        assertEquals((0..23).toList(), hours)
    }

    @Test
    fun `available hours include current hour when next half hour is future`() {
        val afternoonNow = OffsetDateTime.parse("2026-06-18T15:07:00-03:00")

        assertEquals((15..23).toList(), availableSchedulingHours(afternoonNow.toLocalDate(), afternoonNow, zoneId))
        assertEquals(listOf(30), availableSchedulingMinutes(afternoonNow.toLocalDate(), 15, afternoonNow, zoneId))
    }

    @Test
    fun `available hours require at least twenty minutes lead time`() {
        val afternoonNow = OffsetDateTime.parse("2026-06-18T15:25:00-03:00")

        assertEquals((16..23).toList(), availableSchedulingHours(afternoonNow.toLocalDate(), afternoonNow, zoneId))
        assertEquals(emptyList<Int>(), availableSchedulingMinutes(afternoonNow.toLocalDate(), 15, afternoonNow, zoneId))
    }

    @Test
    fun `available hours remove elapsed hours when now advances`() {
        val morningNow = OffsetDateTime.parse("2026-06-18T10:31:00-03:00")
        val afternoonNow = OffsetDateTime.parse("2026-06-18T15:31:00-03:00")

        assertEquals((11..23).toList(), availableSchedulingHours(morningNow.toLocalDate(), morningNow, zoneId))
        assertEquals((16..23).toList(), availableSchedulingHours(afternoonNow.toLocalDate(), afternoonNow, zoneId))
    }

    @Test
    fun `available minutes remove half hour option when lead time boundary passes`() {
        val beforeBoundary = OffsetDateTime.parse("2026-06-18T10:09:00-03:00")
        val afterBoundary = OffsetDateTime.parse("2026-06-18T10:11:00-03:00")

        assertEquals(listOf(30), availableSchedulingMinutes(beforeBoundary.toLocalDate(), 10, beforeBoundary, zoneId))
        assertEquals(emptyList<Int>(), availableSchedulingMinutes(afterBoundary.toLocalDate(), 10, afterBoundary, zoneId))
    }

    @Test
    fun `available minutes only allow future half hour slots`() {
        assertEquals(emptyList<Int>(), availableSchedulingMinutes(now.toLocalDate(), 10, now, zoneId))
        assertEquals(listOf(0, 30), availableSchedulingMinutes(now.toLocalDate(), 11, now, zoneId))
        assertEquals(listOf(0, 30), availableSchedulingMinutes(now.toLocalDate().plusDays(1), 8, now, zoneId))
    }

    @Test
    fun `first available selection skips past options`() {
        val selection = firstAvailableSchedulingSelection(now, zoneId)

        assertEquals(SchedulingSlotSelection(now.toLocalDate(), 11, 0), selection)
    }

    @Test
    fun `buildSchedulingSlot creates local OffsetDateTime with clean seconds and nanos`() {
        val slot = buildSchedulingSlot(
            SchedulingSlotSelection(
                date = LocalDate.parse("2026-06-18"),
                hour = 21,
                minute = 30,
            ),
            ZoneId.of("America/Argentina/Buenos_Aires"),
        )

        assertEquals("2026-06-18T21:30-03:00", slot.toString())
        assertEquals(0, slot.second)
        assertEquals(0, slot.nano)
    }

    @Test
    fun `validateSelectedSlots accepts one to three future aligned unique values`() {
        val values = listOf(
            "2026-06-18T11:00:00-03:00",
            "2026-06-18T11:30:00-03:00",
            "2026-06-19T08:00:00-03:00",
        )

        assertEquals(null, validateSelectedSlots(values, now))
    }

    @Test
    fun `validateSelectedSlots rejects empty too many duplicate past invalid and unaligned values`() {
        assertEquals("Seleccioná al menos un horario.", validateSelectedSlots(emptyList(), now))
        assertEquals(
            "Podés elegir hasta 3 horarios.",
            validateSelectedSlots(
                listOf(
                    "2026-06-18T11:00:00-03:00",
                    "2026-06-18T11:30:00-03:00",
                    "2026-06-18T12:00:00-03:00",
                    "2026-06-18T12:30:00-03:00",
                ),
                now,
            ),
        )
        assertEquals(
            "Los horarios no pueden repetirse.",
            validateSelectedSlots(
                listOf("2026-06-18T11:00:00-03:00", "2026-06-18T11:00:00-03:00"),
                now,
            ),
        )
        assertEquals("Hay un horario con formato inválido.", validateSelectedSlots(listOf("not-a-date"), now))
        assertEquals(
            "Todos los horarios tienen que ser futuros.",
            validateSelectedSlots(listOf("2026-06-18T10:30:00-03:00"), now),
        )
        assertEquals(
            "Los horarios tienen que tener al menos 20 minutos de margen.",
            validateSelectedSlots(listOf("2026-06-18T10:45:00-03:00"), now),
        )
        assertEquals(
            "Los horarios tienen que estar alineados a media hora.",
            validateSelectedSlots(listOf("2026-06-18T11:15:00-03:00"), now),
        )
    }

    @Test
    fun `current validation rejects a slot after it becomes past`() {
        val selected = listOf("2026-06-18T11:00:00-03:00")
        val timeA = OffsetDateTime.parse("2026-06-18T10:31:00-03:00")
        val timeB = OffsetDateTime.parse("2026-06-18T11:01:00-03:00")

        assertEquals(null, validateCurrentSelectedSlots(selected, timeA))
        assertEquals(EXPIRED_SELECTED_SLOT_MESSAGE, validateCurrentSelectedSlots(selected, timeB))
    }

    @Test
    fun `future day slot remains valid after same day clock advance`() {
        val selected = listOf("2026-06-19T08:00:00-03:00")
        val laterSameDay = OffsetDateTime.parse("2026-06-18T23:31:00-03:00")

        assertEquals(null, validateCurrentSelectedSlots(selected, laterSameDay))
    }

    @Test
    fun `stale selected date is replaced by first valid date`() {
        val lateNow = OffsetDateTime.parse("2026-06-18T23:50:00-03:00")

        val corrected = correctedSchedulingPickerSelection(
            selectedDate = "2026-06-18",
            selectedHour = 22,
            selectedMinute = 30,
            now = lateNow,
            zoneId = zoneId,
        )

        assertEquals(LocalDate.parse("2026-06-19"), corrected.date)
        assertEquals(0, corrected.hour)
        assertEquals(30, corrected.minute)
    }

    @Test
    fun `still valid selected date is preserved`() {
        val corrected = correctedSchedulingPickerSelection(
            selectedDate = "2026-06-19",
            selectedHour = 8,
            selectedMinute = 30,
            now = now,
            zoneId = zoneId,
        )

        assertEquals(LocalDate.parse("2026-06-19"), corrected.date)
        assertEquals(8, corrected.hour)
        assertEquals(30, corrected.minute)
    }

    @Test
    fun `expired selected list is invalid but values are not removed`() {
        val selected = listOf(
            "2026-06-18T10:30:00-03:00",
            "2026-06-18T11:30:00-03:00",
        )

        assertEquals(EXPIRED_SELECTED_SLOT_MESSAGE, validateCurrentSelectedSlots(selected, now))
        assertEquals(
            listOf(
                "2026-06-18T10:30:00-03:00",
                "2026-06-18T11:30:00-03:00",
            ),
            selected,
        )
    }

    @Test
    fun `submit availability follows current selected slot validation`() {
        val expired = listOf("2026-06-18T10:30:00-03:00")
        val validAfterRemoval = listOf("2026-06-18T11:30:00-03:00")

        assertFalse(canSubmitSelectedSlots(expired, now))
        assertTrue(canSubmitSelectedSlots(validAfterRemoval, now))
    }

    @Test
    fun `day labels use readable weekday for later days`() {
        val labels = schedulingDayOptions(now, Locale.forLanguageTag("es-AR")).map { it.label }

        assertTrue(labels[2].contains("20"))
    }

    @Test
    fun `centered wheel index keeps middle selections centered`() {
        assertEquals(4, centeredWheelFirstVisibleIndex(selectedIndex = 5, optionCount = 24))
    }

    @Test
    fun `centered wheel index keeps first selections at top edge`() {
        assertEquals(0, centeredWheelFirstVisibleIndex(selectedIndex = 0, optionCount = 24))
        assertEquals(0, centeredWheelFirstVisibleIndex(selectedIndex = 1, optionCount = 24))
    }

    @Test
    fun `centered wheel index keeps last selections at bottom edge`() {
        assertEquals(21, centeredWheelFirstVisibleIndex(selectedIndex = 23, optionCount = 24))
    }

    @Test
    fun `centered wheel index handles invalid or short lists safely`() {
        assertEquals(0, centeredWheelFirstVisibleIndex(selectedIndex = -1, optionCount = 24))
        assertEquals(0, centeredWheelFirstVisibleIndex(selectedIndex = 0, optionCount = 0))
        assertEquals(0, centeredWheelFirstVisibleIndex(selectedIndex = 1, optionCount = 2))
    }
}
