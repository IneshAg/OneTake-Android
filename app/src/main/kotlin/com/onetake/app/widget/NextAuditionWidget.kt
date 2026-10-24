package com.onetake.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.onetake.app.MainActivity
import com.onetake.app.data.Persistence
import com.onetake.app.models.Audition
import com.onetake.app.models.AuditionStatus
import com.onetake.app.models.otDue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

/** What the widget shows: the soonest unsent audition. Pure, so it's unit-tested. */
data class NextUp(
    val auditionId: String,
    val project: String,
    val role: String,
    val due: LocalDateTime,
    val ready: Int,
    val total: Int,
    val nextTitle: String?,
) {
    /** "13" + "h" under two days, then days. Never negative. */
    fun timeLeft(now: LocalDateTime = LocalDateTime.now()): Pair<String, String> {
        val hours = maxOf(0L, Duration.between(now, due).toHours())
        return if (hours < 48) "$hours" to "h" else "${hours / 24}" to "d"
    }

    /** "2 of 4 · Scene 1 next", or "Ready to send" once everything's in. */
    val progressLine: String
        get() = nextTitle?.let { "$ready of $total · $it next" } ?: "Ready to send"

    companion object {
        fun from(auditions: List<Audition>): NextUp? =
            auditions.filter { it.status != AuditionStatus.Sent }.minByOrNull { it.due }?.let { a ->
                NextUp(a.id, a.project, a.role, a.due, a.readyCount, a.totalCount, a.nextPiece?.title)
            }
    }
}

/** Home-screen "Next audition": project, role, time left and what's next. Tapping it opens that
 *  audition in the app. Reads the app's saved state, so it works while the app is closed. */
class NextAuditionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val next = withContext(Dispatchers.IO) { Persistence.load(context) }?.let { NextUp.from(it.auditions) }
        provideContent { Content(context, next) }
    }

    @Composable
    private fun Content(context: Context, next: NextUp?) {
        val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1B181E))
        val primary = ColorProvider(day = Color(0xFF1E1A22), night = Color(0xFFF3EEF5))
        val secondary = ColorProvider(day = Color(0xFF5A5262), night = Color(0xFFB8AEBF))
        val accent = ColorProvider(day = Color(0xFF5B3E73), night = Color(0xFFC8A8E0))
        val due = ColorProvider(day = Color(0xFF8E6210), night = Color(0xFFE2BE7A))
        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_AUDITION
            next?.let { putExtra(EXTRA_AUDITION_ID, it.auditionId) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        Column(
            GlanceModifier.fillMaxSize().background(background).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity(open)),
        ) {
            Text("NEXT UP", style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            if (next == null) {
                Text("Nothing due", style = TextStyle(color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text("Add an audition in OneTake", style = TextStyle(color = secondary, fontSize = 12.sp))
            } else {
                Text(next.project, maxLines = 1, style = TextStyle(color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(next.role, maxLines = 1, style = TextStyle(color = secondary, fontSize = 12.sp))
                Spacer(GlanceModifier.defaultWeight())
                Row(verticalAlignment = Alignment.Bottom) {
                    val (number, unit) = next.timeLeft()
                    Text(number, style = TextStyle(color = due, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Text(unit, style = TextStyle(color = due, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("due ${next.due.otDue}", maxLines = 1, style = TextStyle(color = secondary, fontSize = 12.sp))
                }
                Text(next.progressLine, maxLines = 1, style = TextStyle(color = primary, fontSize = 12.sp))
            }
        }
    }

    companion object {
        const val ACTION_OPEN_AUDITION = "com.onetake.app.OPEN_AUDITION"
        const val EXTRA_AUDITION_ID = "auditionId"

        /** Redraws every placed widget from the saved state. Call after each save. */
        suspend fun refresh(context: Context) {
            runCatching { NextAuditionWidget().updateAll(context) }
        }
    }
}

class NextAuditionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextAuditionWidget()
}
