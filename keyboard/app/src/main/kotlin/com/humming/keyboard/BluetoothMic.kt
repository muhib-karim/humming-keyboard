package com.humming.keyboard

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build
import android.util.Log

/**
 * Humming (M5): dictate through a connected Bluetooth headset's microphone.
 *
 * When a BT headset is connected (and Settings > Voice > "Bluetooth headset mic" is on), the call-audio path is
 * opened for the length of the dictation: setCommunicationDevice on Android 12+, Bluetooth SCO before that. The
 * recorder is pointed at the headset's input with setPreferredDevice. Everything is undone when the mic stops.
 */
object BluetoothMic {
    /** AudioDeviceInfo.TYPE_BLE_HEADSET (API 31); kept as a literal so the pure picker is JVM-testable. */
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLUETOOTH_SCO = 7

    /** Which input to use, by device type: BLE audio headset first, then classic SCO. Pure: BluetoothMicTest. */
    fun pick(types: List<Int>): Int? = when {
        TYPE_BLE_HEADSET in types -> TYPE_BLE_HEADSET
        TYPE_BLUETOOTH_SCO in types -> TYPE_BLUETOOTH_SCO
        else -> null
    }

    class Route(private val am: AudioManager, val input: AudioDeviceInfo, private val usedSco: Boolean) {
        val label: String = input.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth headset"

        fun attach(rec: AudioRecord) {
            runCatching { rec.setPreferredDevice(input) }
        }

        fun end() {
            runCatching {
                if (Build.VERSION.SDK_INT >= 31 && !usedSco) am.clearCommunicationDevice()
                else { @Suppress("DEPRECATION") am.stopBluetoothSco(); @Suppress("DEPRECATION") run { am.isBluetoothScoOn = false } }
            }
        }
    }

    /** Null when no BT headset mic is connected or the setting is off. */
    fun begin(ctx: Context): Route? {
        if (!Prefs.bluetoothMic(ctx)) return null
        val am = ctx.getSystemService(AudioManager::class.java) ?: return null
        val inputs = runCatching { am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }.getOrDefault(emptyList())
        val type = pick(inputs.map { it.type }) ?: return null
        val input = inputs.first { it.type == type }
        return runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val comm = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (comm != null && am.setCommunicationDevice(comm)) return@runCatching Route(am, input, usedSco = false)
            }
            @Suppress("DEPRECATION") am.startBluetoothSco()
            @Suppress("DEPRECATION") run { am.isBluetoothScoOn = true }
            Route(am, input, usedSco = true)
        }.onFailure { Log.w("HUMMING", "HUMMING_BT route failed: ${it.message}") }.getOrNull()
    }
}
