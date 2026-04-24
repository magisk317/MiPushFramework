package com.xiaomi.xmsf
import io.github.magisk317.mipush.runtime.R

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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.magisk317.mipush.platform.service.PushServiceAccessibility

class RemoveDozeActivity : ComponentActivity() {
    private fun setResultAndFinish(result: Int) {
        setResult(result)
        finish()
    }

    private val requestIgnoreBatteryOptimizations = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        setResultAndFinish(
            if (PushServiceAccessibility.isInDozeWhiteList(this)) Activity.RESULT_OK else Activity.RESULT_CANCELED
        )
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
            requestIgnoreBatteryOptimizations.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, e.localizedMessage, e)
            Toast.makeText(this, getString(R.string.common_err, e.message), Toast.LENGTH_SHORT).show()
            setResultAndFinish(Activity.RESULT_CANCELED)
        }
    }

    companion object {
        private const val TAG = "RemoveDozeActivity"
    }
}
