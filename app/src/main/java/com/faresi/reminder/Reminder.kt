package com.faresi.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

// repeat: 0 مرة واحدة، 1 يومي، 2 أسبوعي، 3 شهري
// days: أيام الأسبوع كأقنعة بتات (الأحد = البت 0)
data class Reminder(
    val id: Int,
    var title: String,
    var note: String,
    var year: Int,
    var month: Int,
    var day: Int,
    var hour: Int,
    var minute: Int,
    var repeat: Int,
    var days: Int,
    var active: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("note", note)
        put("year", year); put("month", month); put("day", day)
        put("hour", hour); put("minute", minute)
        put("repeat", repeat); put("days", days); put("active", active)
    }

    companion object {
        fun fromJson(o: JSONObject) = Reminder(
            o.getInt("id"), o.getString("title"), o.optString("note", ""),
            o.getInt("year"), o.getInt("month"), o.getInt("day"),
            o.getInt("hour"), o.getInt("minute"),
            o.getInt("repeat"), o.getInt("days"), o.optBoolean("active", true)
        )
    }
}

object Store {
    private const val PREF = "reminders"
    private const val KEY = "list"

    fun load(c: Context): MutableList<Reminder> {
        val s = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(s)
        val out = mutableListOf<Reminder>()
        for (i in 0 until arr.length()) out.add(Reminder.fromJson(arr.getJSONObject(i)))
        return out
    }

    fun save(c: Context, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun nextId(list: List<Reminder>): Int = (list.maxOfOrNull { it.id } ?: 0) + 1
}

object Scheduler {

    fun nextTrigger(r: Reminder, now: Long = System.currentTimeMillis()): Long? {
        val base = Calendar.getInstance().apply { timeInMillis = now }

        if (r.repeat == 0) {
            val c = Calendar.getInstance()
            c.set(r.year, r.month, r.day, r.hour, r.minute, 0)
            c.set(Calendar.MILLISECOND, 0)
            return if (c.timeInMillis > now) c.timeInMillis else null
        }

        if (r.repeat == 1) {
            val c = base.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, r.hour)
            c.set(Calendar.MINUTE, r.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
            return c.timeInMillis
        }

        if (r.repeat == 2) {
            if (r.days == 0) return null
            for (i in 0..7) {
                val c = base.clone() as Calendar
                c.add(Calendar.DAY_OF_MONTH, i)
                c.set(Calendar.HOUR_OF_DAY, r.hour)
                c.set(Calendar.MINUTE, r.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                val dow = c.get(Calendar.DAY_OF_WEEK) - 1
                if (((r.days shr dow) and 1) == 1 && c.timeInMillis > now) return c.timeInMillis
            }
            return null
        }

        // شهري
        for (i in 0..13) {
            val c = base.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, i)
            if (r.day > c.getActualMaximum(Calendar.DAY_OF_MONTH)) continue
            c.set(Calendar.DAY_OF_MONTH, r.day)
            c.set(Calendar.HOUR_OF_DAY, r.hour)
            c.set(Calendar.MINUTE, r.minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > now) return c.timeInMillis
        }
        return null
    }

    private fun pending(c: Context, id: Int, requestCode: Int, snooze: Boolean): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
            .putExtra("id", id)
            .putExtra("snooze", snooze)
        return PendingIntent.getBroadcast(
            c, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(c: Context, time: Long, pi: PendingIntent) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun schedule(c: Context, r: Reminder) {
        cancel(c, r.id)
        if (!r.active) return
        val t = nextTrigger(r) ?: return
        setAlarm(c, t, pending(c, r.id, r.id, false))
    }

    fun scheduleSnooze(c: Context, id: Int, time: Long) {
        setAlarm(c, time, pending(c, id, id + 100000, true))
    }

    fun cancel(c: Context, id: Int) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, id, id, false))
        am.cancel(pending(c, id, id + 100000, true))
    }
}
