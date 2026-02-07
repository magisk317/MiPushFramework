package com.xiaomi.xmsf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xiaomi.xmsf.utils.LogUtils

class ShareLogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = LogUtils.getShareIntent(this)
        if (intent == null) {
            Toast.makeText(this, R.string.log_none, Toast.LENGTH_SHORT).show()
        } else {
            if (packageManager.resolveActivity(intent, 0) == null) {
                Toast.makeText(this, R.string.activity_intent_not_found, Toast.LENGTH_SHORT).show()
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(Intent.createChooser(intent, getString(R.string.log_share_title)))
            }
        }
        finish()
    }
}
