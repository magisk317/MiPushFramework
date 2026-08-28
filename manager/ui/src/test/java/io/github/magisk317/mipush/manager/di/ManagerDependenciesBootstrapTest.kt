package io.github.magisk317.mipush.manager.di

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ManagerDependenciesBootstrapTest {
    private lateinit var filesDir: File
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        stopGlobalKoin()
        resetBootstrapState()
        filesDir = Files.createTempDirectory("manager-koin-bootstrap").toFile()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.packageName } returns "io.github.magisk317.mipush.test"
    }

    @AfterEach
    fun tearDown() {
        stopGlobalKoin()
        resetBootstrapState()
        filesDir.deleteRecursively()
    }

    @Test
    fun `app shell requires its host then loads manager definitions idempotently`() {
        assertThrows<IllegalStateException> {
            ManagerDependencies.startFromAppShell(context)
        }

        startEmptyHost()
        val host = GlobalContext.get()

        assertDoesNotThrow { ManagerDependencies.startFromAppShell(context) }
        assertDoesNotThrow { ManagerDependencies.startFromAppShell(context) }
        assertSame(host, GlobalContext.get())
    }

    @Test
    fun `app shell host rejects remote host contamination`() {
        startEmptyHost()
        ManagerDependencies.startFromAppShell(context)

        val failure = assertThrows<IllegalStateException> {
            ManagerDependencies.startAsRemoteHost(context)
        }

        assertEquals("Manager dependencies already use the app-shell host", failure.message)
    }

    @Test
    fun `a stopped global host can be recreated before app shell startup`() {
        startEmptyHost()
        ManagerDependencies.startFromAppShell(context)

        stopGlobalKoin()
        resetBootstrapState()
        startEmptyHost()

        assertDoesNotThrow { ManagerDependencies.startFromAppShell(context) }
    }

    private fun startEmptyHost() {
        startKoin {
            androidContext(context)
            modules(module { })
        }
    }

    private fun stopGlobalKoin() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    private fun resetBootstrapState() {
        val type = ManagerDependencies::class.java
        type.getDeclaredField("bootstrapMode").apply {
            isAccessible = true
            set(null, null)
        }
        listOf(
            "hostModulesLoaded",
            "appInitializersStarted",
            "logSanitizationSyncStarted",
            "runtimePreferenceSyncStarted",
            "analyticsSyncStarted",
            "maintenanceSyncStarted",
            "standaloneEventSyncStarted",
        ).forEach { name ->
            type.getDeclaredField(name).apply {
                isAccessible = true
                setBoolean(null, false)
            }
        }
    }
}
