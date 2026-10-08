package org.fossify.calendar.dialogs

import android.app.Activity
import android.app.DatePickerDialog
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.R
import org.fossify.calendar.extensions.config
import org.fossify.commons.helpers.getJavaDayOfWeekFromISO
import org.fossify.calendar.helpers.CycleHelper
import org.fossify.calendar.helpers.Formatter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDatePickerDialogTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.joda.time.LocalDate

/**
 * Lists every recorded first day of a period (newest first).
 * Tapping a date removes it after a confirmation, the neutral button adds a new one via a date picker.
 */
class CyclePeriodStartsDialog(val activity: Activity, val callback: () -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val margin = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val textColor = activity.getProperTextColor()
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, margin / 2, 0, 0)
        }

        val starts = activity.config.cyclePeriodStarts.sortedDescending()
        val hint = TextView(activity).apply {
            setText(if (starts.isEmpty()) R.string.cycle_no_period_starts else R.string.cycle_tap_to_remove)
            setTextColor(textColor)
            alpha = 0.7f
            setPadding(margin, 0, margin, margin / 2)
        }
        list.addView(hint)

        starts.forEach { dayCode ->
            val row = TextView(activity).apply {
                text = Formatter.getDayTitle(activity, dayCode)
                setTextColor(textColor)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(margin, margin / 2, margin, margin / 2)
                val outValue = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { confirmRemoval(dayCode) }
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val scrollView = ScrollView(activity).apply { addView(list) }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNeutralButton(R.string.cycle_add_period_start) { _, _ -> pickNewStart() }
            .apply {
                activity.setupDialogStuff(scrollView, this, R.string.cycle_period_starts) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun confirmRemoval(dayCode: String) {
        ConfirmationDialog(
            activity = activity,
            message = "${activity.getString(R.string.cycle_remove_period_start)}: ${Formatter.getDayTitle(activity, dayCode)}?"
        ) {
            CycleHelper.togglePeriodStart(activity, dayCode)
            callback()
            dialog?.dismiss()
            CyclePeriodStartsDialog(activity, callback)
        }
    }

    private fun pickNewStart() {
        val today = LocalDate.now()
        val datePicker = DatePickerDialog(
            activity,
            activity.getDatePickerDialogTheme(),
            { _, year, monthOfYear, dayOfMonth ->
                val dayCode = CycleHelper.localDateToDayCode(LocalDate(year, monthOfYear + 1, dayOfMonth))
                if (!activity.config.cyclePeriodStarts.contains(dayCode)) {
                    CycleHelper.togglePeriodStart(activity, dayCode)
                }
                callback()
                CyclePeriodStartsDialog(activity, callback)
            },
            today.year,
            today.monthOfYear - 1,
            today.dayOfMonth
        )

        datePicker.datePicker.firstDayOfWeek = getJavaDayOfWeekFromISO(activity.config.firstDayOfWeek)
        datePicker.show()
    }
}
