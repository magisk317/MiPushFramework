package io.github.magisk317.mipush.manager.xmsfdelivery

/** Coarse installation state of the XMSF push runtime package. */
enum class XmsfRuntimeState {
    /** com.xiaomi.xmsf is not installed. */
    NOT_INSTALLED,

    /** Installed, but the version predates the pinned runtime version. */
    UPDATE_AVAILABLE,

    /** Installed, but signed by a certificate other than the host's. */
    SIGNATURE_MISMATCH,

    /** Installed at the pinned version and signed by the host certificate. */
    READY,
}

/** Result of probing the manager to runtime Binder link. */
enum class XmsfConnectivity {
    UNKNOWN,
    CONNECTED,
    RUNTIME_MISSING,
    PERMISSION_DENIED,
    TIMED_OUT,
    INCOMPATIBLE,
    FAILED,
}

/** Outcome of an [XmsfDelivery.ensureInstalled] attempt. */
sealed interface XmsfInstallResult {
    /** The runtime is installed at the pinned version and verified. */
    data object Installed : XmsfInstallResult

    /** Play flavor: the user was sent to the release page; verification pends. */
    data object GuidanceOpened : XmsfInstallResult

    data class Failed(val message: String) : XmsfInstallResult
}

/** Snapshot consumed by the settings UI. */
data class XmsfStatus(
    val state: XmsfRuntimeState = XmsfRuntimeState.NOT_INSTALLED,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val expectedVersionName: String? = null,
    val connectivity: XmsfConnectivity = XmsfConnectivity.UNKNOWN,
    val detail: String? = null,
) {
    val isReady: Boolean get() = state == XmsfRuntimeState.READY
}
