package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ZygiskConfigGateway
import io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult
import io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult
import io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient

class RemoteZygiskConfigGateway(
    private val client: ManagerRuntimeClient,
) : ZygiskConfigGateway {
    override suspend fun isZygiskModuleEnabled(): ZygiskModuleReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_IS_ENABLED,
        ) ?: return ZygiskModuleReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskModuleReadResult.Unavailable(result.details.ifBlank { "zygisk_status_unavailable" })
        }
        return ZygiskModuleReadResult.Available(result.resultLong == 1L)
    }

    override fun getZygiskConfigPath(): String = "/data/adb/mipush_zygisk/app.conf"

    override suspend fun getZygiskConfig(): ZygiskConfigReadResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_GET_CONFIG,
        ) ?: return ZygiskConfigReadResult.Unavailable("runtime_unavailable")
        if (!RemoteWriteSupport.isSuccess(result)) {
            return ZygiskConfigReadResult.Unavailable(result.details.ifBlank { "zygisk_config_unavailable" })
        }
        return ZygiskConfigReadResult.Available(ZygiskConfig.parse(result.details))
    }

    override suspend fun saveZygiskConfig(config: ZygiskConfig): Boolean {
        val content = config.toFileContent()
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SAVE_CONFIG,
            argument = content,
        ) ?: return false
        return RemoteWriteSupport.isSuccess(result)
    }

    override suspend fun forceStopApp(packageName: String): Boolean {
        return RemoteWriteSupport.isSuccess(RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_FORCE_STOP,
            packageName = packageName,
        ))
    }

    override suspend fun scanZygiskPackages(): ZygiskPackageScanResult {
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_ZYGISK_SCAN,
        ) ?: return ZygiskPackageScanResult.Unavailable("runtime_unavailable")
        return if (RemoteWriteSupport.isSuccess(result)) {
            ZygiskPackageScanResult.Available(result.details)
        } else {
            ZygiskPackageScanResult.Unavailable(result.details.ifBlank { "zygisk_scan_unavailable" })
        }
    }
}
