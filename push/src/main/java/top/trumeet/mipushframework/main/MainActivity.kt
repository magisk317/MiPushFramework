@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.navigation.compose.rememberNavController

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import top.trumeet.mipushframework.navigation.NavigationSuiteScaffold
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.navigation.NavHostController
import top.trumeet.mipushframework.navigation.*

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
import top.trumeet.mipushframework.main.HelpScreen
import top.trumeet.mipushframework.main.subpage.SettingsPagePreview
import top.trumeet.ui.theme.*
import com.magisk317.main.viewmodel.SettingsViewModel
import javax.inject.Inject
import com.xiaomi.xmsf.utils.ConfigCenter
import top.trumeet.mipushframework.data.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import com.magisk317.data.DataStoreManager
import kotlinx.coroutines.launch
import androidx.activity.viewModels
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

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainActivityUtils.initOnCreate(applicationContext, configCenter) { placeholder = it.toString() }
        val startDestination = if (intent?.getStringExtra(EXTRA_START_TAB) == START_TAB_SETTINGS) {
            AppDestinations.Settings.ROUTE
        } else {
            AppDestinations.AppsList.ROUTE
        }
        setContent {
            val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()

            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }

            val view = LocalView.current

            LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    if (view.width > 0 && view.height > 0) {
                        try {
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
                        } catch (_: Exception) {
                            currentThemeMode = themeState.mode
                        }
                    } else {
                        currentThemeMode = themeState.mode
                    }
                } else {
                    currentThemeMode = themeState.mode
                }
            }

            Theme(themeMode = ThemeMode.fromValue(currentThemeMode)) {
                val hazeState = remember { HazeState() }
                val hazeStyle = rememberHazeStyle(blurRadius = 25.dp, tintAlpha = 0.2f)
                
                Box(modifier = Modifier.fillMaxSize()) {
                    // Navigation host
                    val navController = rememberNavController()
                    val navigator = NavControllerNavigationCoordinator(navController)
                    var aboutDialogContent by remember { mutableStateOf<String?>(null) }
                    var settingsSectionTitle by rememberSaveable { mutableStateOf<String?>(null) }
                    var settingsBackSignal by rememberSaveable { mutableStateOf(0) }

                    NavigationSuiteScaffold(
                        bottomBar = {
                            BottomNavigationBarV2(
                                navController = navController,
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onTabDoubleTap = { index ->
                                    when (index) {
                                        0 -> eventRepository.startManagePermissions("", true)
                                        else -> {}
                                    }
                                }
                            )
                        },
                        navigationRail = {
                            // for medium/large screens we also show the same tabs vertically
                            BottomNavigationBarV2(
                                navController = navController,
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                onTabDoubleTap = { /* same */ }
                            )
                        },
                        drawerContent = {
                            // future: additional items like 'About' or 'Settings'
                            Text(stringResource(R.string.action_help))
                        },
                        content = { innerPadding ->
                            AppNavHostContent(
                                navController = navController,
                                startDestination = startDestination,
                                contentPadding = innerPadding,
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                                eventsPage = { q, padding, refreshSignal, groupByApp, hState, hStyle ->
                                    EventList(query = q, contentPadding = padding, refreshSignal = refreshSignal, groupByApp = groupByApp, hazeState = hState, hazeStyle = hStyle)
                                },
                                appsPage = { q, padding, refreshSignal, filterMode, hState, hStyle ->
                                    ApplicationList(
                                        q,
                                        contentPadding = padding,
                                        refreshSignal = refreshSignal,
                                        filterMode = filterMode,
                                        onAppClick = { pkg -> eventRepository.startManagePermissions(pkg, true) },
                                        hazeState = hState,
                                        hazeStyle = hStyle
                                    )
                                },
                                settingsPage = { padding, onAbout, onSectionChanged, backSignal, hState, hStyle ->
                                    Settings(
                                        padding,
                                        onShowAboutDialog = onAbout,
                                        onSectionChanged = onSectionChanged,
                                        sectionBackSignal = backSignal,
                                        hazeState = hState,
                                        hazeStyle = hStyle
                                    )
                                },
                                helpPage = { padding, hState, hStyle ->
                                    // simple wrapper to apply padding
                                    HelpScreen(Modifier.padding(padding), hazeState = hState, hazeStyle = hStyle)
                                },
                                onAbout = { content -> aboutDialogContent = content },
                                onSectionChanged = { title -> settingsSectionTitle = title }
                            )
                        }
                    )

                    // Keep system bar areas visually attached to page chrome.
                    SystemBarsScrim(hazeState = hazeState, hazeStyle = hazeStyle)

                    // about dialog
                    if (aboutDialogContent != null) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { aboutDialogContent = null },
                            confirmButton = {
                                DialogActionRow(
                                    actions = listOf(
                                        DialogAction(
                                            label = stringResource(android.R.string.ok),
                                            onClick = { aboutDialogContent = null }
                                        )
                                    )
                                )
                            },
                            text = { Text(aboutDialogContent!!) }
                        )
                    }

                    if (isAnimating && screenshotBitmap != null) {
                        val view = LocalView.current
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
