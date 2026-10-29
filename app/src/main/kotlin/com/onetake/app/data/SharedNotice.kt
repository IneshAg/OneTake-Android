package com.onetake.app.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * A casting notice or sides shared to OneTake from another app (mail, WhatsApp, a browser, the
 * gallery, Files) — the text and any attached images or PDFs. MainActivity receives it and Add
 * audition reads it like any other notice.
 */
data class SharedNotice(val text: String?, val uris: List<Uri>) {
    companion object {
        /** The share in [intent], or null when it isn't a share (or carries nothing readable). */
        fun from(intent: Intent?): SharedNotice? {
            if (intent == null) return null
            val uris = when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else -> return null
            }
            // Mail puts the project and role in the subject, so keep it with the body.
            val text = listOfNotNull(
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            ).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n\n").ifEmpty { null }
            return if (text == null && uris.isEmpty()) null else SharedNotice(text, uris)
        }
    }
}
