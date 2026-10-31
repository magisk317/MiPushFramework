package io.github.magisk317.mipush.manager.xmsfdelivery

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves the flavor-specific [XmsfDelivery] implementation.
 *
 * Each distribution flavor source set (`src/github`, `src/play`) contributes
 * one class with this fixed FQCN that delegates to its own delivery
 * implementation. The shared module therefore never names a flavor class
 * directly, and the module boundary gate sees a single reflective lookup
 * instead of a flavor-dependent compile-time edge.
 */
object XmsfDeliveryProvider {

    private const val FLAVOR_IMPL_CLASS =
        "io.github.magisk317.mipush.manager.xmsfdelivery.XmsfDeliveryFlavorImpl"

    private val cached = AtomicReference<XmsfDelivery?>()

    /**
     * @param trackCi channel gate read on every runtime probe: `true` probes
     *   the CI artifact track, `false` the release registry only. Called from
     *   background dispatchers; must not touch the main thread.
     * @throws IllegalStateException when the flavor class is missing (a
     *   mis-wired source set) or fails to construct.
     */
    fun get(context: Context, trackCi: () -> Boolean): XmsfDelivery {
        cached.get()?.let { return it }
        return synchronized(this) {
            cached.get() ?: create(context, trackCi).also { cached.set(it) }
        }
    }

    private fun create(context: Context, trackCi: () -> Boolean): XmsfDelivery {
        val appContext = context.applicationContext ?: context
        val instance = try {
            val clazz = Class.forName(FLAVOR_IMPL_CLASS)
            val constructor = clazz.getDeclaredConstructor(
                Context::class.java,
                Function0::class.java,
            )
            constructor.isAccessible = true
            constructor.newInstance(appContext, trackCi)
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException(
                "XMSF delivery flavor implementation $FLAVOR_IMPL_CLASS is unavailable",
                error,
            )
        }
        return instance as? XmsfDelivery
            ?: throw IllegalStateException("$FLAVOR_IMPL_CLASS does not implement XmsfDelivery")
    }
}
