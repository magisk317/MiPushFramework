package io.github.magisk317.mipush.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.chrisbanes.haze.HazeState
import dagger.hilt.android.AndroidEntryPoint
import io.github.magisk317.mipush.feature.main.subpage.EventList
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.mipush.feature.ui.theme.SystemBarsScrim
import io.github.magisk317.mipush.feature.ui.theme.rememberHazeStyle

@AndroidEntryPoint
open class RecentEventListPage : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val packageName = intent.dataString!!
        setContent {
            Theme {
                RecentEventPage(packageName = packageName)
            }
        }
    }
}

@Composable
private fun RecentEventPage(packageName: String) {
    val hazeState = remember { HazeState() }
    val hazeStyle = rememberHazeStyle()

    Box(Modifier.fillMaxSize()) {
        EventList(
            query = "",
            packageName = packageName,
            contentPadding = PaddingValues(0.dp),
            hazeState = hazeState,
            hazeStyle = hazeStyle,
        )
        SystemBarsScrim(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            showTop = false,
            showBottom = true,
            bottomBackgroundAlpha = 0f,
        )
    }
}
