package io.github.magisk317.mipush.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MipushManifestContractTest {

    @Test
    fun `application entrypoint uses canonical class name`() {
        val application = parseManifest().getElementsByTagName("application").item(0)
        val applicationName = application.attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
        val description = application.attributes.getNamedItemNS(ANDROID_NS, "description").nodeValue

        assertEquals("io.github.magisk317.mipush.app.App", applicationName)
        assertEquals("@string/xposedDescription", description)
        assertTrue(resolveFile("src/main/java/io/github/magisk317/mipush/app/App.kt").isFile)
    }

    @Test
    fun `desktop entry targets MainActivity and first-start wizard stays in MainActivity`() {
        val document = parseManifest()
        val activities = document.getElementsByTagName("activity")
        val activityNames = (0 until activities.length)
            .mapNotNull { index ->
                activities.item(index).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
            }
            .toSet()

        assertTrue("io.github.magisk317.mipush.feature.main.MainActivity" in activityNames)
        assertTrue("io.github.magisk317.mipush.feature.wizard.RequestPermissionPage" in activityNames)
        assertFalse("io.github.magisk317.mipush.app.ManagerLauncherActivity" in activityNames)
        assertFalse("io.github.magisk317.mipush.feature.wizard.WelcomeActivity" in activityNames)

        val aliases = document.getElementsByTagName("activity-alias")
        val desktopAliases = (0 until aliases.length)
            .map { aliases.item(it) as Element }
            .filter { alias ->
                alias.attributes.getNamedItemNS(ANDROID_NS, "targetActivity")?.nodeValue ==
                    "io.github.magisk317.mipush.feature.main.MainActivity"
            }
        assertTrue(
            desktopAliases.any { alias ->
                val filters = alias.getElementsByTagName("intent-filter")
                (0 until filters.length).any { index ->
                    val filter = filters.item(index) as Element
                    val actions = filter.getElementsByTagName("action")
                    val categories = filter.getElementsByTagName("category")
                    (0 until actions.length).any {
                        actions.item(it).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue ==
                            "android.intent.action.MAIN"
                    } && (0 until categories.length).any {
                        categories.item(it).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue ==
                            "android.intent.category.LAUNCHER"
                    }
                }
            },
            "The desktop alias must target MainActivity directly",
        )

        val mainActivitySource = resolveProjectFile(
            "manager/ui/src/main/java/io/github/magisk317/mipush/feature/main/MainActivity.kt",
        ).readText()
        assertTrue("preferenceRepository.showWizard.first()" in mainActivitySource)
        assertFalse("LEGACY_TARGET_CLASS" in mainActivitySource)
    }

    @Test
    fun `manager activities are hosted in mipush package`() {
        val names = parseManifest().getElementsByTagName("activity").let { activities ->
            (0 until activities.length).mapNotNull { index ->
                activities.item(index).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
            }.toSet()
        }
        assertTrue("io.github.magisk317.mipush.feature.main.MainActivity" in names)
        assertTrue("io.github.magisk317.mipush.feature.main.ApplicationInfoPage" in names)
        assertTrue("io.github.magisk317.mipush.feature.wizard.RequestPermissionPage" in names)
    }

    @Test
    fun `manager app name is localized separately`() {
        assertEquals("MiPush Manager", readStringResource("src/main/res/values/strings.xml", "app_name"))
        assertEquals("MiPush 管理器", readStringResource("src/main/res/values-zh/strings.xml", "app_name"))
    }

    @Test
    fun `legacy xposed manifest metadata is removed`() {
        val document = parseManifest()
        val application = document.getElementsByTagName("application").item(0)
        val metaData = application.childNodes
        val names = buildSet {
            for (index in 0 until metaData.length) {
                val node = metaData.item(index)
                if (node.nodeName == "meta-data") {
                    val name = node.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
                    if (!name.isNullOrBlank()) add(name)
                }
            }
        }

        assertFalse("xposedmodule" in names)
        assertFalse("xposedminversion" in names)
        assertFalse("xposeddescription" in names)
        assertFalse("xposedscope" in names)
        assertFalse(resolveFile("src/main/assets/xposed_init").exists())
    }

    @Test
    fun `libxposed entrypoint hot reload and required scope remain declared`() {
        assertEquals(
            "io.github.magisk317.mipush.hook.LibXposedEntry",
            resolveProjectFile("xposed/src/main/resources/META-INF/xposed/java_init.list").readText().trim(),
        )
        val moduleProps = resolveProjectFile("xposed/src/main/resources/META-INF/xposed/module.prop").readText()
        assertTrue("minApiVersion=102" in moduleProps)
        assertTrue("targetApiVersion=102" in moduleProps)
        assertTrue("autoHotReload=true" in moduleProps)
        assertFalse(moduleProps.lineSequence().map(String::trim).any { it.startsWith("staticScope=") })

        val baseEntrySource = resolveProjectFile(
            "magisk-xposed-kit/src/main/java/io/github/magisk317/xposed/BaseLibXposedEntry.kt",
        ).readText()
        assertTrue("HotReloadingParam" in baseEntrySource)
        assertTrue("HotReloadedParam" in baseEntrySource)
        assertTrue("param.setSavedInstanceState(createHotReloadState())" in baseEntrySource)
        assertTrue("cleanupForHotReload()" in baseEntrySource)
        assertTrue("putStringArrayList(STATE_LOADED_PACKAGES" in baseEntrySource)
        assertTrue("val oldHookHandles = param.oldHookHandles" in baseEntrySource)
        assertTrue("hookApi.beginHotReload(oldHookHandles)" in baseEntrySource)
        assertTrue("restoreHotReloadState(param.savedInstanceState, oldHookHandles)" in baseEntrySource)
        assertTrue("resolveCurrentProcessTargets(param, oldHookHandles)" in baseEntrySource)
        assertTrue("handle.executable.declaringClass.classLoader" in baseEntrySource)
        assertTrue("canLoadSystemServerHooks" in baseEntrySource)
        assertTrue("SYSTEM_SERVER_SENTINEL_CLASSES" in baseEntrySource)
        assertTrue("hookApi.finishHotReload()" in baseEntrySource)
        assertTrue("hookApi.abortHotReload()" in baseEntrySource)
        assertTrue("packages[packageName] as? WeakReference<*>" in baseEntrySource)
        assertFalse("setSavedInstanceState(Pair(" in baseEntrySource)
        assertFalse("HashMap(loadedPackages)" in baseEntrySource)
        assertFalse("savedInstanceState as? Pair" in baseEntrySource)
        assertFalse("?.get() ?: loadedApkRef" in baseEntrySource)
        assertFalse("return resolveContextClassLoader()\n    }\n\n    private fun resolveContextClassLoader" in baseEntrySource)

        val entrySource = resolveProjectFile(
            "xposed/src/main/java/io/github/magisk317/mipush/hook/ModuleHooks.kt",
        ).readText()
        assertTrue("override fun resolveCurrentProcessTargets(param: ModuleLoadedParam)" in entrySource)
        assertTrue("resolveLoadedPackageClassLoader(\"com.android.systemui\")" in entrySource)
        assertTrue("resolveLoadedPackageClassLoader(XMSF_PACKAGE_NAME)" in entrySource)
        assertTrue("resolveLoadedPackageClassLoader(DOCUMENTS_UI_PACKAGE_NAME)" in entrySource)
        assertTrue("resolveLoadedPackageClassLoader(SECURITY_CORE_PACKAGE_NAME)" in entrySource)
        assertTrue("resolveLoadedPackageClassLoader(AMAP_PACKAGE_NAME)" in entrySource)

        val scope = resolveProjectFile("xposed/src/main/resources/META-INF/xposed/scope.list")
            .readLines()
            .filter { it.isNotBlank() }
        assertEquals(
            listOf(
                "android",
                "system",
                "com.android.systemui",
                "com.miui.securitycore",
                "com.google.android.documentsui",
                "com.xiaomi.xmsf",
                "com.autonavi.minimap",
            ),
            scope,
        )
    }


    @Test
    fun `manager host declares connection and recent-events widgets`() {
        val receivers = parseManifest().getElementsByTagName("receiver").let { nodes ->
            (0 until nodes.length).mapNotNull { index ->
                nodes.item(index).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
            }.toSet()
        }
        assertTrue(
            "io.github.magisk317.mipush.app.widget.ConnectionStatusWidgetProvider" in receivers,
        )
        assertTrue(
            "io.github.magisk317.mipush.app.widget.RecentEventsWidgetProvider" in receivers,
        )
    }

    @Test
    fun `event cache handoff uses a signed provider instead of a wakeup broadcast`() {
        val document = parseManifest()
        val providers = document.getElementsByTagName("provider")
        val eventCacheProvider = (0 until providers.length)
            .map(providers::item)
            .single { node ->
                node.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue ==
                    "io.github.magisk317.mipush.app.EventListCacheProvider"
            }

        assertEquals(
            "io.github.magisk317.mipush.event-cache",
            eventCacheProvider.attributes.getNamedItemNS(ANDROID_NS, "authorities").nodeValue,
        )
        assertEquals("true", eventCacheProvider.attributes.getNamedItemNS(ANDROID_NS, "exported").nodeValue)
        assertEquals(
            "com.xiaomi.xmsf.permission.BIND_MANAGER_RUNTIME",
            eventCacheProvider.attributes.getNamedItemNS(ANDROID_NS, "permission").nodeValue,
        )

        val receiverNames = document.getElementsByTagName("receiver").let { nodes ->
            (0 until nodes.length).mapNotNull { index ->
                nodes.item(index).attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue
            }
        }
        assertFalse("io.github.magisk317.mipush.app.EventListCacheUpdatedReceiver" in receiverNames)
    }

    @Test
    fun `event cache provider validates the Android user before writing`() {
        val source = resolveFile("src/main/java/io/github/magisk317/mipush/app/EventListCacheProvider.kt").readText()

        assertTrue(source.contains("Utils.requireValidUserId(Utils.myUserId())"))
        assertTrue(source.contains("validateEventCacheHandoff(events, userId)"))
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(resolveFile("src/main/AndroidManifest.xml"))

    private fun readStringResource(relativePath: String, name: String): String {
        val strings = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resolveFile(relativePath))
            .getElementsByTagName("string")
        return (0 until strings.length)
            .map { strings.item(it) }
            .first { it.attributes.getNamedItem("name").nodeValue == name }
            .textContent
    }

    private fun resolveFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val nested = File("mipush/$relativePath")
        if (nested.exists()) return nested
        return direct
    }

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.isFile) return direct
        val parent = File("../$relativePath")
        require(parent.isFile) { "Cannot resolve file: $relativePath" }
        return parent
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
