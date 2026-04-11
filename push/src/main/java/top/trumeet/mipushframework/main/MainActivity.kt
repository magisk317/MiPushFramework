package top.trumeet.mipushframework.main

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : io.github.magisk317.mipush.feature.main.MainActivity() {
    companion object {
        const val EXTRA_START_TAB = io.github.magisk317.mipush.feature.main.MainActivity.EXTRA_START_TAB
        const val START_TAB_SETTINGS = io.github.magisk317.mipush.feature.main.MainActivity.START_TAB_SETTINGS
        const val EXTRA_START_ROUTE = io.github.magisk317.mipush.feature.main.MainActivity.EXTRA_START_ROUTE
    }
}
