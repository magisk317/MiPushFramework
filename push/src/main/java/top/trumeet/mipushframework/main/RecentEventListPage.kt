package top.trumeet.mipushframework.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
                Box(
                    Modifier
                        .navigationBarsPadding()
                        .fillMaxSize()
                ) {
                    EventList(query = "", packageName = packageName, contentPadding = PaddingValues(0.dp))
                }
            }
        }
    }
}
