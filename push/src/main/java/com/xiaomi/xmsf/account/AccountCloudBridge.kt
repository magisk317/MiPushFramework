package com.xiaomi.xmsf.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import com.xiaomi.mipush.sdk.MiPushClient
import io.github.magisk317.mipush.runtime.PushRuntime

data class AccountCloudAvailability(
    val xiaomiAccountPresent: Boolean,
    val accountPackagePresent: Boolean,
    val cloudServicePackagePresent: Boolean,
)

data class AccountAliasSyncResult(
    val changed: Boolean,
    val currentAlias: String?,
)

data class ServiceTokenResult(
    val sid: String,
    val token: String?,
    val source: String,
    val available: Boolean,
    val error: String? = null,
)

interface AccountCloudBridge {
    fun availability(): AccountCloudAvailability

    fun currentAccount(): Account?

    fun currentAccountName(): String? = currentAccount()?.name?.takeIf { it.isNotBlank() }

    fun syncAlias(previousAlias: String?): AccountAliasSyncResult

    fun getServiceToken(sid: String): ServiceTokenResult
}

class DefaultAccountCloudBridge private constructor(
    private val context: Context,
) : AccountCloudBridge {
    private val accountManager by lazy { AccountManager.get(context) }

    override fun availability(): AccountCloudAvailability {
        return AccountCloudAvailability(
            xiaomiAccountPresent = currentAccount() != null,
            accountPackagePresent = isPackageInstalled(ACCOUNT_PACKAGE_NAME),
            cloudServicePackagePresent = isPackageInstalled(CLOUD_SERVICE_PACKAGE_NAME),
        )
    }

    override fun currentAccount(): Account? {
        return accountManager.accounts.firstOrNull { account ->
            account.type == XIAOMI_ACCOUNT_TYPE && account.name.isNotBlank()
        }
    }

    override fun syncAlias(previousAlias: String?): AccountAliasSyncResult {
        val currentAlias = currentAccountName()
        if (previousAlias.isNullOrBlank() && currentAlias.isNullOrBlank()) {
            return AccountAliasSyncResult(changed = false, currentAlias = null)
        }
        if (previousAlias.isNullOrBlank() && !currentAlias.isNullOrBlank()) {
            MiPushClient.setAlias(context, currentAlias, null)
            PushRuntime.observeAccountEvent("alias_set", "DefaultAccountCloudBridge.syncAlias")
            return AccountAliasSyncResult(changed = true, currentAlias = currentAlias)
        }
        if (!previousAlias.isNullOrBlank() && currentAlias != previousAlias) {
            MiPushClient.unsetAlias(context, previousAlias, null)
            PushRuntime.observeAccountEvent("alias_unset", "DefaultAccountCloudBridge.syncAlias")
            currentAlias?.let { MiPushClient.setAlias(context, it, null) }
            if (!currentAlias.isNullOrBlank()) {
                PushRuntime.observeAccountEvent("alias_set", "DefaultAccountCloudBridge.syncAlias")
            }
            return AccountAliasSyncResult(changed = true, currentAlias = currentAlias)
        }
        return AccountAliasSyncResult(changed = false, currentAlias = currentAlias)
    }

    override fun getServiceToken(sid: String): ServiceTokenResult {
        val account = currentAccount()
            ?: return ServiceTokenResult(
                sid = sid,
                token = null,
                source = "none",
                available = false,
                error = "xiaomi_account_missing",
            )
        return runCatching {
            val result = accountManager.getAuthToken(account, sid, null, false, null, null).result
            val token = result?.getString(AccountManager.KEY_AUTHTOKEN)
            val source = when {
                !token.isNullOrBlank() -> "account_manager"
                availability().cloudServicePackagePresent -> "cloudservice_present_without_token"
                else -> "account_manager_empty"
            }
            ServiceTokenResult(
                sid = sid,
                token = token,
                source = source,
                available = !token.isNullOrBlank(),
                error = if (token.isNullOrBlank()) "auth_token_empty" else null,
            )
        }.getOrElse {
            ServiceTokenResult(
                sid = sid,
                token = null,
                source = "account_manager_error",
                available = false,
                error = it.message ?: it::class.java.simpleName,
            )
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val XIAOMI_ACCOUNT_TYPE = "com.xiaomi"
        private const val ACCOUNT_PACKAGE_NAME = "com.xiaomi.account"
        private const val CLOUD_SERVICE_PACKAGE_NAME = "com.miui.cloudservice"

        @Volatile
        private var instance: DefaultAccountCloudBridge? = null

        @JvmStatic
        fun getInstance(context: Context): DefaultAccountCloudBridge {
            return instance ?: synchronized(this) {
                instance ?: DefaultAccountCloudBridge(context.applicationContext ?: context).also { instance = it }
            }
        }

        @JvmStatic
        fun toBundle(result: ServiceTokenResult): Bundle {
            return Bundle().apply {
                putString("sid", result.sid)
                putString("token", result.token)
                putString("source", result.source)
                putBoolean("available", result.available)
                putString("error", result.error)
            }
        }
    }
}
