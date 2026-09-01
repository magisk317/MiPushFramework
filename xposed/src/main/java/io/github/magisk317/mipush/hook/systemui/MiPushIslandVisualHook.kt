package io.github.magisk317.mipush.hook.systemui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod
import java.util.Collections
import java.util.WeakHashMap

private const val GLASS_STROKE_WIDTH = 1.5f
private const val GLASS_HIGHLIGHT_ALPHA = 210
private const val GLASS_DIM_HIGHLIGHT_ALPHA = 150
private const val GLASS_ACCENT_ALPHA = 35
private const val OUTER_GLOW_STROKE_WIDTH = 2f
private const val OUTER_GLOW_ALPHA = 78
private const val MIN_VISIBLE_ALPHA = 0.01f
private const val ACCENT_HIGHLIGHT_ALPHA = 220
private const val ACCENT_COLOR_ALPHA = 160
private const val ACCENT_GLOW_ALPHA = 70
private const val ACCENT_STROKE_WIDTH = 2f
private const val ACCENT_GLOW_STROKE_WIDTH = 3f

/**
 * MiPush-owned visual layer for HyperOS DynamicIsland.
 *
 * The hook deliberately targets the DynamicIsland background layer only. Notification
 * semantics remain in MiPushIslandHook and unrelated SystemUI notifications are ignored by the
 * state marker check in MiPushIslandVisualState.
 */
