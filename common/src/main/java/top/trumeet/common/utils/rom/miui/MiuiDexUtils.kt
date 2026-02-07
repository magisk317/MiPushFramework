@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.common.utils.rom.miui

import android.content.Context
import android.os.Build
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Array
import java.lang.reflect.Field

internal object MiuiDexUtils {
    @Throws(NoSuchFieldException::class)
    private fun getDexPathList(classLoader: ClassLoader): Any {
        if (classLoader is BaseDexClassLoader) {
            val fields = BaseDexClassLoader::class.java.declaredFields
            for (field in fields) {
                if ("dalvik.system.DexPathList" == field.type.name) {
                    field.isAccessible = true
                    return try {
                        field.get(classLoader)!!
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        }
        throw NoSuchFieldException("dexPathList field not found.")
    }

    @Throws(NoSuchFieldException::class)
    private fun getNativeLibraryDirectoriesField(obj: Any): Field {
        for (field in obj.javaClass.declaredFields) {
            val type = field.type
            if (type.isArray && type.componentType == File::class.java) {
                field.isAccessible = true
                return field
            }
        }
        throw NoSuchFieldException("nativeLibraryDirectories field not found.")
    }

    @Throws(NoSuchFieldException::class)
    private fun getElementField(obj: Any, fieldName: String): Field {
        for (field in obj.javaClass.declaredFields) {
            if (field.name == fieldName) {
                val type = field.type
                if (type.isArray && "dalvik.system.DexPathList\$Element" == type.componentType.name) {
                    field.isAccessible = true
                    return field
                }
            }
        }
        throw NoSuchFieldException("$fieldName field not found.")
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class, ClassNotFoundException::class)
    private fun expandDexElements(targetPathList: Any, extraPathList: Any) {
        mergeArray(targetPathList, extraPathList, "dexElements")
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class, ClassNotFoundException::class)
    private fun expandNativeLibraries(targetPathList: Any, extraPathList: Any, libraryPath: String) {
        if (Build.VERSION.SDK_INT >= 23) {
            mergeArray(targetPathList, extraPathList, "nativeLibraryPathElements")
        } else {
            mergeFileArray(targetPathList, libraryPath)
        }
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class, ClassNotFoundException::class)
    private fun mergeArray(targetPathList: Any, extraPathList: Any, fieldName: String) {
        val extraElements = getElementField(extraPathList, fieldName).get(extraPathList) as kotlin.Array<*>
        val targetField = getElementField(targetPathList, fieldName)
        val targetElements = targetField.get(targetPathList) as kotlin.Array<*>
        val combined = Array.newInstance(
            Class.forName("dalvik.system.DexPathList\$Element"),
            targetElements.size + 1
        ) as kotlin.Array<Any?>
        combined[0] = extraElements[0]
        System.arraycopy(targetElements, 0, combined, 1, targetElements.size)
        targetField.set(targetPathList, combined)
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun mergeFileArray(pathList: Any, path: String) {
        val field = getNativeLibraryDirectoriesField(pathList)
        val original = field.get(pathList) as kotlin.Array<File>
        val combined = arrayOfNulls<File>(original.size + 1)
        combined[0] = File(path)
        System.arraycopy(original, 0, combined, 1, original.size)
        field.set(pathList, combined)
    }

    @JvmStatic
    fun load(dexPath: String?, optimizedDirectory: String?, librarySearchPath: String?, classLoader: ClassLoader): Boolean {
        return load(dexPath, optimizedDirectory, librarySearchPath, classLoader, null)
    }

    @JvmStatic
    fun load(
        dexPath: String?,
        optimizedDirectory: String?,
        librarySearchPath: String?,
        classLoader: ClassLoader,
        context: Context?
    ): Boolean {
        if (dexPath == null && (librarySearchPath == null || context == null)) {
            return false
        }
        return try {
            val targetPathList = getDexPathList(classLoader)
            val finalDexPath: String = if (dexPath != null) {
                dexPath
            } else if (Build.VERSION.SDK_INT < 23) {
                mergeFileArray(targetPathList, librarySearchPath!!)
                return true
            } else {
                context!!.applicationInfo.sourceDir
            }

            val extraLoader = if (optimizedDirectory == null) {
                PathClassLoader(finalDexPath, librarySearchPath, classLoader.parent)
            } else {
                DexClassLoader(finalDexPath, optimizedDirectory, librarySearchPath, classLoader.parent)
            }
            val extraPathList = getDexPathList(extraLoader)

            if (dexPath != null) {
                expandDexElements(targetPathList, extraPathList)
            }
            if (librarySearchPath != null) {
                expandNativeLibraries(targetPathList, extraPathList, librarySearchPath)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
