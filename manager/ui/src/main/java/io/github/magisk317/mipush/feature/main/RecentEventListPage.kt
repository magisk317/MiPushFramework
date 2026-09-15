package io.github.magisk317.mipush.feature.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.feature.main.subpage.EventList
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.mipush.feature.ui.theme.Theme
import io.github.magisk317.uikit.theme.SystemBarsScrim
import io.github.magisk317.uikit.theme.applyEdgeToEdge

open class RecentEventListPage : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyEdgeToEdge(this)
        val packageName = intent?.dataString
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            Theme() {
                RecentEventPage(packageName = packageName)
            }
        }
    }
}

@Composable
private fun RecentEventPage(packageName: String) {
    AppSurface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // The window background used to come from the Page wrapper inside the
        // shared EventList tab composable; the tab composable now relies on its
        // host for the background, so this standalone activity supplies it here.
        Box(Modifier.fillMaxSize()) {
            EventList(
                query = "",
                packageName = packageName,
                contentPadding = PaddingValues(0.dp),
            )
            SystemBarsScrim(navBarAlpha = 0f)
        }
    }
}
