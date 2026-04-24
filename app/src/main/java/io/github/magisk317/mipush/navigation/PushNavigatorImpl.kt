package io.github.magisk317.mipush.navigation

import io.github.magisk317.mipush.runtime.core.config.ConfigNavigationHelper
import io.github.magisk317.mipush.runtime.core.navigation.PushNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNavigatorImpl @Inject constructor(
    private val configNavigationHelper: ConfigNavigationHelper
) : PushNavigator {
    override fun openConfigPreview(packageName: String) {
        // Since the interface method might be called from non-suspend context in EventRepository sometimes,
        // or we just want to fire and forget the navigation.
        CoroutineScope(Dispatchers.Main).launch {
            configNavigationHelper.openForPackage(packageName)
        }
    }
}
