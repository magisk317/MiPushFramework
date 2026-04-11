package io.github.magisk317.mipush.feature.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.feature.ui.component.ExpressiveSectionCard
import io.github.magisk317.mipush.feature.ui.component.FeatureEntryCard
import io.github.magisk317.mipush.feature.ui.component.LoadingIndicatorTokens
import io.github.magisk317.mipush.feature.ui.component.MarkdownView
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderPanel
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderScaffold
import io.github.magisk317.mipush.feature.ui.component.PolygonMorphLoadingIndicator
import io.github.magisk317.mipush.feature.ui.component.SectionColumn
import io.github.magisk317.mipush.feature.ui.component.SessionLoadingRegistry
import io.github.magisk317.mipush.feature.ui.component.rememberMinDurationLoading
import io.github.magisk317.mipush.feature.ui.theme.Theme
import java.io.InputStreamReader

open class HelpPage : ComponentActivity() {
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

@Preview(showSystemUi = true, showBackground = true)
@Composable
fun HelpPage(modifier: Modifier = Modifier) {
    HelpScreen(modifier = modifier)
}

@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HelpRoute.List.route,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(HelpRoute.List.route) {
            HelpHubRoute(
                navController = navController,
                hazeState = hazeState,
                hazeStyle = hazeStyle,
            )
        }
        composable(HelpRoute.Article.route) { backStackEntry ->
            val titleRes = backStackEntry.arguments?.getString(HelpRoute.Article.ARG_TITLE)?.toIntOrNull()
            val markdownResId = backStackEntry.arguments?.getString(HelpRoute.Article.ARG_MARKDOWN)?.toIntOrNull()
            HelpArticleRoute(
                titleRes = titleRes,
                markdownResId = markdownResId,
                onBack = { navController.popBackStack() },
                hazeState = hazeState,
                hazeStyle = hazeStyle,
            )
        }
    }
}

private sealed class HelpRoute(val route: String) {
    data object List : HelpRoute("list")
    data object Article : HelpRoute("article/{titleRes}/{markdownResId}") {
        const val ARG_TITLE = "titleRes"
        const val ARG_MARKDOWN = "markdownResId"

        fun create(titleRes: Int, markdownResId: Int): String {
            return "article/$titleRes/$markdownResId"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpHubRoute(
    navController: NavHostController,
    hazeState: HazeState?,
    hazeStyle: HazeStyle?,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    OverlayHeaderScaffold(
        fallbackTopPadding = topInset + 116.dp,
        bottomPadding = bottomInset + 24.dp,
        overlayModifier = Modifier
            .statusBarsPadding()
            .then(
                if (hazeState != null && hazeStyle != null) {
                    Modifier.hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    Modifier
                }
            ),
        content = { padding ->
            HelpHubContent(
                navController = navController,
                contentPadding = padding,
            )
        },
        overlay = {
            OverlayHeaderPanel(
                title = stringResource(R.string.helplib_title),
                subtitle = stringResource(R.string.help_page_overview_summary),
            ) {
                Text(
                    text = stringResource(R.string.help_page_workspace_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun HelpHubContent(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    SectionColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 12.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        FAQGroup(navController = navController)
        ContactUsGroup()
    }
}

@Composable
private fun HelpArticleRoute(
    titleRes: Int?,
    markdownResId: Int?,
    onBack: () -> Unit,
    hazeState: HazeState?,
    hazeStyle: HazeStyle?,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    OverlayHeaderScaffold(
        fallbackTopPadding = topInset + 108.dp,
        bottomPadding = bottomInset + 24.dp,
        overlayModifier = Modifier
            .statusBarsPadding()
            .then(
                if (hazeState != null && hazeStyle != null) {
                    Modifier.hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    Modifier
                }
            ),
        content = { padding ->
            SectionColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                Markdown(
                    markdownResId = markdownResId,
                    title = titleRes?.let { stringResource(it) } ?: stringResource(R.string.help_page_article_title),
                )
            }
        },
        overlay = {
            OverlayHeaderPanel(
                title = titleRes?.let { stringResource(it) } ?: stringResource(R.string.help_page_article_title),
                subtitle = stringResource(R.string.help_page_article_summary),
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_black_24dp),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.help_page_article_workspace_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun Markdown(
    markdownResId: Int?,
    title: String,
) {
    val context = LocalContext.current
    if (markdownResId == null) return

    ExpressiveSectionCard(
        title = title,
        summary = stringResource(R.string.help_page_article_card_summary),
    ) {
        MarkdownView(
            readRawFile(context, markdownResId),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FAQGroup(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val sessionKey = "help_articles_initial"
    var actualLoading by remember { mutableStateOf(SessionLoadingRegistry.shouldShowInitial(sessionKey)) }
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    val showLoading = rememberMinDurationLoading(actualLoading = actualLoading)

    LaunchedEffect(context) {
        actualLoading = SessionLoadingRegistry.shouldShowInitial(sessionKey)
        val loaded = withContext(Dispatchers.IO) { getArticles(context) }
        articles = loaded
        actualLoading = false
        SessionLoadingRegistry.markShown(sessionKey)
    }

    ExpressiveSectionCard(
        title = stringResource(R.string.helplib_title_faq),
        summary = stringResource(R.string.help_page_faq_summary),
    ) {
        if (showLoading && articles.isEmpty()) {
            PolygonMorphLoadingIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )
        }

        articles.forEach { article ->
            HelpEntryCard(
                title = stringResource(article.titleRes),
                summary = stringResource(R.string.help_page_article_card_summary),
            ) {
                navController.navigate(
                    HelpRoute.Article.create(
                        titleRes = article.titleRes,
                        markdownResId = article.markdownRes,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ContactUsGroup() {
    val context = LocalContext.current

    ExpressiveSectionCard(
        title = stringResource(R.string.helplib_title_contact),
        summary = stringResource(R.string.help_page_contact_summary),
    ) {
        HelpEntryCard(
            title = stringResource(R.string.helplib_action_qq_group),
            summary = stringResource(R.string.help_page_contact_qq_summary),
        ) {
            openUrl(context, "https://qm.qq.com/q/PaFGVEb6so")
        }
        HelpEntryCard(
            title = stringResource(R.string.helplib_action_telegram_group),
            summary = stringResource(R.string.help_page_contact_telegram_summary),
        ) {
            openUrl(context, "https://t.me/+Gf5x3Lqw1tdiZDNl")
        }
        HelpEntryCard(
            title = stringResource(R.string.helplib_action_issue),
            summary = stringResource(R.string.help_page_contact_issue_summary),
        ) {
            openUrl(context, "https://github.com/magisk317/MiPushFramework/issues")
        }
    }
}

@Composable
private fun HelpEntryCard(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    FeatureEntryCard(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    )
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
