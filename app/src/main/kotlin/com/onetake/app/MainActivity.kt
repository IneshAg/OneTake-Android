package com.onetake.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onetake.app.ui.theme.OneTakeTheme
import com.onetake.app.ui.theme.StudioFinish

class MainActivity : ComponentActivity() {

    private val store: AppStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var finish by remember { mutableStateOf(StudioFinish.VELVET) }
            OneTakeTheme(studioFinish = finish) {
                OneTakeApp(
                    store = store,
                    currentFinish = finish,
                    onSelectFinish = { finish = it },
                )
            }
        }
    }
}
