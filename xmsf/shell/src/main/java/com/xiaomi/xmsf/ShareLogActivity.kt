package com.xiaomi.xmsf

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Private compatibility component retained for its manifest name.
 *
 * Runtime diagnostic export is intentionally local-save-only and is initiated by the Manager UI
 * through a user-selected SAF destination. This component must not create archives or share them.
 */
class ShareLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
