package com.onetake.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.onetake.app.data.SharedNotice
import com.onetake.app.ui.theme.OneTakeTheme
import com.onetake.app.ui.theme.StudioFinish
import com.onetake.app.widget.NextAuditionWidget

class MainActivity : ComponentActivity() {

    private val store: AppStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after recreation the share was already taken.
        if (savedInstanceState == null) handle(intent)
        enableEdgeToEdge()
        setContent {
            // Saveable: survives activity recreation (e.g. a dark-mode switch) instead of resetting.
            var finish by rememberSaveable { mutableStateOf(StudioFinish.VELVET) }
            OneTakeTheme(studioFinish = finish) {
                OneTakeApp(
                    store = store,
                    currentFinish = finish,
                    onSelectFinish = { finish = it },
                )
            }
        }
    }

    /** launchMode="singleTask": a share while OneTake is open arrives here rather than starting a
     *  second activity with its own AppStore writing the same state file. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A share from another app, or a tap on the home-screen widget. */
    private fun handle(intent: Intent?) {
        SharedNotice.from(intent)?.let { store.incomingShare = it }
        if (intent?.action == NextAuditionWidget.ACTION_OPEN_AUDITION) {
            store.openAuditionRequest = intent.getStringExtra(NextAuditionWidget.EXTRA_AUDITION_ID)
        }
    }
}
