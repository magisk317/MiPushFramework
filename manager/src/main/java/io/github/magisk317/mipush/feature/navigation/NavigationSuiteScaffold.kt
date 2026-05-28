package io.github.magisk317.mipush.feature.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * Responsive scaffold that switches between bottom bar, navigation rail, or drawer
 * depending on screen width.
 *
 * Usage example:
 * ```kotlin
 * NavigationSuiteScaffold(
 *     navController = navController,
 *     bottomBar = { /* BottomNavigationBarV2 */ },
 *     navigationRail = { /* optional rail for wider screens */ },
 *     drawerContent = { /* ModalNavigationDrawerContent */ },
 * ) { innerPadding ->
 *     AppNavHostContent(..., contentPadding = innerPadding, helpPage = { ... })
 * }
 * ```
 */
@Composable
fun NavigationSuiteScaffold(
    bottomBar: @Composable () -> Unit,
    navigationRail: (@Composable () -> Unit)? = null,
    drawerContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    // cutover widths match Material3 breakpoints
    val widthDp = LocalConfiguration.current.screenWidthDp
    when {
        widthDp >= 840 -> {
            // large screen: use permanent drawer + rail
            ModalNavigationDrawer(
                drawerContent = {
                    ModalDrawerSheet {
                        drawerContent?.invoke(this)
                    }
                }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    navigationRail?.invoke()
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues())
                    }
                }
            }
        }
        widthDp >= 600 -> {
            // medium screen: navigation rail
            Row(modifier = Modifier.fillMaxSize()) {
                navigationRail?.invoke()
                Box(modifier = Modifier.weight(1f)) {
                    content(PaddingValues())
                }
            }
        }
        else -> {
            // small screen: bottom bar
            Scaffold(
                bottomBar = bottomBar,
                content = content
            )
        }
    }
}
