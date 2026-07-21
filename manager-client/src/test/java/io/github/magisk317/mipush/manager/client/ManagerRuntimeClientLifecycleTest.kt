package io.github.magisk317.mipush.manager.client

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.magisk317.mipush.manager.api.IManagerRuntimeService
import io.github.magisk317.mipush.manager.api.ManagerApplicationDetailDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationDiagnosticsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationPageDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationQueryDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationStatsDto
import io.github.magisk317.mipush.manager.api.ManagerApplicationSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerRuntimeClientLifecycleTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `close is idempotent and unbinds once`() {
        val service = FakeRuntimeService()
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        client.close()
        client.close()

        assertEquals(1, context.bindCount)
        assertEquals(1, context.unbindCount)
        assertEquals(1, service.handshakeCount)
        assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
    }

    @Test
    fun `binding death rebinds and a rejected handshake releases the new binding`() {
        val service = FakeRuntimeService(rejectHandshakeAfter = 1)
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        context.dispatchBindingDied()

        assertEquals(2, context.bindCount)
        assertEquals(2, context.unbindCount)
        assertEquals(2, service.handshakeCount)
        assertEquals(ManagerRuntimeAvailability.PermissionDenied, client.availability.value)
        client.close()
        assertEquals(2, context.unbindCount)
    }

    @Test
    fun `missing capability skips only the snapshot operation`() = runBlocking {
        val service = FakeRuntimeService(capabilities = emptyList())
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val result = client.getConnectionSnapshot()

        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        assertEquals(
            ManagerRuntimeResult.Unsupported(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT),
            result,
        )
        assertEquals(0, service.snapshotCount)
        client.close()
    }

    @Test
    fun `missing application capability skips only that operation`() = runBlocking {
        val service = FakeRuntimeService()
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val applicationResult = client.getApplicationPage(ManagerApplicationQueryDto())
        val snapshotResult = client.getConnectionSnapshot()

        assertEquals(
            ManagerRuntimeResult.Unsupported(ManagerProtocol.CAPABILITY_APPLICATION_LIST),
            applicationResult,
        )
        assertTrue(snapshotResult is ManagerRuntimeResult.Success)
        assertEquals(0, service.applicationPageCount)
        assertEquals(1, service.snapshotCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `invalid application request does not invoke the runtime or release the session`() = runBlocking {
        val service = FakeRuntimeService(
            capabilities = listOf(ManagerProtocol.CAPABILITY_APPLICATION_LIST),
        )
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val result = client.getApplicationPage(
            ManagerApplicationQueryDto(pageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE + 1),
        )

        assertEquals(ManagerRuntimeResult.Failed("invalid_application_page_size"), result)
        assertEquals(0, service.applicationPageCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `nullable application detail is a successful not found result`() = runBlocking {
        val service = FakeRuntimeService(
            capabilities = listOf(ManagerProtocol.CAPABILITY_APPLICATION_DETAIL),
        )
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val result = client.getApplicationDetail("example.missing")

        assertEquals(ManagerRuntimeResult.Success(null), result)
        assertEquals(1, service.applicationDetailCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `missing diagnostics capability does not disable application detail`() = runBlocking {
        val service = FakeRuntimeService(
            capabilities = listOf(ManagerProtocol.CAPABILITY_APPLICATION_DETAIL),
        )
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val diagnostics = client.getApplicationDiagnostics("example.missing", registeredType = 0)
        val detail = client.getApplicationDetail("example.missing")

        assertEquals(
            ManagerRuntimeResult.Unsupported(ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS),
            diagnostics,
        )
        assertEquals(ManagerRuntimeResult.Success(null), detail)
        assertEquals(0, service.applicationDiagnosticsCount)
        assertEquals(1, service.applicationDetailCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `invalid capability response does not release unrelated operations`() = runBlocking {
        val service = FakeRuntimeService(
            capabilities = listOf(
                ManagerProtocol.CAPABILITY_APPLICATION_LIST,
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            ),
            applicationPageResult = ManagerApplicationPageDto(
                items = listOf(ManagerApplicationSummaryDto(packageName = "example.app")),
                stats = ManagerApplicationStatsDto(
                    total = 1,
                    usingMiPush = 1,
                    notUsingMiPush = 0,
                    registered = 0,
                    notRegistered = 0,
                ),
            ),
        )
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val page = client.getApplicationPage(ManagerApplicationQueryDto())
        val snapshot = client.getConnectionSnapshot()

        assertEquals(ManagerRuntimeResult.Failed("inconsistent_application_stats_registration"), page)
        assertTrue(snapshot is ManagerRuntimeResult.Success)
        assertEquals(0, context.unbindCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `runtime operation errors are sanitized and remain feature local`() = runBlocking {
        val service = FakeRuntimeService(
            capabilities = listOf(
                ManagerProtocol.CAPABILITY_APPLICATION_DETAIL,
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            ),
            applicationDetailFailure = IllegalArgumentException("private runtime path"),
        )
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        val detail = client.getApplicationDetail("example.app")
        val snapshot = client.getConnectionSnapshot()

        assertEquals(ManagerRuntimeResult.Failed("runtime_operation_failed"), detail)
        assertTrue(snapshot is ManagerRuntimeResult.Success)
        assertEquals(0, context.unbindCount)
        assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
        client.close()
    }

    @Test
    fun `callback arriving after close cannot restore the session`() {
        val service = FakeRuntimeService()
        val context = FakeServiceContext(service, autoConnect = false)
        val client = client(context)

        client.connect()
        client.close()
        context.dispatchConnected(0, service)

        assertEquals(1, context.bindCount)
        assertEquals(1, context.unbindCount)
        assertEquals(0, service.handshakeCount)
        assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
    }

    @Test
    fun `duplicate binder callback starts a fresh session instead of replacing ownership`() {
        val service = FakeRuntimeService(runtimeVersionName = "current")
        val replacement = FakeRuntimeService(runtimeVersionName = "replacement")
        val context = FakeServiceContext(service)
        val client = client(context)

        client.connect()
        context.dispatchConnected(0, replacement)

        assertEquals(2, context.bindCount)
        assertEquals(1, context.unbindCount)
        assertEquals(2, service.handshakeCount)
        assertEquals(0, replacement.handshakeCount)
        assertEquals(
            "current",
            (client.availability.value as ManagerRuntimeAvailability.Available).handshake.runtimeVersionName,
        )
        client.close()
    }

    @Test
    fun `late handshake from an old session cannot replace the new session`() = runBlocking {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstCompleted = CountDownLatch(1)
        val first = FakeRuntimeService(
            runtimeVersionName = "old",
            handshakeStarted = firstStarted,
            handshakeRelease = releaseFirst,
            handshakeCompleted = firstCompleted,
        )
        val second = FakeRuntimeService(runtimeVersionName = "new")
        val context = FakeServiceContext(serviceProvider = { bindIndex -> if (bindIndex == 1) first else second })
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val client = client(context, ioDispatcher = dispatcher, callTimeoutMillis = 1_000L)

        try {
            client.connect()
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS))
            context.dispatchBindingDied(0)
            val available = withTimeout(1_000L) {
                client.availability.first {
                    it is ManagerRuntimeAvailability.Available && it.handshake.runtimeVersionName == "new"
                }
            } as ManagerRuntimeAvailability.Available

            releaseFirst.countDown()
            assertTrue(firstCompleted.await(1, TimeUnit.SECONDS))
            assertEquals("new", available.handshake.runtimeVersionName)
            assertEquals(
                "new",
                (client.availability.value as ManagerRuntimeAvailability.Available).handshake.runtimeVersionName,
            )
        } finally {
            releaseFirst.countDown()
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `accepted bind without a callback times out and remains skippable`() = runBlocking {
        val context = FakeServiceContext(FakeRuntimeService(), autoConnect = false)
        val client = client(
            context = context,
            callTimeoutMillis = 20L,
            reconnectDelayProvider = { 10_000L },
        )

        client.connect()
        val timedOut = withTimeout(1_000L) {
            client.availability.first { it == ManagerRuntimeAvailability.TimedOut }
        }

        assertEquals(ManagerRuntimeAvailability.TimedOut, timedOut)
        assertEquals(1, context.unbindCount)
        client.close()
    }

    @Test
    fun `blocked synchronous handshake does not block the timeout result`() = runBlocking {
        val handshakeStarted = CountDownLatch(1)
        val releaseHandshake = CountDownLatch(1)
        val service = FakeRuntimeService(
            handshakeStarted = handshakeStarted,
            handshakeRelease = releaseHandshake,
        )
        val context = FakeServiceContext(service)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val client = client(
            context = context,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 20L,
            reconnectDelayProvider = { 10_000L },
        )

        try {
            client.connect()
            assertTrue(handshakeStarted.await(1, TimeUnit.SECONDS))
            val timedOut = withTimeout(1_000L) {
                client.availability.first { it == ManagerRuntimeAvailability.TimedOut }
            }

            assertEquals(ManagerRuntimeAvailability.TimedOut, timedOut)
            assertEquals(1, context.unbindCount)
        } finally {
            releaseHandshake.countDown()
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `caller cancellation is not converted into a client timeout`() = runBlocking {
        val snapshotStarted = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val service = FakeRuntimeService(
            snapshotStarted = snapshotStarted,
            snapshotRelease = releaseSnapshot,
        )
        val context = FakeServiceContext(service)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val client = client(
            context = context,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 10_000L,
            reconnectDelayProvider = { 10_000L },
        )

        try {
            client.connect()
            withTimeout(1_000L) {
                client.availability.first { it is ManagerRuntimeAvailability.Available }
            }
            val callerCancelled = runCatching {
                withTimeout(20L) { client.getConnectionSnapshot() }
            }.exceptionOrNull()

            assertTrue(snapshotStarted.await(1, TimeUnit.SECONDS))
            assertTrue(callerCancelled is kotlinx.coroutines.TimeoutCancellationException)
            assertTrue(client.availability.value is ManagerRuntimeAvailability.Available)
            assertEquals(0, context.unbindCount)
        } finally {
            releaseSnapshot.countDown()
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `session release converts internal call cancellation to unavailable`() = runBlocking {
        val snapshotStarted = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val service = FakeRuntimeService(
            snapshotStarted = snapshotStarted,
            snapshotRelease = releaseSnapshot,
        )
        val context = FakeServiceContext(service)
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val client = client(
            context = context,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 10_000L,
            reconnectDelayProvider = { 10_000L },
        )

        try {
            client.connect()
            withTimeout(1_000L) {
                client.availability.first { it is ManagerRuntimeAvailability.Available }
            }
            val result = async(dispatcher) { client.getConnectionSnapshot() }
            assertTrue(snapshotStarted.await(1, TimeUnit.SECONDS))

            context.dispatchBindingDied()
            // The runtime transact is simulated as blocking and cannot be interrupted by
            // cancellation; unblock it so the released session converts the stale response
            // to a typed unavailable result instead of racing the assertion timeout.
            releaseSnapshot.countDown()

            assertEquals(
                ManagerRuntimeResult.Unavailable(
                    ManagerRuntimeAvailability.TemporarilyDisconnected(DisconnectReason.BINDING_DIED),
                ),
                withTimeout(1_000L) { result.await() },
            )
        } finally {
            releaseSnapshot.countDown()
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `internal snapshot timeout keeps a typed timeout result while reconnecting`() = runBlocking {
        val snapshotStarted = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val service = FakeRuntimeService(
            snapshotStarted = snapshotStarted,
            snapshotRelease = releaseSnapshot,
        )
        val context = FakeServiceContext(service)
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val client = client(
            context = context,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 20L,
            reconnectDelayProvider = { 0L },
        )

        try {
            client.connect()
            withTimeout(1_000L) {
                client.availability.first { it is ManagerRuntimeAvailability.Available }
            }
            val result = client.getConnectionSnapshot()

            assertTrue(snapshotStarted.await(1, TimeUnit.SECONDS))
            assertEquals(
                ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.TimedOut),
                result,
            )
        } finally {
            releaseSnapshot.countDown()
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `exhausted remote call permits still produce a typed handshake timeout`() = runBlocking {
        val handshakeStarted = List(3) { CountDownLatch(1) }
        val releaseHandshake = List(3) { CountDownLatch(1) }
        val handshakeCompleted = List(3) { CountDownLatch(1) }
        val services = List(3) { index ->
            FakeRuntimeService(
                runtimeVersionName = "blocked-$index",
                handshakeStarted = handshakeStarted[index],
                handshakeRelease = releaseHandshake[index],
                handshakeCompleted = handshakeCompleted[index],
            )
        }
        val context = FakeServiceContext(
            serviceProvider = { bindIndex -> services[bindIndex - 1] },
        )
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        val client = client(
            context = context,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 50L,
            reconnectDelayProvider = { 10_000L },
        )

        try {
            repeat(2) { index ->
                client.connect()
                assertTrue(handshakeStarted[index].await(1, TimeUnit.SECONDS))
                withTimeout(1_000L) {
                    client.availability.first { it == ManagerRuntimeAvailability.TimedOut }
                }
            }

            client.connect()
            withTimeout(1_000L) {
                client.availability.first { it == ManagerRuntimeAvailability.TimedOut }
            }

            assertEquals(0, services[2].handshakeCount)
            assertEquals(3, context.bindCount)
            assertEquals(3, context.unbindCount)

            client.close()
            assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
            assertEquals(3, context.unbindCount)
        } finally {
            releaseHandshake.forEach(CountDownLatch::countDown)
            assertTrue(handshakeCompleted[0].await(1, TimeUnit.SECONDS))
            assertTrue(handshakeCompleted[1].await(1, TimeUnit.SECONDS))
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `close while bind is in flight releases an accepted binding once`() {
        val context = BlockingBindContext()
        val client = client(context)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val connectFuture = executor.submit { client.connect() }
            assertTrue(context.bindStarted.await(1, TimeUnit.SECONDS))
            client.close()
            context.allowBindReturn.countDown()
            connectFuture.get(1, TimeUnit.SECONDS)

            assertEquals(1, context.bindCount)
            assertEquals(1, context.unbindCount)
            assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
        } finally {
            context.allowBindReturn.countDown()
            client.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `owner scope cancellation closes and unbinds the client`() {
        val ownerJob = Job()
        val ownerScope = CoroutineScope(ownerJob + Dispatchers.Unconfined)
        val context = FakeServiceContext(FakeRuntimeService())
        val client = ManagerRuntimeClient(
            context = context,
            scope = ownerScope,
            ioDispatcher = Dispatchers.Unconfined,
        )

        client.connect()
        ownerJob.cancel()

        assertEquals(1, context.unbindCount)
        assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
    }

    @Test
    fun `owner scope cancellation releases binding before a blocked handshake returns`() {
        val ownerJob = Job()
        val ownerScope = CoroutineScope(ownerJob + Dispatchers.Unconfined)
        val handshakeStarted = CountDownLatch(1)
        val releaseHandshake = CountDownLatch(1)
        val handshakeCompleted = CountDownLatch(1)
        val service = FakeRuntimeService(
            handshakeStarted = handshakeStarted,
            handshakeRelease = releaseHandshake,
            handshakeCompleted = handshakeCompleted,
        )
        val context = FakeServiceContext(service)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val client = ManagerRuntimeClient(
            context = context,
            scope = ownerScope,
            ioDispatcher = dispatcher,
            callTimeoutMillis = 10_000L,
        )

        try {
            client.connect()
            assertTrue(handshakeStarted.await(1, TimeUnit.SECONDS))

            ownerJob.cancel()

            assertEquals(1, context.unbindCount)
            assertEquals(ManagerRuntimeAvailability.Disconnected, client.availability.value)
            assertEquals(1L, handshakeCompleted.count)
        } finally {
            releaseHandshake.countDown()
            assertTrue(handshakeCompleted.await(1, TimeUnit.SECONDS))
            client.close()
            dispatcher.close()
        }
    }

    @Test
    fun `remote runtime failure becomes a typed failure`() {
        val context = FakeServiceContext(
            FakeRuntimeService(handshakeFailure = IllegalArgumentException("malformed reply")),
        )
        val client = client(context)

        client.connect()

        assertTrue(client.availability.value is ManagerRuntimeAvailability.Failed)
        assertEquals(1, context.unbindCount)
        client.close()
    }

    @Test
    fun `rejected bind reports missing runtime without retrying forever`() {
        val context = FakeServiceContext(FakeRuntimeService(), bindResult = false)
        val client = client(context, reconnectDelayProvider = { 10_000L })

        client.connect()

        assertEquals(ManagerRuntimeAvailability.RuntimeMissing, client.availability.value)
        assertEquals(1, context.bindCount)
        assertEquals(0, context.unbindCount)
        client.close()
    }

    private fun client(
        context: Context,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        callTimeoutMillis: Long = 3_000L,
        reconnectDelayProvider: (Int) -> Long = { 0L },
    ) = ManagerRuntimeClient(
        context = context,
        scope = scope,
        ioDispatcher = ioDispatcher,
        callTimeoutMillis = callTimeoutMillis,
        reconnectDelayProvider = reconnectDelayProvider,
    )

    private class FakeServiceContext(
        private val serviceProvider: (Int) -> FakeRuntimeService,
        private val autoConnect: Boolean = true,
        private val bindResult: Boolean = true,
    ) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        private val component = ComponentName(
            ManagerProtocol.RUNTIME_PACKAGE,
            ManagerProtocol.RUNTIME_SERVICE_CLASS,
        )
        private val connections = mutableListOf<ServiceConnection>()
        private val boundConnections = mutableSetOf<ServiceConnection>()

        constructor(
            service: FakeRuntimeService,
            autoConnect: Boolean = true,
            bindResult: Boolean = true,
        ) : this({ service }, autoConnect, bindResult)

        var bindCount = 0
            private set
        var unbindCount = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun bindService(serviceIntent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            val service: FakeRuntimeService
            synchronized(this) {
                bindCount += 1
                connections += connection
                if (!bindResult) return false
                boundConnections += connection
                service = serviceProvider(bindCount)
            }
            if (autoConnect) connection.onServiceConnected(component, service)
            return bindResult
        }

        override fun unbindService(connection: ServiceConnection) {
            synchronized(this) {
                check(boundConnections.remove(connection)) { "service is not bound" }
                unbindCount += 1
            }
        }

        fun dispatchConnected(index: Int, service: FakeRuntimeService) {
            synchronized(this) { connections[index] }.onServiceConnected(component, service)
        }

        fun dispatchBindingDied(index: Int = connections.lastIndex) {
            synchronized(this) { connections[index] }.onBindingDied(component)
        }
    }

    private class BlockingBindContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val bindStarted = CountDownLatch(1)
        val allowBindReturn = CountDownLatch(1)
        private var boundConnection: ServiceConnection? = null

        var bindCount = 0
            private set
        var unbindCount = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun bindService(serviceIntent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            synchronized(this) { bindCount += 1 }
            bindStarted.countDown()
            check(allowBindReturn.await(1, TimeUnit.SECONDS)) { "test did not release bind" }
            synchronized(this) { boundConnection = connection }
            return true
        }

        override fun unbindService(connection: ServiceConnection) {
            synchronized(this) {
                check(boundConnection === connection) { "service is not bound" }
                boundConnection = null
                unbindCount += 1
            }
        }
    }

    private class FakeRuntimeService(
        private val capabilities: List<String> = listOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT),
        private val rejectHandshakeAfter: Int = Int.MAX_VALUE,
        private val runtimeVersionName: String = "test",
        private val handshakeStarted: CountDownLatch? = null,
        private val handshakeRelease: CountDownLatch? = null,
        private val handshakeCompleted: CountDownLatch? = null,
        private val handshakeFailure: RuntimeException? = null,
        private val snapshotStarted: CountDownLatch? = null,
        private val snapshotRelease: CountDownLatch? = null,
        private val applicationPageResult: ManagerApplicationPageDto = ManagerApplicationPageDto(
            stats = ManagerApplicationStatsDto(),
        ),
        private val applicationDetailFailure: RuntimeException? = null,
    ) : IManagerRuntimeService.Stub() {
        var handshakeCount = 0
            private set
        var snapshotCount = 0
            private set
        var applicationPageCount = 0
            private set
        var applicationDetailCount = 0
            private set
        var applicationDiagnosticsCount = 0
            private set

        override fun handshake(clientMajor: Int, clientMinor: Int): ManagerHandshake {
            handshakeCount += 1
            handshakeStarted?.countDown()
            try {
                handshakeRelease?.await(1, TimeUnit.SECONDS)
                handshakeFailure?.let { throw it }
                if (handshakeCount > rejectHandshakeAfter) {
                    throw SecurityException("rejected")
                }
                return ManagerHandshake(
                    protocolMajor = ManagerProtocol.MAJOR,
                    protocolMinor = ManagerProtocol.MINOR,
                    runtimeVersionName = runtimeVersionName,
                    runtimeVersionCode = 1L,
                    supportedCapabilities = capabilities,
                    maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
                    maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
                )
            } finally {
                handshakeCompleted?.countDown()
            }
        }

        override fun getConnectionSnapshot(): ManagerConnectionSnapshotDto {
            snapshotCount += 1
            snapshotStarted?.countDown()
            snapshotRelease?.await(1, TimeUnit.SECONDS)
            return ManagerConnectionSnapshotDto(
                connectionState = ManagerProtocol.CONNECTION_STATE_CONNECTED,
                connectedAtMs = 0L,
                lastDisconnectedAtMs = 0L,
                connectionSessionCount = 0L,
                serverHost = null,
                serverIp = null,
                keepAliveIntervalMs = 0,
                pingIntervalMs = 0,
                downstreamMessageCount = 0L,
                deliveredToAppCount = 0L,
                duplicateMessageCount = 0L,
                ackMessageCount = 0L,
                registeredPackageCount = 0,
                trackedChannelCount = 0,
                boundChannelCount = 0,
            )
        }

        override fun getApplicationPage(query: ManagerApplicationQueryDto): ManagerApplicationPageDto {
            applicationPageCount += 1
            return applicationPageResult
        }

        override fun getApplicationDetail(
            packageName: String,
            ignoreNotRegistered: Boolean,
        ): ManagerApplicationDetailDto? {
            applicationDetailCount += 1
            applicationDetailFailure?.let { throw it }
            return null
        }

        override fun getApplicationDiagnostics(
            packageName: String,
            registeredType: Int,
        ): ManagerApplicationDiagnosticsDto {
            applicationDiagnosticsCount += 1
            return ManagerApplicationDiagnosticsDto(registeredType = registeredType)
        }

        override fun getEventPage(query: ManagerEventQueryDto): ManagerEventPageDto = ManagerEventPageDto()

        override fun getNotificationChannelPage(
            query: ManagerNotificationChannelQueryDto,
        ): ManagerNotificationChannelPageDto = ManagerNotificationChannelPageDto()

        override fun getConfigurationCatalog(): ManagerConfigurationCatalogDto =
            ManagerConfigurationCatalogDto()

        override fun exportRuntimeLogs(): ManagerLogExportResultDto =
            ManagerLogExportResultDto(success = false, details = "unused")

        override fun asBinder(): IBinder = this
    }
}
