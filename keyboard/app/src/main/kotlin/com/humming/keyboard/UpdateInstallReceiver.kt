/*
 * Humming (added file, 1.6). Receives the outcome of an update install session started by AppUpdate.
 */
package com.humming.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.humming.keyboard.UPDATE_INSTALL_STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                AppUpdate.onNeedsConfirm(context.applicationContext, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdate.onResult(true, null)
            else -> AppUpdate.onResult(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status")
        }
    }
}
