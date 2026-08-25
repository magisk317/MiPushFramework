package io.github.magisk317.mipush.manager.api

import android.os.IBinder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerRuntimeServiceAidlTest {
    @Test
    fun `transaction ids remain append only`() {
        assertEquals(IBinder.FIRST_CALL_TRANSACTION, IManagerRuntimeService.Stub.TRANSACTION_handshake)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 1, IManagerRuntimeService.Stub.TRANSACTION_getConnectionSnapshot)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 2, IManagerRuntimeService.Stub.TRANSACTION_getApplicationPage)
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 3, IManagerRuntimeService.Stub.TRANSACTION_getApplicationDetail)
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 4,
            IManagerRuntimeService.Stub.TRANSACTION_getApplicationDiagnostics,
        )
        assertEquals(IBinder.FIRST_CALL_TRANSACTION + 5, IManagerRuntimeService.Stub.TRANSACTION_getEventPage)
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 6,
            IManagerRuntimeService.Stub.TRANSACTION_getNotificationChannelPage,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 7,
            IManagerRuntimeService.Stub.TRANSACTION_getConfigurationCatalog,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 8,
            IManagerRuntimeService.Stub.TRANSACTION_exportRuntimeLogs,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 9,
            IManagerRuntimeService.Stub.TRANSACTION_getRuntimePreferences,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 10,
            IManagerRuntimeService.Stub.TRANSACTION_getManagerMigrationSnapshot,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 11,
            IManagerRuntimeService.Stub.TRANSACTION_uploadConfiguration,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 12,
            IManagerRuntimeService.Stub.TRANSACTION_executeWrite,
        )
        assertEquals(
            IBinder.FIRST_CALL_TRANSACTION + 13,
            IManagerRuntimeService.Stub.TRANSACTION_getRuntimeEnvironmentSnapshot,
        )
    }
}
