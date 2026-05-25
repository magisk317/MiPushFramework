package io.github.magisk317.mipush.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "mipush_framework_settings")
