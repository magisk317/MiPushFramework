package com.xiaomi.push.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils

class MIPushAppInfo private constructor(context: Context) {
    companion object {
        private const val PREF_KEY_DISABLE_PUSH_PKGS = "disable_push_pkg_names"
        private const val PREF_KEY_DISABLE_PUSH_PKGS_CACHE = "disable_push_pkg_names_cache"
        private const val PREF_KEY_UNREGISTERED_PKGS = "unregistered_pkg_names"
        private const val PREF_NAME = "mipush_app_info"

        @Volatile
        private var instance: MIPushAppInfo? = null

        @JvmStatic
        fun getInstance(context: Context): MIPushAppInfo {
            return instance ?: synchronized(this) {
                instance ?: MIPushAppInfo(context).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext ?: context
    private val unRegisteredPkg = mutableListOf<String>()
    private val disabledPushPkg = mutableListOf<String>()
    private val disabledPushPkgCache = mutableListOf<String>()

    init {
        val sharedPreferences = appContext.getSharedPreferences(PREF_NAME, 0)
        sharedPreferences.getString(PREF_KEY_UNREGISTERED_PKGS, "")
            ?.split(",")
            ?.forEach { pkg ->
                if (TextUtils.isEmpty(pkg)) {
                    unRegisteredPkg.add(pkg)
                }
            }
        sharedPreferences.getString(PREF_KEY_DISABLE_PUSH_PKGS, "")
            ?.split(",")
            ?.forEach { pkg ->
                if (!TextUtils.isEmpty(pkg)) {
                    disabledPushPkg.add(pkg)
                }
            }
        sharedPreferences.getString(PREF_KEY_DISABLE_PUSH_PKGS_CACHE, "")
            ?.split(",")
            ?.forEach { pkg ->
                if (!TextUtils.isEmpty(pkg)) {
                    disabledPushPkgCache.add(pkg)
                }
            }
    }

    fun addDisablePushPkg(packageName: String) {
        synchronized(disabledPushPkg) {
            if (!disabledPushPkg.contains(packageName)) {
                disabledPushPkg.add(packageName)
                persist(PREF_KEY_DISABLE_PUSH_PKGS, disabledPushPkg)
            }
        }
    }

    fun addDisablePushPkgCache(packageName: String) {
        synchronized(disabledPushPkgCache) {
            if (!disabledPushPkgCache.contains(packageName)) {
                disabledPushPkgCache.add(packageName)
                persist(PREF_KEY_DISABLE_PUSH_PKGS_CACHE, disabledPushPkgCache)
            }
        }
    }

    fun addUnRegisteredPkg(packageName: String) {
        synchronized(unRegisteredPkg) {
            if (!unRegisteredPkg.contains(packageName)) {
                unRegisteredPkg.add(packageName)
                persist(PREF_KEY_UNREGISTERED_PKGS, unRegisteredPkg)
            }
        }
    }

    fun isPushDisabled(packageName: String): Boolean {
        synchronized(disabledPushPkg) {
            return disabledPushPkg.contains(packageName)
        }
    }

    fun isPushDisabled4User(packageName: String): Boolean {
        synchronized(disabledPushPkgCache) {
            return disabledPushPkgCache.contains(packageName)
        }
    }

    fun isUnRegistered(packageName: String): Boolean {
        synchronized(unRegisteredPkg) {
            return unRegisteredPkg.contains(packageName)
        }
    }

    fun removeDisablePushPkg(packageName: String) {
        synchronized(disabledPushPkg) {
            if (disabledPushPkg.remove(packageName)) {
                persist(PREF_KEY_DISABLE_PUSH_PKGS, disabledPushPkg)
            }
        }
    }

    fun removeDisablePushPkgCache(packageName: String) {
        synchronized(disabledPushPkgCache) {
            if (disabledPushPkgCache.remove(packageName)) {
                persist(PREF_KEY_DISABLE_PUSH_PKGS_CACHE, disabledPushPkgCache)
            }
        }
    }

    fun removeUnRegisteredPkg(packageName: String) {
        synchronized(unRegisteredPkg) {
            if (unRegisteredPkg.remove(packageName)) {
                persist(PREF_KEY_UNREGISTERED_PKGS, unRegisteredPkg)
            }
        }
    }

    private fun persist(key: String, values: List<String>) {
        appContext.getSharedPreferences(PREF_NAME, 0)
            .edit()
            .putString(key, XMStringUtils.join(values, ","))
            .commit()
    }
}
