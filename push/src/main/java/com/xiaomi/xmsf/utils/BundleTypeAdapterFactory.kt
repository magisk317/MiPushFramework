@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.os.Bundle
import android.os.Parcelable
import android.util.Pair
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

class BundleTypeAdapterFactory : TypeAdapterFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!Bundle::class.java.isAssignableFrom(type.rawType)) {
            return null
        }
        return object : TypeAdapter<Bundle>() {
            override fun write(out: JsonWriter, bundle: Bundle?) {
                if (bundle == null) {
                    out.nullValue()
                    return
                }
                out.beginObject()
                for (key in bundle.keySet()) {
                    out.name(key)
                    val value = bundle.get(key)
                    if (value == null) {
                        out.nullValue()
                    } else {
                        gson.toJson(value, value.javaClass, out)
                    }
                }
                out.endObject()
            }

            override fun read(input: JsonReader): Bundle? {
                return when (input.peek()) {
                    JsonToken.NULL -> {
                        input.nextNull()
                        null
                    }
                    JsonToken.BEGIN_OBJECT -> toBundle(readObject(input))
                    else -> throw IOException("expecting object: ${input.path}")
                }
            }

            private fun toBundle(values: List<Pair<String, Any?>>): Bundle {
                val bundle = Bundle()
                for (entry in values) {
                    val key = entry.first
                    val value = entry.second
                    when (value) {
                        is String -> bundle.putString(key, value)
                        is Int -> bundle.putInt(key, value)
                        is Long -> bundle.putLong(key, value)
                        is Double -> bundle.putDouble(key, value)
                        is Parcelable -> bundle.putParcelable(key, value)
                        is List<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val objectValues = value as List<Pair<String, Any?>>
                            val subBundle = toBundle(objectValues)
                            bundle.putParcelable(key, subBundle)
                        }
                        else -> throw IOException("Unparcelable key, value: $key, $value")
                    }
                }
                return bundle
            }

            private fun readObject(input: JsonReader): List<Pair<String, Any?>> {
                val obj = mutableListOf<Pair<String, Any?>>()
                input.beginObject()
                while (input.peek() != JsonToken.END_OBJECT) {
                    when (input.peek()) {
                        JsonToken.NAME -> {
                            val name = input.nextName()
                            val value = readValue(input)
                            obj.add(Pair(name, value))
                        }
                        JsonToken.END_OBJECT -> Unit
                        else -> throw IOException("expecting object: ${input.path}")
                    }
                }
                input.endObject()
                return obj
            }

            private fun readValue(input: JsonReader): Any? {
                return when (input.peek()) {
                    JsonToken.BEGIN_ARRAY -> readArray(input)
                    JsonToken.BEGIN_OBJECT -> readObject(input)
                    JsonToken.BOOLEAN -> input.nextBoolean()
                    JsonToken.NULL -> {
                        input.nextNull()
                        null
                    }
                    JsonToken.NUMBER -> readNumber(input)
                    JsonToken.STRING -> input.nextString()
                    else -> throw IOException("expecting value: ${input.path}")
                }
            }

            private fun readNumber(input: JsonReader): Any {
                val doubleValue = input.nextDouble()
                if (doubleValue - kotlin.math.ceil(doubleValue) == 0.0) {
                    val longValue = doubleValue.toLong()
                    if (longValue >= Int.MIN_VALUE && longValue <= Int.MAX_VALUE) {
                        return longValue.toInt()
                    }
                    return longValue
                }
                return doubleValue
            }

            private fun readArray(input: JsonReader): List<Any?> {
                val list = mutableListOf<Any?>()
                input.beginArray()
                while (input.peek() != JsonToken.END_ARRAY) {
                    list.add(readValue(input))
                }
                input.endArray()
                return list
            }
        } as TypeAdapter<T>
    }
}
