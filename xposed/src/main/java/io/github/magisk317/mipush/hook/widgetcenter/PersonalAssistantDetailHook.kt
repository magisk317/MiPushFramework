package io.github.magisk317.mipush.hook.widgetcenter

import android.graphics.drawable.Drawable
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.hookMethod

/**
 * 小部件中心明细页的白名单绕过：点击应用后 `POST component/store/impl/tail`
 * 对未审核包返回 api=32007（impl have no adapter version content）。
 *
 * v2 教训（实测日志 + 反编译确认）：32007 不是"空数据成功返回"，而是一条
 * 独立的失败渲染路径——PickerDetailRepository 捕获 ApiException 后把 code=32007
 * 的空 wrapper 发给 ViewModel，Fragment.dataLoadingStatus 收到 a$b(32007) 后
 * 直接 appDoUnableInMarket(false) 渲染"正在努力适配中"，全程不经过
 * requestPickerDetailDataSuccess / setMPickerDetailData / updateShow。
 * v2 只 hook 了这三个成功路径方法，所以永远不会触发。
 *
 * v3 改在数据流上游下手：
 * 1. hook loadDataFromIntent：记录当前明细页是否属于本应用（列表点击时
 *    PickerAppListFragment.onItemClick 把 appPackage 写进 arguments）；
 * 2. 主路径 hook getPickerDetailList：本应用的明细请求直接把本地构造的
 *    PickerDetailResponseWrapper 预填进 ViewModel.mPickerDetailData，
 *    原生代码读到非空缓存后自己走 requestPickerDetailDataSuccess(cached, true)
 *    （填 mDataList + downloadManager.initData + 发 a$d 成功状态），
 *    网络请求不再发生，下游渲染全部走原生成功链路；
 * 3. 兜底 hook dataLoadingStatus：万一 32007/32006 失败状态仍到达 Fragment
 *    （其它触发入口或判断失效），在 appDoUnableInMarket 之前改写为
 *    a$d(true) 并手工填充 VM 数据；
 * 4. 保留成功路径的空 wrapper 替换，作为服务器异常返回时的最后兜底。
 */
