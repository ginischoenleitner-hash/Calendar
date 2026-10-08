package org.fossify.calendar.dialogs

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.fossify.calendar.extensions.config
import org.fossify.calendar.helpers.CycleHelper
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.setupDialogStuff

/** Shows every cycle phase with its color; tapping a phase opens a color picker. */
class CyclePhaseColorsDialog(val activity: Activity, val callback: () -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val margin = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val swatchSize = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.color_sample_size)
        val textColor = activity.getProperTextColor()
        val backgroundColor = activity.getProperBackgroundColor()

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, margin / 2, 0, 0)
        }

        CycleHelper.PHASES.forEach { phase ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(margin, margin / 2, margin, margin / 2)
                val outValue = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val name = TextView(activity).apply {
                setText(phase.nameRes)
                setTextColor(textColor)
                textSize = 16f
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val swatch = ImageView(activity)
            swatch.setFillWithStroke(activity.config.getCyclePhaseColor(phase), backgroundColor)
            row.addView(swatch, LinearLayout.LayoutParams(swatchSize, swatchSize))

            row.setOnClickListener {
                ColorPickerDialog(
                    activity = activity,
                    color = activity.config.getCyclePhaseColor(phase)
                ) { wasPositivePressed, color ->
                    if (wasPositivePressed) {
                        activity.config.setCyclePhaseColor(phase, color)
                        swatch.setFillWithStroke(color, backgroundColor)
                        callback()
                    }
                }
            }

            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(list, this, org.fossify.calendar.R.string.cycle_phase_colors) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
