package org.vpilo.babymonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // It's necessary to ensure the app is reinitialized here: the application might stay in memory after quit, keeping the Application
        // created, but without an Activity.
        onApplicationStart()

        AndroidServiceRegistry.setAppCloseListener(::quit)

        setContent {
            CompositionLocalProvider(LocalQuitApplication provides ::quit) {
                App()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidServiceRegistry.setAppCloseListener(null)
    }

    private fun quit() {
        AndroidServiceRegistry.setAppCloseListener(null)
        AndroidServiceRegistry.shutdown()
        onApplicationStop()
        finishAndRemoveTask()
    }
}
