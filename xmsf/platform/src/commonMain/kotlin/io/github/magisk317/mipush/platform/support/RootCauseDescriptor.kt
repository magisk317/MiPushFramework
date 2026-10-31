package io.github.magisk317.mipush.platform.support

// NoClassDefFoundError is a JVM-family type that common sources cannot name directly, so
// its failed-static-initialization shape is matched by runtime class name plus the message
// contract the JVM guarantees for it.
private const val NO_CLASS_DEF_FOUND_ERROR = "NoClassDefFoundError"

private const val COULD_NOT_INITIALIZE_PREFIX = "Could not initialize class "

/**
 * Log-safe description of [throwable]'s root cause: `cause=<root class name>`, plus
 * `failedInit=<class name>` when the root cause is a NoClassDefFoundError carrying the
 * "Could not initialize class <name>" message.
 *
 * The log sanitizer masks Throwable.message as sensitive payload, which also erases the
 * class name naming the real init failure -- the one diagnostic that matters when a static
 * initializer breaks on an R8-stripped build. Class names are not sensitive payload, so
 * this descriptor re-exposes exactly those and never embeds message text.
 */
internal fun rootCauseDescriptor(throwable: Throwable): String {
    var root: Throwable = throwable
    var cause = root.cause
    while (cause != null && cause !== root) {
        root = cause
        cause = root.cause
    }
    val className = root::class.qualifiedName ?: root::class.simpleName ?: "Throwable"
    val failedInit = root.failedStaticInitClass()
    return if (failedInit == null) "cause=$className" else "cause=$className failedInit=$failedInit"
}

private fun Throwable.failedStaticInitClass(): String? {
    if (this::class.simpleName != NO_CLASS_DEF_FOUND_ERROR) return null
    val message = message ?: return null
    if (!message.startsWith(COULD_NOT_INITIALIZE_PREFIX)) return null
    return message.removePrefix(COULD_NOT_INITIALIZE_PREFIX).trim().ifEmpty { null }
}
