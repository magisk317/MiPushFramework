@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import top.trumeet.common.push.PushServiceAccessibility

class RemoveDozeActivity : AppCompatActivity() {
    private fun setResultAndFinish(result: Int) {
        setResult(result)
        finish()
    }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || PushServiceAccessibility.isInDozeWhiteList(this)) {
            setResultAndFinish(Activity.RESULT_OK)
            return
        }
        val intent = Intent()
        val packageName = packageName
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:$packageName")
        try {
            startActivityForResult(intent, RC_REQUEST)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, e.localizedMessage, e)
            Toast.makeText(this, getString(R.string.common_err, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_REQUEST -> setResultAndFinish(
                if (PushServiceAccessibility.isInDozeWhiteList(this)) Activity.RESULT_OK else Activity.RESULT_CANCELED
            )
        }
    }

    companion object {
        private const val RC_REQUEST = 0
        private const val TAG = "RemoveDozeActivity"
    }
}
