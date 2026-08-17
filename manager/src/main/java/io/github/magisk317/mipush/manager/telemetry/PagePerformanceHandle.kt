package io.github.magisk317.mipush.manager.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos

/**
 * Manager-owned bridge between a page and its current navigation transition.
 *
 * The handle is token-scoped: callbacks from a disposed page or an older token are routed to the
 * recorder as stale diagnostics and cannot publish current page state. The Compose helper reports
 * first composition once and waits for a frame before reporting first meaningful frame; ordinary
 * recomposition therefore never counts as a meaningful frame.
 */
class PagePerformanceHandle(
    val token: TransitionToken,
    private val recorder: TransitionPerformanceRecorder,
) {
    fun pagerSettled(page: Int, timestampNanos: Long? = null) {
        recorder.markPagerSettled(token, page, timestampNanos ?: System.nanoTime())
    }

    fun firstComposition(timestampNanos: Long? = null) {
        recorder.markFirstComposition(token, timestampNanos ?: System.nanoTime())
    }

    fun firstMeaningfulFrame(timestampNanos: Long? = null) {
        recorder.markFirstMeaningfulFrame(token, timestampNanos ?: System.nanoTime())
    }

    fun cachePresented(source: String, ageMillis: Long = 0, timestampNanos: Long? = null) {
        recorder.markCacheResult(token, source, ageMillis, timestampNanos ?: System.nanoTime())
    }

    fun dataReady(timestampNanos: Long? = null) {
        recorder.markDataReady(token, timestampNanos ?: System.nanoTime())
    }

    fun binderCall(
        operation: String,
        priority: String,
        durationMillis: Long,
        result: String,
        availability: String? = null,
        timestampNanos: Long? = null,
    ) {
        recorder.markBinderCall(
            token = token,
            operation = operation,
            priority = priority,
            durationMillis = durationMillis,
            result = result,
            availability = availability,
            timestampNanos = timestampNanos ?: System.nanoTime(),
        )
    }

    fun cancel(timestampNanos: Long? = null) {
        recorder.cancel(token, timestampNanos ?: System.nanoTime())
    }

    fun finish(timestampNanos: Long? = null) {
        recorder.finish(token, timestampNanos ?: System.nanoTime())
    }
}

/** Reports token-scoped Compose stages without treating recomposition as a frame. */
@Composable
fun PagePerformanceLifecycle(
    handle: PagePerformanceHandle?,
    content: @Composable () -> Unit,
) {
    if (handle == null) {
        content()
        return
    }

    SideEffect { handle.firstComposition() }
    LaunchedEffect(handle) {
        withFrameNanos { timestamp -> handle.firstMeaningfulFrame(timestamp) }
    }
    DisposableEffect(handle) {
        onDispose { handle.cancel() }
    }
    content()
}
