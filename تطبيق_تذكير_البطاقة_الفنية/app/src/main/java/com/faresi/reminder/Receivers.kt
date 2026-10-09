package com.faresi.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val snooze = intent.getBooleanExtra("snooze", false)
        val list = Store.load(context)
        val r = list.find { it.id == id } ?: return

        Notifier.show(context, r)

        if (!snooze) {
            if (r.repeat == 0) {
                r.active = false
                Store.save(context, list)
            } else {
                Scheduler.schedule(context, r)
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val DONE = "com.faresi.reminder.DONE"
        const val SNOOZE = "com.faresi.reminder.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        if (id < 0) return
        NotificationManagerCompat.from(context).cancel(id)
        if (intent.action == SNOOZE) {
            Scheduler.scheduleSnooze(context, id, System.currentTimeMillis() + 10 * 60 * 1000L)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val list = Store.load(context)
        var changed = false
        list.forEach { r ->
            if (r.active) {
                if (Scheduler.nextTrigger(r) == null) {
                    r.active = false
                    changed = true
                } else {
                    Scheduler.schedule(context, r)
                }
            }
        }
        if (changed) Store.save(context, list)
    }
}
