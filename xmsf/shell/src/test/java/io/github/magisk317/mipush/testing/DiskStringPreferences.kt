package io.github.magisk317.mipush.testing

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Host fixture with real commit/reopen I/O; it does not emulate Android process lifecycle. */
class DiskStringPreferences(private val file: Path) : SharedPreferences {
    var failWrites = false
    private val values = Properties().apply {
        if (Files.exists(file)) Files.newInputStream(file).use { load(it) }
    }

    override fun contains(key: String) = values.containsKey(key)
    override fun getString(key: String, defValue: String?): String? = values.getProperty(key, defValue)
    override fun getAll(): MutableMap<String, *> = values.stringPropertyNames().associateWith { values.getProperty(it) }.toMutableMap()
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = error("unused string-set fixture")
    override fun getInt(key: String, defValue: Int): Int = error("unused int fixture")
    override fun getLong(key: String, defValue: Long): Long = error("unused long fixture")
    override fun getFloat(key: String, defValue: Float): Float = error("unused float fixture")
    override fun getBoolean(key: String, defValue: Boolean): Boolean = error("unused boolean fixture")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val edits = mutableMapOf<String, String?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { edits[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { edits[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = error("unused string-set fixture")
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = error("unused int fixture")
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = error("unused long fixture")
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = error("unused float fixture")
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = error("unused boolean fixture")
        override fun commit(): Boolean {
            if (clear) values.clear()
            edits.forEach { (key, value) -> if (value == null) values.remove(key) else values.setProperty(key, value) }
            // Android commits memory before disk too; a failed removal must still keep admission fenced.
            if (failWrites) return false
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { values.store(it, null) }
            return true
        }
        override fun apply() { check(commit()) }
    }
}

fun diskPreferencesContext(directory: Path): Context {
    val preferences = mutableMapOf<String, DiskStringPreferences>()
    return mockk<Context>(relaxed = true).also { context ->
        every { context.getSharedPreferences(any(), any()) } answers {
            preferences.getOrPut(firstArg()) { DiskStringPreferences(directory.resolve(firstArg<String>() + ".properties")) }
        }
    }
}
