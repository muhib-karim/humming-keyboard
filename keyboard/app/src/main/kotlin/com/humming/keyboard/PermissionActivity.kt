package com.humming.keyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/** Transparent trampoline: an IME cannot show the runtime-permission dialog itself. */
class PermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) {
                "Microphone on. Tap the mic key to dictate."
            } else {
                "Microphone is off. Open the Humming app to allow it."
            },
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    companion object {
        private const val REQ_MIC = 7
    }
}
