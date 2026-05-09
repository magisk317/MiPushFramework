package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ACCESSIBILITY_HEARTBEAT
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_AUTHORITY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_ENABLED
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DEDICATED_SERVICE
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class KeepAlivePreferenceProvider : ContentProvider() {
    private val keys = listOf(
        KEEPALIVE_PREF_OOM_ADJ,
        KEEPALIVE_PREF_ANTI_KILL,
        KEEPALIVE_PREF_STANDBY_BYPASS,
        KEEPALIVE_PREF_DOZE_BYPASS,
        KEEPALIVE_PREF_ACCESSIBILITY_HEARTBEAT,
        KEEPALIVE_PREF_DEDICATED_SERVICE,
    )

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (uri.authority != KEEPALIVE_PREF_AUTHORITY || uri.lastPathSegment != KEEPALIVE_PREF_PATH_FLAGS) {
            return null
        }
        val appContext = context?.applicationContext ?: return null
        val repository = PreferenceRepository(appContext.dataStore)
        val requestedKeys = selectionArgs?.filter { it in keys }?.takeIf { it.isNotEmpty() } ?: keys

        return MatrixCursor(arrayOf(KEEPALIVE_PREF_COLUMN_KEY, KEEPALIVE_PREF_COLUMN_ENABLED)).apply {
            val flags = runBlocking {
                mapOf(
                    KEEPALIVE_PREF_OOM_ADJ to repository.keepAliveOomAdj.first(),
                    KEEPALIVE_PREF_ANTI_KILL to repository.keepAliveAntiKill.first(),
                    KEEPALIVE_PREF_STANDBY_BYPASS to repository.keepAliveStandbyBypass.first(),
                    KEEPALIVE_PREF_DOZE_BYPASS to repository.keepAliveDozeBypass.first(),
                    KEEPALIVE_PREF_ACCESSIBILITY_HEARTBEAT to repository.keepAliveAccessibilityHeartbeat.first(),
                    KEEPALIVE_PREF_DEDICATED_SERVICE to repository.keepAliveDedicatedService.first(),
                )
            }
            requestedKeys.forEach { key ->
                addRow(arrayOf<Any>(key, if (flags[key] == true) 1 else 0))
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null
}
