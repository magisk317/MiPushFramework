package io.github.magisk317.mipush.testing

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs

/**
 * Creates a [Context] mock that returns [SharedPreferences] instances
 * for the given preference-name → initial-data pairs.
 *
 * Preferences are cached per name so repeated calls with the same name
 * return the same instance (matching real Android behaviour).
 */
fun mockContext(vararg prefData: Pair<String, Map<String, Any?>>): Context {
    val stores = prefData.associate { (name, values) ->
        name to values.toMutableMap()
    }.toMutableMap()
    val cache = mutableMapOf<String, SharedPreferences>()

    val ctx = mockk<Context>(relaxed = true)
    every { ctx.applicationContext } returns ctx
    every { ctx.getSharedPreferences(any(), any()) } answers {
        val name = firstArg<String>()
        cache.getOrPut(name) {
            mockSharedPreferences(stores.getOrPut(name) { mutableMapOf() })
        }
    }
    return ctx
}

/**
 * Creates a [SharedPreferences] mock backed by the given mutable map.
 * Calling [SharedPreferences.edit] returns an editor that transparently
 * mutates the same backing store, enabling read-after-write consistency.
 */
fun mockSharedPreferences(
    values: MutableMap<String, Any?> = mutableMapOf(),
): SharedPreferences {
    val prefs = mockk<SharedPreferences>(relaxed = true)

    every { prefs.getString(any(), any()) } answers {
        values[firstArg()] as? String ?: secondArg()
    }
    every { prefs.getLong(any(), any()) } answers {
        values[firstArg()] as? Long ?: secondArg()
    }
    every { prefs.contains(any()) } answers {
        firstArg<String>() in values
    }
    every { prefs.getAll() } answers { values.toMap() }
    every { prefs.edit() } answers { mockEditor(values) }

    return prefs
}

private fun mockEditor(values: MutableMap<String, Any?>): SharedPreferences.Editor {
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    every { editor.putString(any(), any()) } answers {
        val key = firstArg<String>()
        val value = secondArg<String?>()
        if (value == null) values.remove(key) else values[key] = value
        editor
    }
    every { editor.putLong(any(), any()) } answers {
        values[firstArg()] = secondArg<Long>()
        editor
    }
    every { editor.remove(any()) } answers {
        values.remove(firstArg<String>())
        editor
    }
    every { editor.clear() } answers {
        values.clear()
        editor
    }
    every { editor.commit() } returns true
    every { editor.apply() } just Runs

    return editor
}
