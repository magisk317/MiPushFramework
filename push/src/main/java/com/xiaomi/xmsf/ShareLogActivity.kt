package com.xiaomi.xmsf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.xiaomi.xmsf.utils.LogUtils

class ShareLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = LogUtils.prepareShareIntent(this)
        val intent = result.intent
        if (intent == null) {
            val error = result.error
            if (error.isNullOrBlank()) {
                Toast.makeText(this, R.string.log_none, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.log_share_error, error), Toast.LENGTH_SHORT).show()
            }
        } else {
            if (packageManager.resolveActivity(intent, 0) == null) {
                Toast.makeText(this, R.string.activity_intent_not_found, Toast.LENGTH_SHORT).show()
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, getString(R.string.log_share_title)))
            }
        }
        finish()
    }
}
