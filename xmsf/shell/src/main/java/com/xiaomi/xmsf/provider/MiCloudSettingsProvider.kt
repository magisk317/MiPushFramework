package com.xiaomi.xmsf.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process

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

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = queryWithAuthorization(
        projection = projection,
        selection = selection,
        selectionArgs = selectionArgs,
        sortOrder = sortOrder,
        callerAllowed = isSensitiveCallerAllowed(),
    )

    internal fun queryWithAuthorization(
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
        callerAllowed: Boolean,
    ): Cursor? {
        require(projection != null && selection == null && selectionArgs == null && sortOrder == null) {
            "Please use MiCloud SDK APIs to query settings."
        }
        require(projection.size == 1) {
            "Only one key allowed. Error query number: ${projection.size}"
        }
        val key = projection[0]
        require(key.isNotBlank()) { "Key cannot be null or blank." }

        // Stock 7.4.67-C MiCloudSettingsProvider gates on sb.a.c() before every operation. The old
        // port already attempted migration without hard-gating; preserve that deliberate fallback
        // because a fresh install has no legacy authority and would otherwise remain unusable.
        migrateLegacySettings()

        // Stock 7.4.67-C MiCloudSettingsProvider exposes a null-valued row to an unauthorized
        // caller. The old port threw SecurityException, breaking the cursor ABI and allowing callers
        // to distinguish denial from an unset value.
        val value = if (callerAllowed) settings()?.getString(key, null) else null
        return MatrixCursor(arrayOf(key), 1).apply {
            addRow(arrayOf<Any?>(value))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        require(values?.containsKey(MICLOUD_SETTINGS_KEY) == true) {
            "Please use MiCloud SDK APIs to insert settings."
        }
        val key = values.getAsString(MICLOUD_SETTINGS_KEY)
        require(!key.isNullOrBlank()) { "Do not insert a null key." }
        require(allowInsert(key)) { "Key $key is not allowed to insert." }

        // See query(): preserve the stock migration attempt without making an absent legacy
        // provider permanently disable the replacement XMSF on fresh installations.
        migrateLegacySettings()

        // Stock 7.4.67-C MiCloudSettingsProvider uses commit() and returns the URI only after the
        // value is durable. The old port used apply() and reported success even if persistence later
        // failed, which could make cloud callers observe a write that never existed.
        if (!storeSetting(key, values.getAsString(MICLOUD_SETTINGS_VALUE))) return null
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw RuntimeException("Unsupport delete!!")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw RuntimeException("Unsupport update!!")
    }

    private fun settings(): SharedPreferences? = runCatching {
        context?.getSharedPreferences(SETTINGS_FILE_NAME, 0)
    }.getOrNull()

    private fun storeSetting(key: String, value: String?): Boolean {
        return settings()?.edit()?.putString(key, value)?.commit() == true
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

        val editor = settings()?.edit() ?: return
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
        // Stock 7.4.67-C compares the caller signature with system UID 1000. The old port trusted
        // root, system, and self by numeric UID, which was broader and did not prove platform trust.
        val signatureMatchesSystem = providerContext.packageManager.checkSignatures(
            callingUid,
            Process.SYSTEM_UID,
        ) == PackageManager.SIGNATURE_MATCH
        return isSensitiveCallerAllowed(hasCloudManagerPermission, signatureMatchesSystem)
    }

    companion object {
        const val AUTHORITY = "com.xiaomi.xmsf.provider.MiCloudSettingsProvider"
        internal const val CLOUD_MANAGER_PERMISSION = "com.xiaomi.permission.CLOUD_MANAGER"
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

        internal fun isSensitiveCallerAllowed(
            hasCloudManagerPermission: Boolean,
            signatureMatchesSystem: Boolean,
        ): Boolean = hasCloudManagerPermission || signatureMatchesSystem
    }
}
