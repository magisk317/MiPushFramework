package top.trumeet.mipushframework.main


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaomi.xmsf.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import top.trumeet.mipushframework.component.MarkdownView
import top.trumeet.mipushframework.component.LoadingIndicatorTokens
import top.trumeet.mipushframework.component.PolygonMorphLoadingIndicator
import top.trumeet.mipushframework.component.SessionLoadingRegistry
import top.trumeet.mipushframework.component.SettingsGroup
import top.trumeet.mipushframework.component.SettingsItem
import top.trumeet.mipushframework.component.rememberMinDurationLoading
import top.trumeet.ui.theme.Theme
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HelpPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                HelpScreen()
            }
        }
    }
}

@Preview(
    showSystemUi = true,
    showBackground = true,
)
@Composable
fun HelpPage(modifier: Modifier = Modifier) {
    HelpScreen()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HelpScreen(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        stringResource(R.string.app_name) + " " + stringResource(R.string.helplib_title)
                    )
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = (Modifier
                    .statusBarsPadding()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                ).let { base ->
                    if (hazeState != null && hazeStyle != null) {
                        base.hazeEffect(hazeState, hazeStyle) {
                            forceInvalidateOnPreDraw = true
                        }
                    } else {
                        base
                    }
                }
            )
        }
    ) { innerPadding ->
        HelpList(Modifier.padding(innerPadding))
    }
}

@Composable
fun HelpList(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "list",
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        composable("list") { HelpList(navController) }
        composable("markdown/{markdownResId}") { backStackEntry ->
            val markdownResId = backStackEntry.arguments?.getString("markdownResId")?.toInt()
            Markdown(markdownResId)
        }
    }
}

@Composable
fun HelpList(navController: NavHostController) {
    Column {
        FAQGroup(navController)
        ContactUsGroup()
    }
}

@Composable
private fun Markdown(markdownResId: Int?) {
    MarkdownView(
        readRawFile(LocalContext.current, markdownResId!!), modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun FAQGroup(
    navController: NavHostController
) {
    val context = LocalContext.current
    val sessionKey = "help_articles_initial"
    var actualLoading by remember { mutableStateOf(SessionLoadingRegistry.shouldShowInitial(sessionKey)) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    val showLoading = rememberMinDurationLoading(actualLoading = actualLoading)

    LaunchedEffect(context) {
        actualLoading = SessionLoadingRegistry.shouldShowInitial(sessionKey)
        val loaded = withContext(Dispatchers.IO) {
            getArticles(context)
        }
        articles = loaded
        actualLoading = false
        SessionLoadingRegistry.markShown(sessionKey)
    }

    SettingsGroup(title = stringResource(R.string.helplib_title_faq)) {
        if (showLoading && articles.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                PolygonMorphLoadingIndicator(modifier = Modifier.size(LoadingIndicatorTokens.ContainedSize))
            }
        }
        for (article in articles) {
            SettingsItem(
                title = stringResource(article.titleRes)
            ) {
                navController.navigate("markdown/${article.markdownRes}")
            }
        }
    }
}

@Composable
private fun ContactUsGroup() {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.helplib_title_contact)) {
        SettingsItem(title = stringResource(R.string.helplib_action_qq_group)) {
            openUrl(context, "https://qm.qq.com/q/PaFGVEb6so")
        }
        SettingsItem(title = stringResource(R.string.helplib_action_telegram_group)) {
            openUrl(context, "https://t.me/+Gf5x3Lqw1tdiZDNl")
        }
        SettingsItem(title = stringResource(R.string.helplib_action_issue)) {
            openUrl(context, "https://github.com/magisk317/MiPushFramework/issues")
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

class Article(val titleRes: Int, val markdownRes: Int)

private fun getArticles(context: Context): List<Article> {
    val articlesArray = context.resources.getStringArray(R.array.help_articles)
    val packageName = context.packageName

    val articles = mutableListOf<Article>()
    for (str in articlesArray) {
        val info = str.split("|")
        if (info.size != 2) continue

        val titleRes = context.resources.getIdentifier(info[0], "string", packageName)
        val markdownRes = context.resources.getIdentifier(info[1], "raw", packageName)

        if (titleRes != 0 && markdownRes != 0) {
            articles.add(Article(titleRes, markdownRes))
        }
    }
    return articles
}

private fun readRawFile(context: Context, fileName: Int): String {
    val inputStream = context.resources.openRawResource(fileName)
    val reader = InputStreamReader(inputStream)
    return reader.readText()
}
