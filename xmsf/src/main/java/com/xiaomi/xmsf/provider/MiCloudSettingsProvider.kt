package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.xiaomi.xmsf.stock.StockSurfaceSupport

class MiCloudSettingsProvider : ContentProvider() {
    private val allowedInsertedKeys = setOf(
        MICLOUD_NETWORK_AVAILABILITY_KEY,
        MICLOUD_HOSTS_V2,
        MICLOUD_ACCOUNTNAME_V2,
        MICLOUD_UPDATEHOSTS_THIRD_PARTY,
        OPEN_PDC_HOST_KEY,
    )

    private val allowedInsertedPrefixes = listOf(
        PREFIX_SYNC_FOR_SIM,
        PREFIX_SETTING_LAST_TIME_ALERT_AUTHORITY,
    )

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return Bundle()
        return when (method) {
            "getAvailability" -> StockSurfaceSupport.accountAvailabilityBundle(context)
            "getServiceToken" -> StockSurfaceSupport.serviceTokenBundle(context, extras?.getString("sid").orEmpty())
            else -> Bundle().apply { putString("error", "unknown_method:$method") }
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.authority != AUTHORITY) return null
        if (uri.lastPathSegment == PATH_GET_ALL) return getAllSettings()

        val key = projection?.singleOrNull()
            ?: throw IllegalArgumentException("Only one key allowed. Error query number: ${projection?.size ?: 0}")
        require(key.isNotBlank()) { "Key cannot be null or blank." }

        val value = settings().getString(key, null)
            ?: readLegacySystemSetting(key)
                ?.also { storeSetting(key, it) }
        return MatrixCursor(arrayOf(key), 1).apply {
            addRow(arrayOf<Any?>(value))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uri.authority != AUTHORITY) return null
        val key = values?.getAsString(MICLOUD_SETTINGS_KEY)
        require(!key.isNullOrBlank()) { "Do not insert a null key." }
        require(allowInsert(key)) { "Key $key is not allowed to insert." }

        storeSetting(key, values.getAsString(MICLOUD_SETTINGS_VALUE))
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getAllSettings(): Cursor {
        val all = settings().all
        return MatrixCursor(arrayOf("key", "value"), all.size).apply {
            all.forEach { (key, value) ->
                addRow(arrayOf(key, value))
            }
        }
    }

    private fun settings() = requireNotNull(context).getSharedPreferences(SETTINGS_FILE_NAME, 0)

    private fun savedKeys() = requireNotNull(context).getSharedPreferences(SAVED_KEY_FILE_NAME, 0)

    private fun storeSetting(key: String, value: String?) {
        settings().edit().putString(key, value).apply()
        if (!savedKeys().contains(key)) {
            savedKeys().edit().putString(key, key).apply()
        }
    }

    private fun readLegacySystemSetting(key: String): String? {
        if (!needCopyFromSystem(key) || savedKeys().contains(key)) return null
        return Settings.System.getString(context?.contentResolver ?: return null, key)
    }

    private fun allowInsert(key: String): Boolean {
        return key in allowedInsertedKeys || allowedInsertedPrefixes.any(key::startsWith)
    }

    private fun needCopyFromSystem(key: String): Boolean = allowInsert(key)

    companion object {
        const val AUTHORITY = "com.xiaomi.xmsf.provider.MiCloudSettingsProvider"
        private const val PATH_GET_ALL = "get_all"
        private const val SETTINGS_FILE_NAME = "micloud_settings"
        private const val SAVED_KEY_FILE_NAME = "saved_key_file"
        const val MICLOUD_SETTINGS_KEY = "micloud_settings_key"
        const val MICLOUD_SETTINGS_VALUE = "micloud_settings_value"
        const val MICLOUD_NETWORK_AVAILABILITY_KEY = "micloud_network_availability"
        private const val MICLOUD_HOSTS_V2 = "micloud_hosts_v2"
        private const val MICLOUD_ACCOUNTNAME_V2 = "micloud_accountname_v2"
        private const val MICLOUD_UPDATEHOSTS_THIRD_PARTY = "micloud_updatehosts_third_party"
        private const val OPEN_PDC_HOST_KEY = "com.xiaomi.opensdk.pdc.host"
        private const val PREFIX_SYNC_FOR_SIM = "sync_for_sim_"
        private const val PREFIX_SETTING_LAST_TIME_ALERT_AUTHORITY = "setting_last_time_alert_"
    }
}
