package io.github.magisk317.mipush.feature.wizard.support

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.magisk317.mipush.data.PreferenceRepository
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class WizardSPUtilsTest {
    @TempDir
    lateinit var tempDirectory: Path

    private fun contextWithVersionCode(versionCode: Long): Context {
        val packageInfo = mockk<PackageInfo>()
        every { packageInfo.longVersionCode } returns versionCode
        val packageManager = mockk<PackageManager>()
        every {
            packageManager.getPackageInfo(any<String>(), any<Int>())
        } returns packageInfo
        return mockk<Context>().apply {
            every { packageName } returns "io.github.magisk317.mipush"
            every { this@apply.packageManager } returns packageManager
        }
    }

    private fun repository(): PreferenceRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempDirectory.resolve("wizard.preferences_pb").toFile()
        }
        return PreferenceRepository(dataStore)
    }

    @Test
    fun `fully granted device never shows the report on a fresh install`() = runBlocking {
        val repository = repository()
        assertFalse(
            WizardSPUtils.consumeLaunchReport(
                context = contextWithVersionCode(1917L),
                preferenceRepository = repository,
                hasOutstandingRequirement = false,
            ),
        )
        Unit
    }

    @Test
    fun `fully granted device never shows the report on an upgrade`() = runBlocking {
        val repository = repository()
        repository.setWizardSeenVersionCode(1909L)
        assertFalse(
            WizardSPUtils.consumeLaunchReport(
                context = contextWithVersionCode(1917L),
                preferenceRepository = repository,
                hasOutstandingRequirement = false,
            ),
        )
        Unit
    }

    @Test
    fun `outstanding requirement shows the report exactly once after an upgrade`() = runBlocking {
        val repository = repository()
        repository.setWizardSeenVersionCode(1909L)
        val context = contextWithVersionCode(1917L)
        assertTrue(
            WizardSPUtils.consumeLaunchReport(
                context = context,
                preferenceRepository = repository,
                hasOutstandingRequirement = true,
            ),
        )
        // The same build never replays it, even while the requirement is still unmet.
        assertFalse(
            WizardSPUtils.consumeLaunchReport(
                context = context,
                preferenceRepository = repository,
                hasOutstandingRequirement = true,
            ),
        )
        Unit
    }

    @Test
    fun `outstanding requirement shows the report on a fresh install`() = runBlocking {
        val repository = repository()
        assertTrue(
            WizardSPUtils.consumeLaunchReport(
                context = contextWithVersionCode(1917L),
                preferenceRepository = repository,
                hasOutstandingRequirement = true,
            ),
        )
        Unit
    }

    @Test
    fun `skipping a granted launch also consumes the version`() = runBlocking {
        val repository = repository()
        val context = contextWithVersionCode(1917L)
        assertFalse(
            WizardSPUtils.consumeLaunchReport(
                context = context,
                preferenceRepository = repository,
                hasOutstandingRequirement = false,
            ),
        )
        // A later restart of the same build must not surface it even if a requirement
        // regressed, because ordinary launches never pop the report.
        assertFalse(
            WizardSPUtils.consumeLaunchReport(
                context = context,
                preferenceRepository = repository,
                hasOutstandingRequirement = true,
            ),
        )
        Unit
    }
}
