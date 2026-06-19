package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.feature.main.subpage.ApplicationPageOperation
import io.github.magisk317.mipush.feature.main.subpage.ApplicationStats
import io.github.magisk317.mipush.feature.main.subpage.toApplicationStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel constructor(
    private val applicationGateway: ManagerApplicationGateway,
    private val context: Context,
) : ViewModel() {

    private val _stats = MutableStateFlow(ApplicationStats())
    val stats: StateFlow<ApplicationStats> = _stats.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val operation = ApplicationPageOperation(applicationGateway)
                val applications = operation.getMiPushApplicationsThatQueryMatched(query = "", filterMode = 0)
                operation.updateRegisteredApplicationDb(context, applications.res)
                applications.toApplicationStats()
            }
            _stats.value = result
        }
    }
}
