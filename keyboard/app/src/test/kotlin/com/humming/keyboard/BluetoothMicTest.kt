package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMicTest {
    private val builtinMic = 15 // AudioDeviceInfo.TYPE_BUILTIN_MIC
    private val wiredHeadset = 3

    @Test fun phoneMicOnly() = assertNull(BluetoothMic.pick(listOf(builtinMic)))
    @Test fun wiredIsNotBluetooth() = assertNull(BluetoothMic.pick(listOf(builtinMic, wiredHeadset)))
    @Test fun classicHeadset() = assertEquals(BluetoothMic.TYPE_BLUETOOTH_SCO, BluetoothMic.pick(listOf(builtinMic, 7)))
    @Test fun leAudioWins() = assertEquals(BluetoothMic.TYPE_BLE_HEADSET, BluetoothMic.pick(listOf(7, builtinMic, 26)))
}
