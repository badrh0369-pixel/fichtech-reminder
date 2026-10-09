package com.faresi.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    const val CHANNEL = "reminders_v1"

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "تذكيرات إعداد البطاقة الفنية"
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 500, 250, 500)
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun show(c: Context, r: Reminder) {
        ensureChannel(c)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val open = PendingIntent.getActivity(c, r.id, Intent(c, MainActivity::class.java), flags)
        val done = PendingIntent.getBroadcast(
            c, r.id * 10 + 1,
            Intent(c, ActionReceiver::class.java).setAction(ActionReceiver.DONE).putExtra("id", r.id),
            flags
        )
        val snooze = PendingIntent.getBroadcast(
            c, r.id * 10 + 2,
            Intent(c, ActionReceiver::class.java).setAction(ActionReceiver.SNOOZE).putExtra("id", r.id),
            flags
        )

        val text = if (r.note.isBlank()) "حان وقت إعداد البطاقة الفنية" else r.note
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(r.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "تم", done)
            .addAction(0, "أجّل 10 دقائق", snooze)
            .build()

        try {
            NotificationManagerCompat.from(c).notify(r.id, n)
        } catch (e: SecurityException) {
            // إذن الإشعارات غير ممنوح
        }
    }
}
