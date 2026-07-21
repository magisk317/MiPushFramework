package io.github.magisk317.mipush.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    fun `manager launcher is exported for LSPosed and launcher entrypoints`() {
        val activities = parseManifest().getElementsByTagName("activity")
        val launcher = (0 until activities.length)
            .map { activities.item(it) }
            .first { node ->
                node.attributes.getNamedItemNS(ANDROID_NS, "name")?.nodeValue ==
                    "io.github.magisk317.mipush.app.ManagerLauncherActivity"
        }

        assertEquals("true", launcher.attributes.getNamedItemNS(ANDROID_NS, "exported").nodeValue)
        assertEquals("true", launcher.attributes.getNamedItemNS(ANDROID_NS, "excludeFromRecents").nodeValue)
        assertEquals("true", launcher.attributes.getNamedItemNS(ANDROID_NS, "noHistory").nodeValue)
        val intentFilters = launcher.childNodes.let { children ->
            (0 until children.length)
                .map(children::item)
                .filter { it.nodeName == "intent-filter" }
        }
        val declarations = intentFilters.map { intentFilter ->
            (0 until intentFilter.childNodes.length)
                .map(intentFilter.childNodes::item)
                .filter { it.nodeName == "action" || it.nodeName == "category" }
                .associate { node ->
                    node.nodeName to node.attributes.getNamedItemNS(ANDROID_NS, "name").nodeValue
                }
        }

        assertTrue(
            declarations.any { filter ->
                filter["action"] == "android.intent.action.MAIN" &&
                    filter["category"] == "android.intent.category.LAUNCHER"
            },
        )
        assertTrue(
            declarations.any { filter ->
                filter["action"] == "android.intent.action.MAIN" &&
                    filter["category"] == "de.robv.android.xposed.category.MODULE_SETTINGS"
            }
        )
        val launcherSource = resolveFile(
            "src/main/java/io/github/magisk317/mipush/app/ManagerLauncherActivity.kt",
        ).readText()
        assertTrue("Intent.FLAG_ACTIVITY_NEW_TASK" in launcherSource)
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
