package io.github.magisk317.mipush.platform.support

import com.xiaomi.xmpush.thrift.XmPushActionContainer

/**
 * Diagnostic seam for the container-build trace.
 *
 * The trace itself instruments the push SDK's own hook points, so its implementation lives in
 * :xmsf:push next to HookTrace/AspectLogCompat. Platform must not depend on that higher layer:
 * the shell installs the push-facing implementation during DI bootstrap, beside the other bridge
 * installs. A missing install only drops diagnostics, never behaviour.
 */
object BuildContainerTrace {
    @Volatile
    var trace: ((Int, XmPushActionContainer?) -> Unit)? = null

    fun onBuildContainer(payloadSize: Int, container: XmPushActionContainer?) {
        trace?.invoke(payloadSize, container)
    }
}
