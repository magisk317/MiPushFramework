@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import top.trumeet.mipushframework.MainActivityUtils
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.component.DialogAction
import top.trumeet.mipushframework.component.DialogActionRow
import top.trumeet.mipushframework.main.subpage.ApplicationList
import top.trumeet.mipushframework.main.subpage.ApplicationListPreview
import top.trumeet.mipushframework.main.subpage.EventDetailsDialogPreview
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.mipushframework.main.subpage.EventListPreview
import top.trumeet.mipushframework.main.subpage.Settings
import top.trumeet.mipushframework.main.subpage.SettingsPagePreview
import top.trumeet.ui.theme.*
import com.magisk317.main.viewmodel.SettingsViewModel
import javax.inject.Inject
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.mipushframework.data.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import com.magisk317.data.DataStoreManager
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.hypot

private val mainActivityUtils = MainActivityUtils()
private var placeholder by mutableStateOf("Search...")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
        const val START_TAB_SETTINGS = "settings"
    }

    @Inject lateinit var configCenter: ConfigCenter
    @Inject lateinit var eventRepository: EventRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainActivityUtils.initOnCreate(applicationContext, configCenter) { placeholder = it.toString() }
        val startDestination = if (intent?.getStringExtra(EXTRA_START_TAB) == START_TAB_SETTINGS) {
            Screen.Settings.route.toString()
        } else {
            Screen.Apps.route.toString()
        }
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
            val view = LocalView.current

            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }

            androidx.compose.runtime.LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    try {
                        if (view.width > 0 && view.height > 0) {
                            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            view.draw(canvas)
                            screenshotBitmap = bitmap

                            val centerX = if (themeState.centerX >= 0f) themeState.centerX else view.width / 2f
                            val centerY = if (themeState.centerY >= 0f) themeState.centerY else view.height / 2f
                            animationCenter = Offset(centerX, centerY)

                            isAnimating = true
                            currentThemeMode = themeState.mode

                            revealAnim.snapTo(0f)
                            revealAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 600),
                            )

                            isAnimating = false
                            screenshotBitmap = null
                        } else {
                            currentThemeMode = themeState.mode
                        }
                    } catch (_: Exception) {
                        currentThemeMode = themeState.mode
                    }
                } else {
                    currentThemeMode = themeState.mode
                }
            }

            Theme(themeMode = ThemeMode.fromValue(currentThemeMode)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Main(
                        startDestination,
                        configCenter = configCenter,
                        eventsPage = { query, padding, refreshSignal, groupByApp ->
                            EventList(
                                query,
                                contentPadding = padding,
                                refreshSignal = refreshSignal,
                                groupByApp = groupByApp
                            )
                        },
                        appsPage = { query, padding, refreshSignal, filterMode ->
                            ApplicationList(query, contentPadding = padding, refreshSignal = refreshSignal,
                                filterMode = filterMode,
                                onAppClick = { pkg -> eventRepository.startManagePermissions(pkg, true) })
                        },
                        settingsPage = { padding, onAbout, onSectionChanged, backSignal ->
                            Settings(
                                padding,
                                onShowAboutDialog = onAbout,
                                onSectionChanged = onSectionChanged,
                                sectionBackSignal = backSignal
                            )
                        }
                    )

                    if (isAnimating && screenshotBitmap != null) {
                        val oldImage = screenshotBitmap!!.asImageBitmap()
                        val maxRadius = hypot(view.width.toFloat(), view.height.toFloat())
                        val radius = maxRadius * revealAnim.value

                        androidx.compose.foundation.Image(
                            bitmap = oldImage,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawCircle(
                                        color = Color.Transparent,
                                        radius = radius,
                                        center = animationCenter,
                                        blendMode = BlendMode.Clear,
                                    )
                                },
                        )
                    }
                }
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
    configCenter: ConfigCenter,
    eventsPage: @Composable (String, PaddingValues, Int, Boolean) -> Unit,
    appsPage: @Composable (String, PaddingValues, Int, Int) -> Unit,
    settingsPage: @Composable (PaddingValues, (String) -> Unit, (String?) -> Unit, Int) -> Unit
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
    var showAppFilterModeMenu by remember { mutableStateOf(false) }
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember { configCenter }
    val hazeBlurRadius by DataStoreManager.hazeBlurRadius.collectAsStateWithLifecycle(initialValue = 25)
    val hazeTintAlpha by DataStoreManager.hazeTintAlpha.collectAsStateWithLifecycle(initialValue = 0.2f)
    val previewBlurRadius by DataStoreManager.previewHazeBlurRadius.collectAsStateWithLifecycle(initialValue = null)
    val previewTintAlpha by DataStoreManager.previewHazeTintAlpha.collectAsStateWithLifecycle(initialValue = null)

    val actualBlurRadius = previewBlurRadius ?: hazeBlurRadius
    val actualTintAlpha = previewTintAlpha ?: hazeTintAlpha

    val eventGroupByApp by DataStoreManager.eventGroupByApp.collectAsStateWithLifecycle(initialValue = false)
    val appFilterMode by DataStoreManager.appFilterMode.collectAsStateWithLifecycle(initialValue = 0)
    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
    var settingsSectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsBackSignal by rememberSaveable { mutableStateOf(0) }
    val onShowAboutDialog: (String) -> Unit = { content -> aboutDialogContent = content }

    val hazeState = remember { HazeState() }
    val hazeStyle = rememberHazeStyle(blurRadius = actualBlurRadius.dp, tintAlpha = actualTintAlpha)

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
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = !eventGroupByApp,
                                            onClick = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("按应用分组") },
                                    onClick = {
                                        showEventDisplayModeMenu = false
                                        scope.launch { DataStoreManager.setEventGroupByApp(true) }
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = eventGroupByApp,
                                            onClick = null
                                        )
                                    }
                                )
                            }
                        }
                    }

                    1 -> Row(
                        modifier = topBarModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchBar(
                            placeholder,
                            appsQuery,
                            modifier = Modifier.weight(1f)
                        ) { appsQuery = it }
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            IconButton(
                                onClick = { showAppFilterModeMenu = true }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_settings_black_24dp),
                                    contentDescription = "App filter mode",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showAppFilterModeMenu,
                                onDismissRequest = { showAppFilterModeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("全部（默认）") },
                                    onClick = {
                                        showAppFilterModeMenu = false
                                        scope.launch { DataStoreManager.setAppFilterMode(0) }
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = appFilterMode == 0,
                                            onClick = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("已注册") },
                                    onClick = {
                                        showAppFilterModeMenu = false
                                        scope.launch { DataStoreManager.setAppFilterMode(1) }
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = appFilterMode == 1,
                                            onClick = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("未注册") },
                                    onClick = {
                                        showAppFilterModeMenu = false
                                        scope.launch { DataStoreManager.setAppFilterMode(2) }
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = appFilterMode == 2,
                                            onClick = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text("其他") },
                                    onClick = {
                                        showAppFilterModeMenu = false
                                        scope.launch { DataStoreManager.setAppFilterMode(3) }
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.RadioButton(
                                            selected = appFilterMode == 3,
                                            onClick = null
                                        )
                                    }
                                )
                            }
                        }
                    }

                    else -> CenterAlignedTopAppBar(
                        title = {
                            androidx.compose.material3.Text(
                                settingsSectionTitle ?: stringResource(Screen.Settings.route)
                            )
                        },
                        navigationIcon = {
                            if (settingsSectionTitle != null) {
                                IconButton(onClick = { settingsBackSignal++ }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(android.R.string.cancel)
                                    )
                                }
                            }
                        },
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
                        when (index) {
                            0 -> eventsRefreshSignal++
                            1 -> appsRefreshSignal++
                        }
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
                    1 -> appsPage(appsQuery, innerPadding, appsRefreshSignal, appFilterMode)
                    else -> settingsPage(
                        innerPadding,
                        onShowAboutDialog,
                        { title -> settingsSectionTitle = title },
                        settingsBackSignal
                    )
                }
            }
        }

        aboutDialogContent?.let { content ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { aboutDialogContent = null },
                confirmButton = {
                    DialogActionRow(
                        actions = listOf(
                            DialogAction(
                                label = stringResource(android.R.string.copy),
                                onClick = {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboardManager.text = content
                                    aboutDialogContent = null
                                }
                            ),
                            DialogAction(
                                label = stringResource(android.R.string.ok),
                                onClick = { aboutDialogContent = null }
                            )
                        )
                    )
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
        configCenter = ConfigCenter(),
        eventsPage = { _, _, _, _ ->
            Column {
                EventListPreview()
            }
        },
        appsPage = { _, _, _, _ -> },
        settingsPage = { _, _, _, _ -> }
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
        configCenter = ConfigCenter(),
        eventsPage = { _, _, _, _ -> },
        appsPage = { _, _, _, _ ->
            Column {
                ApplicationListPreview()
            }
        },
        settingsPage = { _, _, _, _ -> }
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
        configCenter = ConfigCenter(),
        eventsPage = { _, _, _, _ -> },
        appsPage = { _, _, _, _ -> },
        settingsPage = { padding, onAbout, onSectionChanged, backSignal ->
            Settings(
                padding,
                onShowAboutDialog = onAbout,
                onSectionChanged = onSectionChanged,
                sectionBackSignal = backSignal
            )
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
        configCenter = ConfigCenter(),
        eventsPage = { _, _, _, _ -> EventDetailsDialogPreview() },
        appsPage = { _, _, _, _ -> },
        settingsPage = { _, _, _, _ -> }
    )
}
