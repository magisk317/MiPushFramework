package com.xiaomi.mipush.sdk.help
import io.github.magisk317.mipush.protocol.model.*

import android.app.Activity
import android.os.Bundle
import com.xiaomi.mipush.sdk.AwakeHelper

class HelpActivity : Activity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        AwakeHelper.doAWork(this, intent, null)
        finish()
    }
}
