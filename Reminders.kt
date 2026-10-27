package com.trip.itinerary

import android.app.*
import android.content.*
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "trip"
    private const val LEAD_MIN = 10L
    private val zone = ZoneId.of("Asia/Jakarta")

    private fun intent(ctx: Context) = Intent(ctx, ReminderReceiver::class.java)

    fun schedule(ctx: Context, days: List<Day>) {
        cancel(ctx, days)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        days.forEachIndexed { d, day ->
            day.items.forEachIndexed { i, item ->
                if (item.start.isEmpty()) return@forEachIndexed
                val at = LocalDateTime.parse(item.start).atZone(zone).toInstant().toEpochMilli() - LEAD_MIN * 60_000
                if (at <= now) return@forEachIndexed
                val it = intent(ctx).putExtra("id", d * 100 + i).putExtra("title", item.title)
                    .putExtra("text", "Mulai ${item.start.takeLast(5)} · $LEAD_MIN menit lagi" +
                            if (item.note.isNotEmpty()) " · ${item.note}" else "")
                val pi = PendingIntent.getBroadcast(ctx, d * 100 + i, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancel(ctx: Context, days: List<Day>) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        days.forEachIndexed { d, day ->
            day.items.indices.forEach { i ->
                PendingIntent.getBroadcast(ctx, d * 100 + i, intent(ctx),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it) }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (ctx.getSharedPreferences("trip", Context.MODE_PRIVATE).getBoolean("remind", false))
                Reminders.schedule(ctx, loadDays(ctx))
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(Reminders.CHANNEL, "Pengingat itinerary", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra("title"))
            .setContentText(intent.getStringExtra("text"))
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(intent.getIntExtra("id", 0), n)
    }
}
