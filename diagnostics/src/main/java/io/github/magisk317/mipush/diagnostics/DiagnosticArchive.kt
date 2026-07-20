package io.github.magisk317.mipush.diagnostics

import android.content.Context
import android.content.Intent
import java.io.File
import io.github.magisk317.xposed.diagnostics.DiagnosticArchive as SharedDiagnosticArchive

/**
 * Compatibility facade over the shared archive pipeline in magisk-xposed-kit:diagnostics.
 * Product collectors stay in common/xmsf LogBundleExporter implementations.
 */
object DiagnosticArchive {
    data class BundleResult(
        val file: File?,
        val details: String,
    )

    data class ClearResult(
        val success: Boolean,
        val details: String,
    )

    fun buildBundle(
        context: Context,
        timestamp: String,
        exportDir: File,
        exportFilePrefix: String,
        stagingDirPrefix: String,
        deletePath: (File) -> Boolean = SharedDiagnosticArchive::deleteRecursively,
        collect: (File, MutableList<String>) -> Unit,
        onInfo: (String) -> Unit = {},
        onWarning: (String) -> Unit = {},
        onError: (String, Throwable?) -> Unit = { _, _ -> },
        makeWorldReadable: Boolean = true,
        worldReadableParentDepth: Int = 2,
    ): BundleResult {
        val result = SharedDiagnosticArchive.buildBundle(
            context = context,
            timestamp = timestamp,
            exportDir = exportDir,
            exportFilePrefix = exportFilePrefix,
            stagingDirPrefix = stagingDirPrefix,
            deletePath = deletePath,
            collect = collect,
            onInfo = onInfo,
            onWarning = onWarning,
            onError = onError,
            makeWorldReadable = makeWorldReadable,
            worldReadableParentDepth = worldReadableParentDepth,
        )
        return BundleResult(result.file, result.details)
    }

    fun buildShareIntent(
        context: Context,
        file: File,
        authority: String,
        onInfo: (String) -> Unit = {},
        onWarning: (String) -> Unit = {},
    ): Intent = SharedDiagnosticArchive.buildShareIntent(
        context = context,
        file = file,
        authority = authority,
        onInfo = onInfo,
        onWarning = onWarning,
    )

    fun clearDirectories(
        targets: List<Pair<String, File>>,
        deletePath: (File) -> Boolean = SharedDiagnosticArchive::deleteRecursively,
        onWarning: (String) -> Unit = {},
    ): ClearResult {
        val result = SharedDiagnosticArchive.clearDirectories(
            targets = targets,
            deletePath = deletePath,
            onWarning = onWarning,
        )
        return ClearResult(result.success, result.details)
    }

    fun copyDirectory(
        source: File,
        target: File,
        includeFile: (File) -> Boolean = { true },
        onWarning: (String) -> Unit = {},
    ) {
        SharedDiagnosticArchive.copyDirectory(
            source = source,
            target = target,
            includeFile = includeFile,
            onWarning = onWarning,
        )
    }

    fun ensureDirectory(
        dir: File,
        recreateWhenFile: Boolean,
        onWarning: (String) -> Unit = {},
    ): Boolean = SharedDiagnosticArchive.ensureDirectory(dir, recreateWhenFile, onWarning)

    fun deleteRecursively(target: File): Boolean = SharedDiagnosticArchive.deleteRecursively(target)
}
