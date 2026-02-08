@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.trumeet.mipushframework.MainPageUtils
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.main.subpage.ApplicationList
import top.trumeet.mipushframework.main.subpage.ApplicationListPreview
import top.trumeet.mipushframework.main.subpage.EventDetailsDialogPreview
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.mipushframework.main.subpage.EventListPreview
import top.trumeet.mipushframework.main.subpage.Settings
import top.trumeet.mipushframework.main.subpage.SettingsPagePreview
import top.trumeet.ui.theme.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magisk317.data.DataStoreManager
import com.magisk317.main.viewmodel.SettingsViewModel

private val mainPageUtils1 = MainPageUtils()
private var placeholder by mutableStateOf("Search...")

class MainPage : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainPageUtils1.initOnCreate(applicationContext) { placeholder = it.toString() }
        setContent {
            Theme {
                Main(
                    Screen.Apps.route.toString(),
                    eventsPage = { query, padding, refreshSignal, groupByApp ->
                        EventList(
                            query,
                            contentPadding = padding,
                            refreshSignal = refreshSignal,
                            groupByApp = groupByApp
                        )
                    },
                    appsPage = { query, padding, refreshSignal ->
                        ApplicationList(query, contentPadding = padding, refreshSignal = refreshSignal)
                    },
                    settingsPage = { padding, onAbout -> 
                        Settings(padding, onShowAboutDialog = onAbout) 
                    }
                )
            }
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
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onSelect: (Int) -> Unit,
    onTabDoubleTap: (Int) -> Unit
) {
    val items = listOf(Screen.Events, Screen.Apps, Screen.Settings)
    var lastTappedIndex by remember { mutableStateOf(-1) }
    var lastTappedAt by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hazeEffect(hazeState, hazeStyle) {
                forceInvalidateOnPreDraw = true
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
            .navigationBarsPadding()
    ) {
        NavigationBar(
            modifier = Modifier.height(72.dp),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            items.forEachIndexed { index, screen ->
                val name = stringResource(screen.route)
                NavigationBarItem(
                    icon = { Icon(painterResource(id = screen.icon), contentDescription = name) },
                    label = { androidx.compose.material3.Text(name) },
                    selected = selectedIndex == index,
                    alwaysShowLabel = false,
                    onClick = {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTapOnCurrentTab =
                            selectedIndex == index &&
                                lastTappedIndex == index &&
                                now - lastTappedAt < 450L
                        if (isDoubleTapOnCurrentTab) {
                            onTabDoubleTap(index)
                        } else {
                            onSelect(index)
                        }
                        lastTappedIndex = index
                        lastTappedAt = now
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Main(
    startDestination: String,
    eventsPage: @Composable (String, PaddingValues, Int, Boolean) -> Unit,
    appsPage: @Composable (String, PaddingValues, Int) -> Unit,
    settingsPage: @Composable (PaddingValues, (String) -> Unit) -> Unit
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
    var eventsRefreshSignal by rememberSaveable { mutableStateOf(0) }
    var appsRefreshSignal by rememberSaveable { mutableStateOf(0) }
    var showEventDisplayModeMenu by remember { mutableStateOf(false) }
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember { com.magisk317.Global.ConfigCenter() }
    val hazeBlurRadius by DataStoreManager.hazeBlurRadius.collectAsStateWithLifecycle(initialValue = 25)
    val hazeTintAlpha by DataStoreManager.hazeTintAlpha.collectAsStateWithLifecycle(initialValue = 0.2f)
    val eventGroupByApp by DataStoreManager.eventGroupByApp.collectAsStateWithLifecycle(initialValue = false)
    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
    val onShowAboutDialog: (String) -> Unit = { content -> aboutDialogContent = content }

    val hazeState = remember { HazeState() }
    val hazeStyle = rememberHazeStyle(blurRadius = hazeBlurRadius.dp, tintAlpha = hazeTintAlpha)

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                val topBarModifier = Modifier
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                    .statusBarsPadding()

                when (currentPage) {
                    0 -> Row(
                        modifier = topBarModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchBar(
                            placeholder,
                            eventsQuery,
                            modifier = Modifier.weight(1f)
                        ) { eventsQuery = it }
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(
                                onClick = { showEventDisplayModeMenu = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings_black_24dp),
                                    contentDescription = "Record display mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showEventDisplayModeMenu,
                                onDismissRequest = { showEventDisplayModeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("不分组（默认）") },
                                    onClick = {
                                        showEventDisplayModeMenu = false
                                        scope.launch { DataStoreManager.setEventGroupByApp(false) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("按应用分组") },
                                    onClick = {
                                        showEventDisplayModeMenu = false
                                        scope.launch { DataStoreManager.setEventGroupByApp(true) }
                                    }
                                )
                            }
                        }
                    }

                    1 -> SearchBar(
                        placeholder,
                        appsQuery,
                        modifier = topBarModifier
                    ) { appsQuery = it }

                    else -> CenterAlignedTopAppBar(
                        title = { androidx.compose.material3.Text(stringResource(Screen.Settings.route)) },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = topBarModifier
                    )
                }
            },
            bottomBar = {
                BottomNavigationBar(
                    currentPage,
                    hazeState,
                    hazeStyle,
                    onSelect = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    onTabDoubleTap = { index ->
                        when (index) {
                            0 -> eventsRefreshSignal++
                            1 -> appsRefreshSignal++
                        }
                    }
                )
            }
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
            ) { page ->
                when (page) {
                    0 -> eventsPage(eventsQuery, innerPadding, eventsRefreshSignal, eventGroupByApp)
                    1 -> appsPage(appsQuery, innerPadding, appsRefreshSignal)
                    else -> settingsPage(
                        innerPadding,
                        onShowAboutDialog
                    )
                }
            }
        }

        aboutDialogContent?.let { content ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { aboutDialogContent = null },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { aboutDialogContent = null }) {
                        androidx.compose.material3.Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboardManager.text = content
                        aboutDialogContent = null
                    }) {
                        androidx.compose.material3.Text("Copy")
                    }
                },
                title = { androidx.compose.material3.Text(stringResource(R.string.action_about)) },
                text = {
                    androidx.compose.material3.Text(content)
                }
            )
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
        eventsPage = { _, _, _, _ ->
            Column {
                EventListPreview()
            }
        },
        appsPage = { _, _, _ -> },
        settingsPage = { _, _ -> }
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
        eventsPage = { _, _, _, _ -> },
        appsPage = { _, _, _ ->
            Column {
                ApplicationListPreview()
            }
        },
        settingsPage = { _, _ -> }
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
        eventsPage = { _, _, _, _ -> },
        appsPage = { _, _, _ -> },
        settingsPage = { padding, onAbout -> 
            Settings(padding, onShowAboutDialog = onAbout) 
        }
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
        eventsPage = { _, _, _, _ -> EventDetailsDialogPreview() },
        appsPage = { _, _, _ -> },
        settingsPage = { _, _ -> }
    )
}
