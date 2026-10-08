package org.fossify.calendar.extensions

import android.app.Activity
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.helpers.CycleHelper
import org.fossify.calendar.helpers.TYPE_EVENT
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.RadioItem

private const val ACTION_TOGGLE_PERIOD_START = 100

/**
 * Called when a day is long pressed in any calendar view.
 * Without cycle tracking this keeps the original behaviour (create an event or a task).
 * With cycle tracking enabled it additionally offers to mark / unmark the day as the first day of a period.
 */
fun Activity.showDayLongPressActions(dayCode: String) {
    if (!config.cycleTrackingEnabled) {
        launchNewEventOrTask(dayCode)
        return
    }

    val isRecordedStart = config.cyclePeriodStarts.contains(dayCode)
    val items = arrayListOf(RadioItem(TYPE_EVENT, getString(R.string.event)))
    if (config.allowCreatingTasks) {
        items.add(RadioItem(TYPE_TASK, getString(R.string.task)))
    }
    items.add(
        RadioItem(
            ACTION_TOGGLE_PERIOD_START,
            getString(if (isRecordedStart) R.string.cycle_remove_period_start else R.string.cycle_mark_period_start)
        )
    )

    RadioGroupDialog(this, items) {
        when (it) {
            TYPE_EVENT -> launchNewEventIntent(dayCode)
            TYPE_TASK -> launchNewTaskIntent(dayCode)
            ACTION_TOGGLE_PERIOD_START -> {
                val isNowStart = CycleHelper.togglePeriodStart(this, dayCode)
                toast(if (isNowStart) R.string.cycle_period_start_added else R.string.cycle_period_start_removed)
                onCycleDataChanged()
            }
        }
    }
}

private fun Activity.launchNewEventOrTask(dayCode: String) {
    if (config.allowCreatingTasks) {
        val items = arrayListOf(
            RadioItem(TYPE_EVENT, getString(R.string.event)),
            RadioItem(TYPE_TASK, getString(R.string.task))
        )

        RadioGroupDialog(this, items) {
            if (it == TYPE_EVENT) {
                launchNewEventIntent(dayCode)
            } else {
                launchNewTaskIntent(dayCode)
            }
        }
    } else {
        launchNewEventIntent(dayCode)
    }
}

/** Redraws all calendar views and widgets after the cycle data or settings changed. */
fun Activity.onCycleDataChanged() {
    updateWidgets()
    (this as? MainActivity)?.refreshItems()
}