class MiPushIslandVisualHook : BaseHook(), ISystemUIPluginHooker {
    private val hookedBackgroundClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val hookedContentClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val visualKeys = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val backdropStates = Collections.synchronizedMap(WeakHashMap<View, BackdropState>())
    private val retryHandler = Handler(Looper.getMainLooper())

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != SYSTEM_UI_PACKAGE || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        scheduleInstall(param.classLoader, attempts = 8)
    }

    /**
     * DynamicIsland implementation classes are loaded by the MIUI plugin ClassLoader, not the
     * SystemUI application ClassLoader. Reuse the plugin dispatch path used by focus hooks so the
     * visual hook reaches the actual background/content classes on HyperOS.
     */
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun hook(classLoader: ClassLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        scheduleInstall(classLoader, attempts = 8)
    }

    override fun onHotReloading() {
        MiPushIslandVisualState.clear()
        visualKeys.clear()
        backdropStates.clear()
    }

    private fun scheduleInstall(classLoader: ClassLoader, attempts: Int) {
        retryHandler.post {
            val installed = runCatching { install(classLoader) }.getOrDefault(false)
            if (!installed && attempts > 0) {
                retryHandler.postDelayed({ scheduleInstall(classLoader, attempts - 1) }, RETRY_DELAY_MS)
            } else if (!installed) {
                XLog.w(TAG, "DynamicIsland visual classes unavailable for loader=$classLoader")
            }
        }
    }

    private fun install(classLoader: ClassLoader): Boolean {
        val backgroundClass = runCatching {
            classLoader.loadClass(BACKGROUND_VIEW_CLASS)
        }.getOrNull() ?: return false
        val contentClass = runCatching {
            classLoader.loadClass(CONTENT_VIEW_CLASS)
        }.getOrNull()

        var installed = false
        synchronized(hookedBackgroundClasses) {
            if (hookedBackgroundClasses.add(backgroundClass)) {
                hookBackground(backgroundClass)
                installed = true
            }
        }
        if (contentClass != null) {
            synchronized(hookedContentClasses) {
                if (hookedContentClasses.add(contentClass)) {
                    hookContent(contentClass)
                    installed = true
                }
            }
        }
        if (installed) {
            XLog.i(TAG, "installed MiPush DynamicIsland visual hooks renderer=mipush")
        }
        return true
    }

    private fun hookBackground(backgroundClass: Class<*>) {
        runCatching {
            backgroundClass.hookMethod("setDrawable", Drawable::class.java) {
                doBefore {
                    val view = thisObject as? View
                    val snapshot = MiPushIslandVisualState.current()
                    if (snapshot == null) {
                        view?.let(::clearVisualBackdrop)
                        return@doBefore
                    }
                    if (!shouldRender(snapshot)) {
                        view?.let(::clearVisualBackdrop)
                        return@doBefore
                    }
                    val drawable = args.firstOrNull() as? Drawable ?: return@doBefore
                    if (drawable is MiPushAccentDrawable) return@doBefore
                    view?.let { visualKeys[it] = snapshot.key }
                    args[0] = MiPushAccentDrawable(
                        delegate = drawable,
                        key = snapshot.key,
                        density = view?.resources?.displayMetrics?.density ?: 1f,
                    )
                }
            }
        }.onFailure {
            XLog.w(TAG, "setDrawable hook unavailable: ${it.message}")
        }

        runCatching {
            backgroundClass.hookAllMethods("onDraw") {
                doAfter {
                    val view = thisObject as? View ?: return@doAfter
                    val canvas = args.firstOrNull() as? Canvas ?: return@doAfter
                    val snapshot = snapshotFor(view)
                    if (snapshot != null && shouldRender(snapshot)) {
                        drawGlassOverlay(view, canvas, snapshot)
                    } else {
                        clearVisualBackdrop(view)
                    }
                }
            }
        }.onFailure {
            XLog.w(TAG, "background overlay hook unavailable: ${it.message}")
        }

        runCatching {
            backgroundClass.hookAllMethods("alphaAnimation") {
                doBefore {
                    val view = thisObject as? View ?: return@doBefore
                    val snapshot = snapshotFor(view) ?: return@doBefore
                    if (!shouldRender(snapshot)) return@doBefore
                    val value = args.firstOrNull() as? Float ?: return@doBefore
                    args[0] = value.coerceIn(0f, 1f)
                }
            }
        }.onFailure {
            XLog.d(TAG, "alpha animation hook skipped: ${it.message}")
        }
    }

    private fun hookContent(contentClass: Class<*>) {
        contentClass.declaredMethods
            .filter { method ->
                method.name == "updateBackgroundBg" &&
                    method.parameterTypes.firstOrNull() == View::class.java
            }
            .forEach { method ->
                runCatching {
                    method.hook {
                        doAfter {
                            val view = args.firstOrNull() as? View ?: return@doAfter
                            val snapshot = snapshotFor(view)
                            if (snapshot != null && shouldRender(snapshot)) {
                                applyBackdrop(view, snapshot)
                            } else {
                                clearVisualBackdrop(view)
                            }
                        }
                    }
                }.onFailure {
                    XLog.d(TAG, "background update hook skipped: ${it.message}")
                }
            }
    }

    private fun shouldRender(snapshot: IslandVisualSnapshot): Boolean {
        return MiPushIslandVisualState.isRenderable(snapshot)
    }

    private fun snapshotFor(view: View): IslandVisualSnapshot? {
        val key = visualKeys[view]
        return key?.let(MiPushIslandVisualState::snapshotForKey)
    }

    private fun applyBackdrop(view: View, snapshot: IslandVisualSnapshot) {
        val color = parseColor(snapshot.accentColor)
        val radius = view.resources.displayMetrics.density * 32f
        val previous = backdropStates[view]
        val original = if (previous != null && view.background === previous.applied) {
            previous.original
        } else {
            view.background
        }
        val applied = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(82, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(34, Color.red(color), Color.green(color), Color.blue(color)),
            ),
        ).apply { cornerRadius = radius }
        backdropStates[view] = BackdropState(original = original, applied = applied)
        view.background = applied
        MiPushBlurCompat.apply(view, snapshot)
        view.invalidate()
    }

    private fun clearVisualBackdrop(view: View) {
        val hadVisualState = visualKeys.remove(view) != null || backdropStates.containsKey(view)
        if (!hadVisualState) return
        val state = backdropStates.remove(view)
        if (state != null && view.background === state.applied) {
            view.background = state.original
        }
        MiPushBlurCompat.clear(view)
    }

    private fun drawGlassOverlay(view: View, canvas: Canvas, snapshot: IslandVisualSnapshot) {
        val bounds = Rect(0, 0, view.width, view.height)
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val color = parseColor(snapshot.accentColor)
        val radius = (bounds.height() * 0.5f).coerceAtLeast(12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = view.resources.displayMetrics.density *
            GLASS_STROKE_WIDTH
        paint.shader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            Color.argb(
                GLASS_HIGHLIGHT_ALPHA,
                255,
                255,
                255,
            ),
            Color.argb(GLASS_ACCENT_ALPHA, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            bounds.left.toFloat() + paint.strokeWidth,
            bounds.top.toFloat() + paint.strokeWidth,
            bounds.right.toFloat() - paint.strokeWidth,
            bounds.bottom.toFloat() - paint.strokeWidth,
            radius,
            radius,
            paint,
        )
        if (shouldRender(snapshot)) {
            paint.shader = null
            paint.strokeWidth = view.resources.displayMetrics.density * OUTER_GLOW_STROKE_WIDTH
            paint.color = Color.argb(OUTER_GLOW_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRoundRect(
                bounds.left.toFloat() - paint.strokeWidth,
                bounds.top.toFloat() - paint.strokeWidth,
                bounds.right.toFloat() + paint.strokeWidth,
                bounds.bottom.toFloat() + paint.strokeWidth,
                radius + paint.strokeWidth,
                radius + paint.strokeWidth,
                paint,
            )
        }
        if (view.isAttachedToWindow &&
            view.isShown &&
            view.alpha > MIN_VISIBLE_ALPHA
        ) {
            view.postInvalidateDelayed(ANIMATION_FRAME_DELAY_MS)
        }
    }

    private fun parseColor(raw: String): Int = runCatching {
        Color.parseColor(raw)
    }.getOrDefault(Color.rgb(0, 200, 255))

    private companion object {
        private const val TAG = "MiPushIslandVisualHook"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val BACKGROUND_VIEW_CLASS =
            "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
        private const val CONTENT_VIEW_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
        private const val RETRY_DELAY_MS = 350L
        private const val ANIMATION_FRAME_DELAY_MS = 42L
    }

    private data class BackdropState(
        val original: Drawable?,
        val applied: Drawable,
    )
}

private class MiPushAccentDrawable(
    private val delegate: Drawable,
    private val key: String,
    private val density: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        delegate.bounds = bounds
        delegate.draw(canvas)
        val snapshot = MiPushIslandVisualState.snapshotForKey(key) ?: return
        if (!MiPushIslandVisualState.isRenderable(snapshot)) return
        val color = runCatching { Color.parseColor(snapshot.accentColor) }
            .getOrDefault(Color.rgb(0, 200, 255))
        val radius = bounds.height() * 0.5f
        val phase = (android.os.SystemClock.uptimeMillis() % 2400L).toFloat() / 2400f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * ACCENT_STROKE_WIDTH
        paint.shader = LinearGradient(
            bounds.left.toFloat() + bounds.width() * phase,
            bounds.top.toFloat(),
            bounds.right.toFloat() - bounds.width() * phase,
            bounds.bottom.toFloat(),
            Color.argb(ACCENT_HIGHLIGHT_ALPHA, 255, 255, 255),
            Color.argb(ACCENT_COLOR_ALPHA, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.MIRROR,
        )
        canvas.drawRoundRect(RectF(bounds), radius, radius, paint)
        paint.shader = null
        paint.color = Color.argb(ACCENT_GLOW_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
        paint.strokeWidth = density * ACCENT_GLOW_STROKE_WIDTH
        canvas.drawRoundRect(
            bounds.left.toFloat() - density,
            bounds.top.toFloat() - density,
            bounds.right.toFloat() + density,
            bounds.bottom.toFloat() + density,
            radius + density,
            radius + density,
            paint,
        )
    }

    override fun setAlpha(alpha: Int) {
        delegate.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        delegate.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by SystemUI")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = delegate.intrinsicWidth
    override fun getIntrinsicHeight(): Int = delegate.intrinsicHeight

    override fun onBoundsChange(bounds: Rect) {
        delegate.bounds = bounds
    }
}

private object MiPushBlurCompat {
    fun apply(view: View, snapshot: IslandVisualSnapshot) {
        val classLoader = view.javaClass.classLoader ?: return
        runCatching {
            val blurClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
                .firstOrNull() ?: return
            val setMode = blurClass.getDeclaredMethod(
                "setMiViewBlurModeCompat",
                View::class.java,
                Int::class.javaPrimitiveType,
            )
            setMode.invoke(null, view, 1)
        }.onFailure {
            XLog.d("MiPushIslandVisualHook", "MiBlurCompat unavailable: ${it.message}")
        }
    }

    fun clear(view: View) {
        val classLoader = view.javaClass.classLoader ?: return
        runCatching {
            val blurClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
                .firstOrNull() ?: return
            blurClass.getDeclaredMethod(
                "setMiViewBlurModeCompat",
                View::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(null, view, 0)
            blurClass.getDeclaredMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                .invoke(null, view)
        }.onFailure {
            XLog.d("MiPushIslandVisualHook", "MiBlurCompat clear unavailable: ${it.message}")
        }
    }
}
