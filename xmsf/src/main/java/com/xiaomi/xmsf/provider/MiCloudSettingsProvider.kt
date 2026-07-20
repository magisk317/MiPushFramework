package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
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
            "getServiceToken" -> {
                if (!isSensitiveCallerAllowed() || !isValidServiceTokenRequest(arg, extras)) {
                    Bundle().apply { putString("error", "access_denied") }
                } else {
                    StockSurfaceSupport.serviceTokenBundle(context, extras?.getString("sid").orEmpty())
                }
            }
            else -> Bundle().apply { putString("error", "unknown_method:$method") }
        }
    }

    override fun onCreate(): Boolean {
        migrateLegacySettings()
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.authority != AUTHORITY) return null
        enforceSensitiveCallerAllowed()
        require(selection == null && selectionArgs == null && sortOrder == null) {
            "Please use MiCloud SDK APIs to query settings."
        }
        migrateLegacySettings()

        val key = projection?.singleOrNull()
            ?: throw IllegalArgumentException("Only one key allowed. Error query number: ${projection?.size ?: 0}")
        require(key.isNotBlank()) { "Key cannot be null or blank." }

        val value = settings().getString(key, null)
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

        migrateLegacySettings()
        storeSetting(key, values.getAsString(MICLOUD_SETTINGS_VALUE))
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Unsupported delete")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Unsupported update")
    }

    private fun settings() = requireNotNull(context).getSharedPreferences(SETTINGS_FILE_NAME, 0)

    private fun storeSetting(key: String, value: String?) {
        settings().edit().putString(key, value).apply()
    }

    private fun allowInsert(key: String): Boolean {
        return key in allowedInsertedKeys || allowedInsertedPrefixes.any(key::startsWith)
    }

    private fun migrateLegacySettings() {
        val providerContext = context ?: return
        val migrationState = providerContext.getSharedPreferences(MIGRATION_STATE_FILE_NAME, 0)
        if (migrationState.getBoolean(MIGRATION_SUCCESS_KEY, false)) return

        val fromLegacyAuthority = runCatching {
            providerContext.contentResolver.query(LEGACY_GET_ALL_URI, null, null, null, null)?.use { cursor ->
                buildMap<String, String?> {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(0), cursor.getString(1))
                    }
                }
            }
        }.getOrNull()
        val legacyLocal = providerContext.getSharedPreferences(LEGACY_SETTINGS_FILE_NAME, 0).all
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()
        val migrated = fromLegacyAuthority ?: legacyLocal.takeIf { it.isNotEmpty() } ?: return

        val editor = settings().edit()
        migrated.forEach { (key, value) -> editor.putString(key, value) }
        if (editor.commit()) {
            migrationState.edit().putBoolean(MIGRATION_SUCCESS_KEY, true).commit()
        }
    }

    private fun isSensitiveCallerAllowed(): Boolean {
        val providerContext = context ?: return false
        val callingUid = Binder.getCallingUid()
        val hasCloudManagerPermission = providerContext.checkCallingPermission(CLOUD_MANAGER_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        return isSensitiveCallerAllowed(callingUid, providerContext.applicationInfo?.uid, hasCloudManagerPermission)
    }

    private fun enforceSensitiveCallerAllowed() {
        if (!isSensitiveCallerAllowed()) {
            throw SecurityException("MiCloud settings require CLOUD_MANAGER or a trusted caller")
        }
    }

    private fun isValidServiceTokenRequest(arg: String?, extras: Bundle?): Boolean {
        if (!arg.isNullOrEmpty() || extras == null) return false
        return runCatching {
            extras.keySet() == setOf("sid") &&
                extras.getString("sid")?.let { sid ->
                    sid.isNotBlank() && sid.toByteArray(Charsets.UTF_8).size <= MAX_SID_BYTES
                } == true
        }.getOrDefault(false)
    }

    companion object {
        const val AUTHORITY = "com.xiaomi.xmsf.provider.MiCloudSettingsProvider"
        private const val CLOUD_MANAGER_PERMISSION = "com.xiaomi.permission.CLOUD_MANAGER"
        private const val MAX_SID_BYTES = 128
        internal const val SETTINGS_FILE_NAME = "micloud_sdk_settings"
        internal const val LEGACY_SETTINGS_FILE_NAME = "micloud_settings"
        internal const val MIGRATION_STATE_FILE_NAME = "micloud_migrate_state"
        internal const val MIGRATION_SUCCESS_KEY = "migrate_success"
        private val LEGACY_GET_ALL_URI = Uri.parse(
            "content://com.xiaomi.micloudsdk.provider.MiCloudSettingsProvider/get_all",
        )
        const val MICLOUD_SETTINGS_KEY = "micloud_settings_key"
        const val MICLOUD_SETTINGS_VALUE = "micloud_settings_value"
        const val MICLOUD_NETWORK_AVAILABILITY_KEY = "micloud_network_availability"
        private const val MICLOUD_HOSTS_V2 = "micloud_hosts_v2"
        private const val MICLOUD_ACCOUNTNAME_V2 = "micloud_accountname_v2"
        private const val MICLOUD_UPDATEHOSTS_THIRD_PARTY = "micloud_updatehosts_third_party"
        private const val OPEN_PDC_HOST_KEY = "com.xiaomi.opensdk.pdc.host"
        private const val PREFIX_SYNC_FOR_SIM = "sync_for_sim_"
        private const val PREFIX_SETTING_LAST_TIME_ALERT_AUTHORITY = "setting_last_time_alert_"

        internal fun isSensitiveCallerAllowed(callingUid: Int, appUid: Int?, hasCloudManagerPermission: Boolean): Boolean {
            return callingUid == Process.ROOT_UID ||
                callingUid == Process.SYSTEM_UID ||
                callingUid == appUid ||
                hasCloudManagerPermission
        }
    }
}
