// Shared form pieces for the owner screens (opening hours, working hours, time off).
package com.group12.salonlink.feature.owner

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.group12.salonlink.R
import com.group12.salonlink.domain.model.TimeRange
import com.group12.salonlink.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** One editable day: open or closed, and from/to. Kept even when closed so re-opening restores it. */
data class DayHoursDraft(val open: Boolean, val start: LocalTime, val end: LocalTime)

/** Editable copy of [WeeklyHours], Monday first. */
data class HoursDraft(val days: Map<DayOfWeek, DayHoursDraft>) {

    /** True if every open day starts before it ends. */
    val isValid: Boolean get() = days.values.none { it.open && it.start >= it.end }

    fun toWeeklyHours(): WeeklyHours =
        WeeklyHours(days.filterValues { it.open && it.start < it.end }.mapValues { TimeRange(it.value.start, it.value.end) })

    fun update(day: DayOfWeek, change: (DayHoursDraft) -> DayHoursDraft) = copy(days = days + (day to change(days.getValue(day))))

    companion object {
        private val DEFAULT_START = LocalTime.of(9, 0)
        private val DEFAULT_END = LocalTime.of(17, 0)

        fun from(hours: WeeklyHours) = HoursDraft(
            DayOfWeek.entries.associateWith { day ->
                hours.days[day]?.let { DayHoursDraft(true, it.start, it.end) } ?: DayHoursDraft(false, DEFAULT_START, DEFAULT_END)
            },
        )
    }
}

/** Seven rows: day name, open switch and from / to buttons. */
@Composable
fun WeeklyHoursEditor(draft: HoursDraft, onChange: (HoursDraft) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { day ->
            val d = draft.days.getValue(day)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(44.dp),
                )
                Switch(checked = d.open, onCheckedChange = { open -> onChange(draft.update(day) { it.copy(open = open) }) })
                if (d.open) {
                    TimeButton(d.start, stringResource(R.string.block_from)) { t -> onChange(draft.update(day) { it.copy(start = t) }) }
                    Text("–")
                    TimeButton(d.end, stringResource(R.string.block_to)) { t -> onChange(draft.update(day) { it.copy(end = t) }) }
                } else {
                    Text(stringResource(R.string.hours_closed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Shows a time and opens Android's clock picker when tapped.
 * WHY the platform dialog: it is familiar, accessible and needs no extra library.
 */
@Composable
fun TimeButton(time: LocalTime, label: String, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val text = "%02d:%02d".format(time.hour, time.minute)
    AssistChip(
        onClick = {
            TimePickerDialog(context, { _, h, m -> onPick(LocalTime.of(h, m - m % 10)) }, time.hour, time.minute, true).show()
        },
        label = { Text(text) },
        modifier = Modifier.semantics { contentDescription = "$label $text" },
    )
}

/** Shows a date and opens Android's calendar picker when tapped (past days are disabled). */
@Composable
fun DateButton(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    AssistChip(
        onClick = {
            DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth)
                .apply { datePicker.minDate = System.currentTimeMillis() - 1000 }
                .show()
        },
        label = { Text(date.toString()) },
    )
}

/** A label with a switch on the right. */
@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
