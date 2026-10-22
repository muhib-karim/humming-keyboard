/*
 * Humming (added file, 1.6). Receives the outcome of an update install session started by AppUpdate.
 */
package com.humming.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.humming.keyboard.UPDATE_INSTALL_STATUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 1.7.1: IntentCompat picks the API 33 call or the old one, so this file calls nothing deprecated.
                val confirm = androidx.core.content.IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                AppUpdate.onNeedsConfirm(context.applicationContext, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdate.onResult(true, null)
            else -> AppUpdate.onResult(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status")
        }
    }
}
