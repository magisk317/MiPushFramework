package top.trumeet.mipushframework.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.main.subpage.EventList
import top.trumeet.ui.theme.Theme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecentEventListPage : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val packageName = intent.dataString!!
        setContent {
            Theme {
                Column(
                    Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    var query by rememberSaveable { mutableStateOf("") }
                    SearchBar(
                        placeholder = "搜索...",
                        query = query,
                        onValueChange = { query = it }
                    )
                    EventList(query, packageName, PaddingValues(0.dp))
                }
            }
        }
    }
}
