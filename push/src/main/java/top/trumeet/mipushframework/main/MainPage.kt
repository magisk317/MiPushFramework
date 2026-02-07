@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.xiaomi.xmsf.R
import top.trumeet.mipushframework.MainPageUtils
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.main.subpage.ApplicationList
import top.trumeet.mipushframework.main.subpage.ApplicationListPreview
import top.trumeet.mipushframework.main.subpage.EventDetailsDialogPreview
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.mipushframework.main.subpage.EventListPreview
import top.trumeet.mipushframework.main.subpage.Settings
import top.trumeet.mipushframework.main.subpage.SettingsPagePreview
import top.trumeet.ui.theme.Theme
import kotlinx.coroutines.launch

private val mainPageUtils1 = MainPageUtils()
private var placeholder by mutableStateOf("Search...")

class MainPage : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        mainPageUtils1.initOnCreate(applicationContext) { placeholder = it.toString() }
        setContent {
            Theme {
                window.navigationBarColor = MaterialTheme.colorScheme.surfaceColorAtElevation(
                    NavigationBarDefaults.Elevation
                ).toArgb()
            }
            Main(
                Screen.Apps.route.toString(),
                eventsPage = { query -> EventList(query) },
                appsPage = { query -> ApplicationList(query) },
                settingsPage = { Settings() }
            )
        }
    }
}

private sealed class Screen(val route: Int, val icon: Int) {
    object Events : Screen(R.string.main_event, R.drawable.ic_event_note_black_24dp)
    object Apps : Screen(R.string.main_apps, R.drawable.ic_apps_black_24dp)
    object Settings : Screen(R.string.main_settings, R.drawable.ic_settings_black_24dp)
}

@Composable
fun BottomNavigationBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val items = listOf(Screen.Events, Screen.Apps, Screen.Settings)

    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .height(72.dp),
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets,
    ) {
        items.forEachIndexed { index, screen ->
            val name = stringResource(screen.route)
            NavigationBarItem(
                icon = { Icon(painterResource(id = screen.icon), contentDescription = name) },
                label = { androidx.compose.material3.Text(name) },
                selected = selectedIndex == index,
                alwaysShowLabel = false,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Main(
    startDestination: String,
    eventsPage: @Composable (String) -> Unit,
    appsPage: @Composable (String) -> Unit,
    settingsPage: @Composable () -> Unit
) {
    val initialIndex = when (startDestination) {
        Screen.Events.route.toString() -> 0
        Screen.Apps.route.toString() -> 1
        Screen.Settings.route.toString() -> 2
        else -> 1
    }
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var eventsQuery by rememberSaveable { mutableStateOf("") }
    var appsQuery by rememberSaveable { mutableStateOf("") }
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    Theme {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                when (currentPage) {
                    0 -> SearchBar(placeholder, eventsQuery) { eventsQuery = it }
                    1 -> SearchBar(placeholder, appsQuery) { appsQuery = it }
                    else -> CenterAlignedTopAppBar(
                        title = { androidx.compose.material3.Text(stringResource(Screen.Settings.route)) },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
                    )
                }
            },
            bottomBar = {
                BottomNavigationBar(currentPage) { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> eventsPage(eventsQuery)
                        1 -> appsPage(appsQuery)
                        else -> settingsPage()
                    }
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainEventsPreview() {
    Main(
        Screen.Events.route.toString(),
        eventsPage = { _ ->
            Column {
                EventListPreview()
            }
        },
        appsPage = { _ -> },
        settingsPage = { }
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainAppsPreview() {
    Main(
        Screen.Apps.route.toString(),
        eventsPage = { _ -> },
        appsPage = { _ ->
            Column {
                ApplicationListPreview()
            }
        },
        settingsPage = { }
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainSettingsPreview() {
    Main(
        Screen.Settings.route.toString(),
        eventsPage = { _ -> },
        appsPage = { _ -> },
        settingsPage = { SettingsPagePreview() }
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
private fun MainDialogPreview() {
    Main(
        Screen.Events.route.toString(),
        eventsPage = { _ -> EventDetailsDialogPreview() },
        appsPage = { _ -> },
        settingsPage = { }
    )
}
