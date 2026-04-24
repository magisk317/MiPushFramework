@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main

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
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import io.github.magisk317.mipush.feature.navigation.NavigationSuiteScaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.magisk317.mipush.runtime.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.theme.UiKitStyle
import androidx.navigation.NavHostController
import io.github.magisk317.mipush.feature.navigation.*

import io.github.magisk317.mipush.feature.main.MainActivityUtils
import io.github.magisk317.mipush.feature.ui.component.SearchBar
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.main.subpage.ApplicationList
import io.github.magisk317.mipush.feature.main.subpage.ConfigurationEditor
import io.github.magisk317.mipush.feature.main.subpage.Configurations
import io.github.magisk317.mipush.feature.main.subpage.ApplicationListPreview
import io.github.magisk317.mipush.feature.main.subpage.EventDetailsDialogPreview
import io.github.magisk317.mipush.feature.main.subpage.EventList
import io.github.magisk317.mipush.feature.main.subpage.EventListPreview
import io.github.magisk317.mipush.feature.main.subpage.Settings
import io.github.magisk317.mipush.feature.main.subpage.SettingsPagePreview
import io.github.magisk317.mipush.feature.ui.theme.*
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import io.github.magisk317.mipush.runtime.app.ConfigCenter
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.platform.support.LegacyComponentNames
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.hypot

private val mainActivityUtils = MainActivityUtils()
private var placeholder by mutableStateOf("Search...")

@AndroidEntryPoint
open class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_TAB = LegacyComponentNames.EXTRA_START_TAB
        const val START_TAB_SETTINGS = LegacyComponentNames.START_TAB_SETTINGS
        const val EXTRA_START_ROUTE = LegacyComponentNames.EXTRA_START_ROUTE
    }

    @Inject lateinit var configCenter: ConfigCenter
    @Inject lateinit var eventRepository: EventRepository

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainActivityUtils.initOnCreate(applicationContext, configCenter) { placeholder = it.toString() }
        val explicitRoute = intent?.getStringExtra(EXTRA_START_ROUTE)
        val startDestination = when {
            explicitRoute?.startsWith(AppDestinations.Configs.ROUTE) == true ||
                explicitRoute?.startsWith(AppDestinations.ConfigsSearch.ROUTE) == true ||
                explicitRoute?.startsWith(AppDestinations.ConfigEditor.ROUTE) == true -> AppDestinations.Configs.ROUTE

            explicitRoute?.startsWith(AppDestinations.Settings.ROUTE) == true ||
                explicitRoute?.startsWith(AppDestinations.SettingsSection.ROUTE) == true ||
                intent?.getStringExtra(EXTRA_START_TAB) == START_TAB_SETTINGS -> AppDestinations.Settings.ROUTE

            else -> AppDestinations.Overview.ROUTE
        }
        setContent {
            val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()

            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var currentUiKitStyle by remember { mutableIntStateOf(themeState.uiKitStyle) }
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
                            currentUiKitStyle = themeState.uiKitStyle

                            revealAnim.snapTo(0f)
                            revealAnim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 600),
                            )

                            isAnimating = false
                            screenshotBitmap = null
                        } catch (_: Exception) {
                            currentThemeMode = themeState.mode
                            currentUiKitStyle = themeState.uiKitStyle
                        }
                    } else {
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    }
                } else if (themeState.uiKitStyle != currentUiKitStyle) {
                    currentUiKitStyle = themeState.uiKitStyle
                } else {
                    currentThemeMode = themeState.mode
                    currentUiKitStyle = themeState.uiKitStyle
                }
            }

            Theme(
                themeMode = ThemeMode.fromValue(currentThemeMode),
                uiKitStyle = currentUiKitStyle,
            ) {
                val hazeState = remember { HazeState() }
                val hazeStyle = rememberHazeStyle()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        startDestination = startDestination,
                        initialRouteOverride = explicitRoute,
                        hazeState = hazeState,
                        hazeStyle = hazeStyle,
                        eventRepository = eventRepository,
                    )

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