class PersonalAssistantDetailHook : BaseHook() {

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != PersonalAssistantPickerHook.PA_PACKAGE_NAME) return
        val classLoader = param.classLoader
        runCatching {
            val fragment = findHookClass(FRAGMENT_CLASS, classLoader)
            val viewModel = findHookClass(VIEW_MODEL_CLASS, classLoader)
            val wrapperClass = findHookClass(WRAPPER_CLASS, classLoader)
            val stateClass = findHookClass(STATE_CLASS, classLoader)
            val stateFailClass = findHookClass(STATE_FAIL_CLASS, classLoader)
            val stateSuccessClass = findHookClass(STATE_SUCCESS_CLASS, classLoader)

            // 1) 当前详情页是否属于本应用
            fragment.hookMethod("loadDataFromIntent", android.os.Bundle::class.java) {
                doAfter {
                    val bundle = args.firstOrNull() as? android.os.Bundle ?: return@doAfter
                    val mine = runCatching { bundle.toString().contains(MIPUSH_PACKAGE) }.getOrDefault(false)
                    detailIsMipush = mine
                    XLog.i(TAG, "loadDataFromIntent: isMipush=$mine")
                }
            }

            // 2) 主路径：预填本地 wrapper，让原生缓存分支接管
            viewModel.hookMethod("getPickerDetailList", android.os.Bundle::class.java) {
                doBefore {
                    if (!isOurDetailPage(args.firstOrNull())) return@doBefore
                    val vm = thisObject ?: return@doBefore
                    runCatching {
                        val filled = buildWrapper(classLoader, PersonalAssistantPickerHook.currentApplicationIcon())
                        vm.setFieldOf("mPickerDetailData", filled)
                        XLog.i(TAG, "getPickerDetailList: local wrapper seeded, native cache path takes over")
                    }.onFailure { XLog.e(TAG, "seed local detail wrapper failed", it) }
                }
            }

            // 3) 兜底：失败状态到达渲染前改写为成功
            fragment.hookMethod("dataLoadingStatus", stateClass) {
                doBefore {
                    if (!detailIsMipush) return@doBefore
                    val state = args.firstOrNull() ?: return@doBefore
                    if (state.javaClass != stateFailClass) return@doBefore
                    val code = runCatching {
                        state.javaClass.declaredFields
                            .firstOrNull { it.type == java.lang.Integer.TYPE }
                            ?.apply { isAccessible = true }
                            ?.getInt(state)
                    }.getOrNull()
                    if (code != 32007 && code != 32006) return@doBefore
                    val vm = runCatching {
                        thisObject?.javaClass?.getMethod("getMPickerDetailViewModel\$app_release")
                            ?.invoke(thisObject)
                    }.getOrNull() ?: return@doBefore
                    runCatching {
                        val filled = buildWrapper(classLoader, PersonalAssistantPickerHook.currentApplicationIcon())
                        vm.setFieldOf("mPickerDetailData", filled)
                        (vm.fieldOf("mDataList") as? MutableList<Any?>)?.let { data ->
                            data.clear()
                            (filled.fieldOf("pickerDetailResponses") as? List<Any?>)?.let { data.addAll(it) }
                        }
                        @Suppress("UNCHECKED_CAST")
                        (args as Array<Any?>)[0] = stateSuccessClass.getDeclaredConstructor(java.lang.Boolean.TYPE)
                            .apply { isAccessible = true }
                            .newInstance(true)
                        XLog.i(TAG, "dataLoadingStatus: rewrote api=$code into local success state")
                    }.onFailure { XLog.e(TAG, "rewrite failure state failed", it) }
                }
            }

            // 4) 成功路径兜底：服务器异常返回空 wrapper 时替换为本地数据
            viewModel.hookMethod("setMPickerDetailData", wrapperClass) {
                doBefore { replaceIfOurs(args, classLoader, "setMPickerDetailData") }
            }
            viewModel.hookMethod("requestPickerDetailDataSuccess", wrapperClass, java.lang.Boolean.TYPE) {
                doBefore { replaceIfOurs(args, classLoader, "requestPickerDetailDataSuccess") }
            }
            fragment.hookMethod("updateShow", wrapperClass) {
                doBefore { replaceIfOurs(args, classLoader, "updateShow") }
            }

            // 6) mock 预览图：无网络预览 URL 时，在本进程内用本应用 widget 真实
            //    布局 + mock 假数据渲染成 bitmap 填进预览位，并置加载成功标志，
            //    让"添加"链路的预览检查自然通过。
            runCatching {
                val containerClass = findHookClass(CONTAINER_CLASS, classLoader)
                val resourcesClass = findHookClass(DETAIL_RESOURCES_CLASS, classLoader)
                val responseClass = findHookClass(RESPONSE_CLASS, classLoader)
                containerClass.hookMethod(
                    "showPreviewImage",
                    String::class.java,
                    java.lang.Boolean.TYPE,
                    Integer.TYPE,
                    Integer.TYPE,
                    responseClass,
                    resourcesClass,
                ) {
                    doAfter { renderMockPreviewIfNeeded(thisObject, args) }
                }
            }.onFailure { XLog.e(TAG, "install mock-preview hook failed", it) }

            // 6b) Glide 失败占位会覆盖 mock 预览，在 setImageDrawable 里拦截恢复
            runCatching {
                val imageViewClass = findHookClass(OBSERVE_IMAGE_VIEW_CLASS, classLoader)
                imageViewClass.hookMethod("setImageDrawable", android.graphics.drawable.Drawable::class.java) {
                    doAfter { restoreMockPreviewIfNeeded(thisObject as? android.widget.ImageView ?: return@doAfter) }
                }
            }.onFailure { XLog.e(TAG, "install imageview restore hook failed", it) }

            // EXP-D: 诊断负一屏 RemoteViews 渲染失败——apply 抛异常时 doAfter 不会执行，
            // 对比 called/OK 日志即可定位失败的布局
            runCatching {
                val rvClass = findHookClass("android.widget.RemoteViews", classLoader)
                rvClass.hookMethod("apply", android.content.Context::class.java, android.view.ViewGroup::class.java) {
                    doBefore { XLog.i(TAG, "RV.apply called pkg=${(thisObject?.fieldOf("mApplication") as? android.content.pm.ApplicationInfo)?.packageName}") }
                    doAfter { XLog.i(TAG, "RV.apply OK") }
                }
            }.onFailure { XLog.i(TAG, "EXP-D: apply(2) hook unavailable: ${it.message}") }
            runCatching {
                val rvClass = findHookClass("android.widget.RemoteViews", classLoader)
                val sizeF = findHookClass("android.util.SizeF", classLoader)
                rvClass.hookMethod("apply", android.content.Context::class.java, android.view.ViewGroup::class.java, sizeF) {
                    doBefore { XLog.i(TAG, "RV.apply3 called") }
                    doAfter { XLog.i(TAG, "RV.apply3 OK") }
                }
            }.onFailure { XLog.i(TAG, "EXP-D: apply(3) hook unavailable: ${it.message}") }
            runCatching {
                val rvClass = findHookClass("android.widget.RemoteViews", classLoader)
                rvClass.hookMethod("reapply", android.content.Context::class.java, android.view.View::class.java) {
                    doBefore { XLog.i(TAG, "RV.reapply called") }
                    doAfter { XLog.i(TAG, "RV.reapply OK") }
                }
            }.onFailure { XLog.i(TAG, "EXP-D: reapply(2) hook unavailable: ${it.message}") }
            runCatching {
                val rvClass = findHookClass("android.widget.RemoteViews", classLoader)
                val sizeF = findHookClass("android.util.SizeF", classLoader)
                rvClass.hookMethod("reapply", android.content.Context::class.java, android.view.View::class.java, sizeF) {
                    doBefore { XLog.i(TAG, "RV.reapply3 called") }
                    doAfter { XLog.i(TAG, "RV.reapply3 OK") }
                }
            }.onFailure { XLog.i(TAG, "EXP-D: reapply(3) hook unavailable: ${it.message}") }

            // 5) "添加"前置检查放行：无网络预览图时 Glide 必然加载失败，
            //    DragHelper.b 会以"预览加载失败"拒绝添加。本应用的详情页
            //    强制把加载标志置为成功（拖拽影子用占位 bitmap，无碍功能）。
            runCatching {
                val dragHelper = findHookClass(DRAG_HELPER_CLASS, classLoader)
                dragHelper.hookMethod("b", android.content.Context::class.java, android.view.View::class.java) {
                    doBefore {
                        if (!detailIsMipush) return@doBefore
                        val view = args.getOrNull(1) ?: return@doBefore
                        if (view.javaClass.name != OBSERVE_IMAGE_VIEW_CLASS) return@doBefore
                        runCatching {
                            view.javaClass.getMethod("setGlideResourceLoadSuccess", java.lang.Boolean.TYPE)
                                .invoke(view, true)
                            XLog.i(TAG, "drag check: preview forced load-success")
                        }.onFailure { XLog.e(TAG, "force preview success failed", it) }
                    }
                }
            }.onFailure { XLog.e(TAG, "install drag-check hook failed", it) }
            XLog.i(TAG, "picker detail hooks installed")
        }.onFailure {
            XLog.e(TAG, "install detail hook failed", it)
        }
    }

    private fun isOurDetailPage(bundleArg: Any?): Boolean {
        if (detailIsMipush) return true
        val bundle = bundleArg as? android.os.Bundle ?: return false
        return runCatching { bundle.toString().contains(MIPUSH_PACKAGE) }.getOrDefault(false)
    }

    private fun renderMockPreviewIfNeeded(container: Any?, args: Array<out Any?>) {
        XLog.i(
            TAG,
            "mock-preview fired: url=${args.firstOrNull()} isMipush=$detailIsMipush " +
                "argCount=${args.size} arg4Class=${args.getOrNull(4)?.javaClass?.name} " +
                "container=${container?.javaClass?.name}",
        )
        if (!detailIsMipush) return
        val url = args.firstOrNull() as? String ?: return
        if (url.isNotEmpty()) return
        if (container == null) return
        val spanType = runCatching {
            container.javaClass.declaredMethods.firstOrNull { it.name == "currentSpanType" }
                ?.apply { isAccessible = true }?.invoke(container) as? Int
        }.getOrNull() ?: run {
            XLog.w(TAG, "mock-preview: currentSpanType reflection failed")
            return
        }
        val imageView = container.fieldOf("mImageView") as? android.widget.ImageView ?: run {
            XLog.w(TAG, "mock-preview: mImageView not found")
            return
        }
        val bitmap = renderMockPreview(imageView.context, spanType == 4, imageView.width, imageView.height)
        if (bitmap != null) {
            synchronized(mockBitmaps) { mockBitmaps[imageView] = bitmap }
            imageView.setImageBitmap(bitmap)
            runCatching {
                imageView.javaClass.getMethod("setGlideResourceLoadSuccess", java.lang.Boolean.TYPE)
                    .invoke(imageView, true)
            }
            // Glide 空 URL 会立即失败把占位 drawable 盖回来；且首次渲染时容器可能
            // 还没 layout（默认尺寸），延迟用实际尺寸重渲染并重设两次保证终态
            imageView.postDelayed({
                val fitted = renderMockPreview(
                    imageView.context, spanType == 4, imageView.width, imageView.height,
                ) ?: bitmap
                imageView.setImageBitmap(fitted)
                XLog.i(TAG, "mock preview re-applied (600ms) ${fitted.width}x${fitted.height}")
            }, 600)
            imageView.postDelayed({
                val fitted = renderMockPreview(
                    imageView.context, spanType == 4, imageView.width, imageView.height,
                ) ?: bitmap
                imageView.setImageBitmap(fitted)
                XLog.i(TAG, "mock preview re-applied (1500ms)")
            }, 1500)
            XLog.i(TAG, "mock preview rendered for spanType=$spanType")
        } else {
            XLog.w(TAG, "mock preview render failed for spanType=$spanType")
        }
    }

    /** Glide 失败占位（null BitmapDrawable）覆盖 mock 预览时恢复。 */
    private fun restoreMockPreviewIfNeeded(view: android.widget.ImageView) {
        if (!detailIsMipush) return
        val drawable = view.drawable ?: return
        val hasContent = runCatching {
            drawable !is android.graphics.drawable.BitmapDrawable || drawable.bitmap != null
        }.getOrDefault(true)
        if (hasContent) return
        val mock = synchronized(mockBitmaps) { mockBitmaps[view] } ?: return
        runCatching {
            view.setImageBitmap(mock)
            view.javaClass.getMethod("setGlideResourceLoadSuccess", java.lang.Boolean.TYPE)
                .invoke(view, true)
            XLog.i(TAG, "mock preview restored over placeholder")
        }
    }

    private fun renderMockPreview(
        context: android.content.Context,
        isRecent: Boolean,
        widthHint: Int,
        heightHint: Int,
    ): android.graphics.Bitmap? = runCatching {
        val pkgContext = context.createPackageContext(
            MIPUSH_PACKAGE,
            android.content.Context.CONTEXT_IGNORE_SECURITY,
        )
        val res = pkgContext.resources
        fun id(name: String, type: String): Int = res.getIdentifier(name, type, MIPUSH_PACKAGE)
        val layoutRes = id(
            if (isRecent) "mipush_widget_recent_events" else "mipush_widget_connection_status",
            "layout",
        )
        val view = android.view.LayoutInflater.from(pkgContext).inflate(layoutRes, null, false)

        fun setText(name: String, value: String) {
            (view.findViewById(id(name, "id")) as? android.widget.TextView)?.text = value
        }

        if (isRecent) {
            setText("widget_updated_at", "刚刚更新")
            val tertiary = res.getColor(id("widget_text_tertiary", "color"))
            val rows = listOf(
                MockRow("微信", "您收到一条新消息", "com.tencent.mm", "14:22"),
                MockRow("邮件", "服务器心跳正常", "com.android.email", "14:20"),
                MockRow("短信", "验证码已转发到手机", "com.android.mms", "14:18"),
                MockRow("系统", "长连接已建立", "com.android.settings", "14:15"),
            )
            rows.forEachIndexed { index, mockRow ->
                val rowView = view.findViewById<android.view.View>(id("widget_row_$index", "id")) ?: return@forEachIndexed
                rowView.visibility = android.view.View.VISIBLE
                (rowView.findViewById(id("row_title_$index", "id")) as? android.widget.TextView)?.text =
                    "${mockRow.title} · ${mockRow.body}"
                (rowView.findViewById(id("row_meta_$index", "id")) as? android.widget.TextView)?.apply {
                    text = mockRow.time
                    setTextColor(tertiary)
                }
                (rowView.findViewById(id("row_icon_$index", "id")) as? android.widget.ImageView)?.let { iconView ->
                    runCatching { pkgContext.packageManager.getApplicationIcon(mockRow.pkg) }
                        .getOrNull()?.let { iconView.setImageDrawable(it) }
                }
            }
        } else {
            setText("widget_status_value", "已连接")
            setText("widget_status_detail", "推送服务运行正常")
            setText("widget_messages_value", "今日 42 条")
            setText("widget_server_value", "s.example.push.net:5223")
            setText("widget_updated_at", "刚刚更新")
            (view.findViewById(id("widget_duration_value", "id")) as? android.widget.TextView)?.text = "02:35:17"
            (view.findViewById(id("widget_status_dot", "id")) as? android.widget.ImageView)
                ?.setImageResource(id("mipush_widget_dot_connected", "drawable"))
        }

        val width = if (widthHint > 0) widthHint else 1018
        val heightHint0 = if (heightHint > 0) heightHint else if (isRecent) 980 else 473
        // 内容超高时按比例缩小字号与 padding（最多 3 轮），保证 1:1 无压缩全显示
        fun measureContent(): Int {
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            )
            return view.measuredHeight
        }
        var contentHeight = measureContent()
        if (contentHeight > heightHint0) {
            var scale = heightHint0.toFloat() / contentHeight
            repeat(3) {
                shrinkTextAndPadding(view, scale)
                val after = measureContent()
                if (after <= heightHint0) return@repeat
                scale = heightHint0.toFloat() / after
            }
            contentHeight = measureContent()
        }
        val height = heightHint0
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        bitmap
    }.onFailure { XLog.e(TAG, "renderMockPreview error", it) }.getOrNull()

    private fun shrinkTextAndPadding(view: android.view.View, scale: Float) {
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) shrinkTextAndPadding(view.getChildAt(i), scale)
        }
        if (view is android.widget.TextView) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, view.textSize * scale)
        }
        view.setPadding(
            (view.paddingLeft * scale).toInt(),
            (view.paddingTop * scale).toInt(),
            (view.paddingRight * scale).toInt(),
            (view.paddingBottom * scale).toInt(),
        )
    }

    private data class MockRow(val title: String, val body: String, val pkg: String, val time: String)

    private fun replaceIfOurs(args: Array<out Any?>, classLoader: ClassLoader, origin: String) {
        if (!detailIsMipush) return
        val wrapper = args.firstOrNull() ?: return
        val responses = wrapper.fieldOf("pickerDetailResponses") as? List<*>
        if (responses?.isNotEmpty() == true) return
        val icon = PersonalAssistantPickerHook.currentApplicationIcon()
        runCatching {
            val filled = buildWrapper(classLoader, icon)
            @Suppress("UNCHECKED_CAST")
            (args as Array<Any?>)[0] = filled
            XLog.i(TAG, "injected 2 native widget impls via $origin")
        }.onFailure { XLog.e(TAG, "inject detail failed via $origin", it) }
    }

    private fun buildWrapper(classLoader: ClassLoader, icon: Drawable?): Any {
        val responseClass = findHookClass(RESPONSE_CLASS, classLoader)
        val widgetClass = findHookClass(WIDGET_CLASS, classLoader)
        val tipClass = findHookClass(TIP_CLASS, classLoader)
        val wrapperClass = findHookClass(WRAPPER_CLASS, classLoader)

        fun widget(provider: String, title: String, desc: String, width: Int, height: Int): Any {
            val ctor = widgetClass.getDeclaredConstructor(
                String::class.java,        // widgetProviderName
                String::class.java,        // widgetTitle
                String::class.java,        // desc
                String::class.java,        // lightPreviewUrl
                String::class.java,        // darkPreviewUrl
                Integer.TYPE,              // sort
                Integer.TYPE,              // appWidgetWidth
                Integer.TYPE,              // appWidgetHeight
                List::class.java,          // editConfig
                Integer.TYPE,              // installStatus
                java.lang.Boolean.TYPE,    // isIndependentProcessWidget
                android.os.Bundle::class.java,    // customEditOptions
            ).apply { isAccessible = true }
            return ctor.newInstance(provider, title, desc, "", "", 0, width, height, null, 0, false, null)
        }

        fun response(widget: Any, uniqueCode: String, originStyle: Int): Any {
            val ctor = responseClass.getDeclaredConstructor(
                String::class.java,               // implUniqueCode
                String::class.java,               // abilityCode
                Integer.TYPE,                     // implType (1 = APP_WIDGET)
                Integer.TYPE,                     // style
                Integer.TYPE,                     // originStyle
                String::class.java,               // appPackage
                String::class.java,               // appIcon
                String::class.java,               // appName
                String::class.java,               // appInstalledVersionName
                Integer.TYPE,                     // appVersionCode
                String::class.java,               // appDownloadUrl
                String::class.java,               // appVersionName
                String::class.java,               // appPublisherName
                String::class.java,               // appPermissionUrl
                String::class.java,               // appPrivacyUrl
                tipClass,                         // installInfo
                findHookClass(SUPPER_APP_CLASS, classLoader), // supperAppInfo
                widgetClass,                      // widgetImplInfo
                findHookClass(MAML_CLASS, classLoader),       // mamlImplInfo
                java.lang.Long::class.java,       // priceInCent
                findHookClass(DRM_CLASS, classLoader),        // drmResult
            ).apply { isAccessible = true }
            val tip = tipClass.getDeclaredConstructor(
                String::class.java, String::class.java,
            ).apply { isAccessible = true }.newInstance("", "")
            return ctor.newInstance(
                uniqueCode,
                "",
                1,
                0,
                originStyle,
                MIPUSH_PACKAGE,
                "",
                "MiPush",
                "1.0.4",
                1818,
                "",
                "1.0.4",
                "",
                "",
                "",
                tip,
                null,
                widget,
                null,
                null,
                null,
            )
        }

        val responses = listOf(
            response(
                widget(
                    "io.github.magisk317.mipush.app.widget.ConnectionStatusWidgetProvider",
                    "连接状态", "MiPush 连接状态小部件", 4, 2,
                ),
                "wd_mipush_connection_status",
                2,
            ),
            response(
                widget(
                    "io.github.magisk317.mipush.app.widget.RecentEventsWidgetProvider",
                    "推送记录", "MiPush 推送记录小部件", 4, 4,
                ),
                "wd_mipush_recent_events",
                4,
            ),
        )

        val tip = tipClass.getDeclaredConstructor(
            String::class.java, String::class.java,
        ).apply { isAccessible = true }.newInstance("", "")
        val wrapperCtor = wrapperClass.getDeclaredConstructor(
            List::class.java,                 // pickerDetailResponses
            String::class.java,               // appPackage
            String::class.java,               // appIcon
            String::class.java,               // appName
            Integer.TYPE,                     // appVersionCode
            String::class.java,               // appVersionName
            String::class.java,               // appDownloadUrl
            String::class.java,               // appPublisherName
            String::class.java,               // appPermissionUrl
            String::class.java,               // appPrivacyUrl
            tipClass,                         // installInfo
            Class.forName("java.util.ArrayList"), // mamlsNeedDownload
            java.lang.Boolean.TYPE,           // z8
            Integer.TYPE,                     // i11
            Drawable::class.java,             // localAppIcon
            java.lang.Boolean.TYPE,           // useAppIcon
            java.lang.Long::class.java,       // priceInCent
            java.lang.Boolean.TYPE,           // z11
            java.lang.Boolean.TYPE,           // z12
        ).apply { isAccessible = true }
        return wrapperCtor.newInstance(responses, MIPUSH_PACKAGE, "", "MiPush", 1818, "1.0.4", "", "", "", "", tip, ArrayList<Any>(), false, 0, icon, false, null, false, false)
    }

    companion object {
        @Volatile
        private var detailIsMipush: Boolean = false

        private const val TAG = "PersonalAssistantDetailHook"
        private const val FRAGMENT_CLASS =
            "com.miui.personalassistant.picker.business.detail.PickerDetailFragment"
        private const val VIEW_MODEL_CLASS =
            "com.miui.personalassistant.picker.business.detail.PickerDetailViewModel"
        private const val WRAPPER_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWrapper"
        private const val RESPONSE_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponse"
        private const val WIDGET_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWidget"
        private const val TIP_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailTipStr"
        private const val SUPPER_APP_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailSupperAppInfo"
        private const val MAML_CLASS =
            "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseMaml"
        private const val DRM_CLASS = "miui.drm.DrmManager\$DrmResult"
        private const val STATE_CLASS =
            "com.miui.personalassistant.base.viewmodel.a"
        private const val STATE_FAIL_CLASS =
            "com.miui.personalassistant.base.viewmodel.a\$b"
        private const val STATE_SUCCESS_CLASS =
            "com.miui.personalassistant.base.viewmodel.a\$d"
        private const val DRAG_HELPER_CLASS = "d9.b"
        private const val OBSERVE_IMAGE_VIEW_CLASS =
            "com.miui.personalassistant.image.ObserveGlideLoadStatusImageView"
        private const val CONTAINER_CLASS =
            "com.miui.personalassistant.picker.business.detail.widget.PickerItemContainer"
        private const val DETAIL_RESOURCES_CLASS =
            "com.miui.personalassistant.picker.business.detail.utils.PickerDetailResources"
        private const val MIPUSH_PACKAGE = PersonalAssistantPickerHook.MIPUSH_PACKAGE

        /** 渲染好的 mock 预览，key 为预览 ImageView（弱引用，随条目回收）。 */
        private val mockBitmaps = java.util.WeakHashMap<android.widget.ImageView, android.graphics.Bitmap>()
    }
}
