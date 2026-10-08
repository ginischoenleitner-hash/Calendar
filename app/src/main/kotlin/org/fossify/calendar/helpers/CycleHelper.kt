package org.fossify.calendar.helpers

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.commons.extensions.adjustAlpha
import org.joda.time.Days
import org.joda.time.LocalDate

/**
 * A boundary of a cycle phase, expressed in days (never weeks).
 * Cycle days are 1-based: day 1 is the first day of the period.
 */
sealed class CycleDay {
    /** The n-th day of the cycle, counted from the first day of the period (1 = first day). */
    data class FromStart(val day: Int) : CycleDay()

    /** Counted back from the last day of the cycle (0 = last day, 14 = fourteen days before the last day). */
    data class FromEnd(val daysBeforeEnd: Int) : CycleDay()

    fun resolve(cycleLength: Int): Int = when (this) {
        is FromStart -> day
        is FromEnd -> cycleLength - daysBeforeEnd
    }.coerceIn(1, cycleLength)
}

data class CyclePhase(
    val id: String,
    @StringRes val nameRes: Int,
    val start: CycleDay,
    val end: CycleDay,
    val defaultColor: Int
)

data class CycleDayInfo(
    /** 1-based day within the current cycle */
    val cycleDay: Int,
    /** length of the cycle this day belongs to (measured for completed cycles, configured for predicted ones) */
    val cycleLength: Int,
    /** null if no phase covers this cycle day */
    val phase: CyclePhase?,
    /** true if this day is a recorded first day of a period */
    val isRecordedStart: Boolean,
    /** true if the day lies after the last recorded period start, i.e. it is a forecast */
    val isPredicted: Boolean
)

object CycleHelper {
    /**
     * The cycle phases, in order. The first phase whose range contains a cycle day wins.
     *
     * PLACEHOLDER definitions, to be replaced with the user's own phase definitions.
     */
    val PHASES: List<CyclePhase> = listOf(
        CyclePhase(
            id = "menstruation",
            nameRes = R.string.cycle_phase_menstruation,
            start = CycleDay.FromStart(1),
            end = CycleDay.FromStart(5),
            defaultColor = 0xFFF6C1CB.toInt()   // pastel rose
        ),
        CyclePhase(
            id = "follicular",
            nameRes = R.string.cycle_phase_follicular,
            start = CycleDay.FromStart(6),
            end = CycleDay.FromEnd(16),
            defaultColor = 0xFFCDEBD6.toInt()   // pastel mint
        ),
        CyclePhase(
            id = "ovulation",
            nameRes = R.string.cycle_phase_ovulation,
            start = CycleDay.FromEnd(15),
            end = CycleDay.FromEnd(13),
            defaultColor = 0xFFFFE1B8.toInt()   // pastel apricot
        ),
        CyclePhase(
            id = "luteal",
            nameRes = R.string.cycle_phase_luteal,
            start = CycleDay.FromEnd(12),
            end = CycleDay.FromEnd(0),
            defaultColor = 0xFFDCD4F2.toInt()   // pastel lavender
        )
    )

    fun dayCodeToLocalDate(dayCode: String): LocalDate? = try {
        LocalDate(
            dayCode.substring(0, 4).toInt(),
            dayCode.substring(4, 6).toInt(),
            dayCode.substring(6, 8).toInt()
        )
    } catch (e: Exception) {
        null
    }

    fun localDateToDayCode(date: LocalDate): String = date.toString("yyyyMMdd")

    fun getPhaseForCycleDay(cycleDay: Int, cycleLength: Int): CyclePhase? =
        PHASES.firstOrNull { cycleDay in it.start.resolve(cycleLength)..it.end.resolve(cycleLength) }

    /** Creates a calculator from the current settings, or null when cycle tracking is off or no period start is recorded. */
    fun getCalculator(context: Context): CycleCalculator? {
        val config = context.config
        if (!config.cycleTrackingEnabled) {
            return null
        }

        val starts = config.cyclePeriodStarts.mapNotNull { dayCodeToLocalDate(it) }.sorted()
        if (starts.isEmpty()) {
            return null
        }

        val colors = PHASES.associate { it.id to config.getCyclePhaseColor(it) }
        return CycleCalculator(starts, config.cycleLength, colors)
    }

    /**
     * Pastel colors are used almost as-is on light backgrounds; on dark backgrounds
     * they are toned down so that light text stays readable.
     */
    fun getDisplayColor(phaseColor: Int, backgroundColor: Int): Int {
        val isLightBackground = ColorUtils.calculateLuminance(backgroundColor or 0xFF000000.toInt()) > 0.5
        return phaseColor.adjustAlpha(if (isLightBackground) 0.85f else 0.30f)
    }

    /** Changes whenever anything that affects the cycle coloring changes, used to invalidate cached views. */
    fun getSignature(context: Context): String {
        val config = context.config
        if (!config.cycleTrackingEnabled) {
            return ""
        }

        val colors = PHASES.joinToString(",") { config.getCyclePhaseColor(it).toString() }
        return "${config.cycleLength}|${config.cyclePeriodStarts.joinToString(",")}|$colors"
    }

    fun togglePeriodStart(context: Context, dayCode: String): Boolean {
        val config = context.config
        val starts = config.cyclePeriodStarts.toMutableList()
        val isNowStart = if (starts.contains(dayCode)) {
            starts.remove(dayCode)
            false
        } else {
            starts.add(dayCode)
            true
        }
        config.cyclePeriodStarts = starts
        return isNowStart
    }
}

class CycleCalculator(
    private val starts: List<LocalDate>,
    private val configuredLength: Int,
    private val phaseColors: Map<String, Int>
) {
    fun getInfo(dayCode: String): CycleDayInfo? {
        val date = CycleHelper.dayCodeToLocalDate(dayCode) ?: return null
        return getInfo(date)
    }

    fun getInfo(date: LocalDate): CycleDayInfo? {
        // nothing is shown before the first recorded period start
        val index = starts.indexOfLast { !it.isAfter(date) }
        if (index == -1) {
            return null
        }

        val start = starts[index]
        val next = starts.getOrNull(index + 1)
        val daysSinceStart = Days.daysBetween(start, date).days

        if (next != null) {
            val measuredLength = Days.daysBetween(start, next).days
            // a very long gap most likely means some period starts were not entered,
            // so fall back to repeating the configured cycle length
            if (measuredLength <= configuredLength * 2) {
                val cycleDay = daysSinceStart + 1
                return CycleDayInfo(
                    cycleDay = cycleDay,
                    cycleLength = measuredLength,
                    phase = CycleHelper.getPhaseForCycleDay(cycleDay, measuredLength),
                    isRecordedStart = daysSinceStart == 0,
                    isPredicted = false
                )
            }
        }

        val cycleDay = daysSinceStart % configuredLength + 1
        return CycleDayInfo(
            cycleDay = cycleDay,
            cycleLength = configuredLength,
            phase = CycleHelper.getPhaseForCycleDay(cycleDay, configuredLength),
            isRecordedStart = daysSinceStart == 0,
            isPredicted = next == null
        )
    }

    /** The phase color for the given day, or null if the day has no phase. */
    fun getPhaseColor(dayCode: String): Int? {
        val phase = getInfo(dayCode)?.phase ?: return null
        return phaseColors[phase.id] ?: phase.defaultColor
    }
}
