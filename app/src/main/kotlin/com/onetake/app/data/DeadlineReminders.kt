package com.onetake.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.onetake.app.MainActivity
import com.onetake.app.R
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.otTime
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Local deadline reminders — "Morning it's due" (8:00 on the due date) and "Two hours before" —
 * scheduled with AlarmManager from the saved state. Nothing leaves the phone. The receiver re-reads
 * the saved state when it fires, so a tape sent (or an audition deleted) in the meantime stays quiet.
 */
object DeadlineReminders {
    private const val CHANNEL = "deadlines"
    private const val PREFS = "deadline_reminders"
    private const val KEY_CODES = "codes"
    const val EXTRA_AUDITION = "audition"
    const val EXTRA_KIND = "kind"
    const val KIND_MORNING = "morning"
    const val KIND_TWO_HOURS = "two_hours"

    /** Cancels every reminder set earlier and schedules the ones the current state calls for. */
    fun reschedule(context: Context, state: PersistedState) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().forEach { key ->
            val (id, kind) = key.split('|').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
            alarms.cancel(pendingIntent(context, id, kind))
        }
        val now = LocalDateTime.now()
        val scheduled = mutableSetOf<String>()
        state.auditions.filter { it.status != AuditionStatus.Sent }.forEach { a ->
            val times = buildList {
                if (state.notifyMorning) add(KIND_MORNING to a.due.toLocalDate().atTime(8, 0))
                if (state.notifyTwoHours) add(KIND_TWO_HOURS to a.due.minusHours(2))
            }
            times.filter { (_, at) -> at.isAfter(now) && !at.isAfter(a.due) }.forEach { (kind, at) ->
                val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context, a.id, kind))
                scheduled += "${a.id}|$kind"
            }
        }
        prefs.edit().putStringSet(KEY_CODES, scheduled).apply()
    }

    private fun pendingIntent(context: Context, auditionId: String, kind: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.onetake.app.REMINDER.$kind.$auditionId") // unique per reminder
            .putExtra(EXTRA_AUDITION, auditionId)
            .putExtra(EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun post(context: Context, auditionId: String, kind: String) {
        val state = Persistence.load(context) ?: return
        val a = state.auditions.firstOrNull { it.id == auditionId } ?: return
        if (a.status == AuditionStatus.Sent) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Deadlines", NotificationManager.IMPORTANCE_DEFAULT))
        val text = when (kind) {
            KIND_MORNING -> "${a.role} is due today at ${a.due.otTime}. Next: ${a.nextAction.lowercase()}."
            else -> "Two hours left on ${a.role}. Next: ${a.nextAction.lowercase()}."
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(a.project)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify("$auditionId|$kind".hashCode(), notification)
    }
}

/** Fires a scheduled deadline reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(DeadlineReminders.EXTRA_AUDITION) ?: return
        val kind = intent.getStringExtra(DeadlineReminders.EXTRA_KIND) ?: return
        DeadlineReminders.post(context, id, kind)
    }
}

/** Alarms don't survive a reboot — put them back from the saved state. */
class RemindersBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Persistence.load(context)?.let { DeadlineReminders.reschedule(context, it) }
    }
}
