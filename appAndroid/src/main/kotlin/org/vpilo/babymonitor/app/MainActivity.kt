package org.vpilo.babymonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import org.vpilo.babymonitor.android.service.AndroidServiceRegistry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(LocalQuitApplication provides ::quit) {
                App()
            }
        }
    }

    private fun quit() {
        AndroidServiceRegistry.shutdown()
        finishAndRemoveTask()
    }
}
