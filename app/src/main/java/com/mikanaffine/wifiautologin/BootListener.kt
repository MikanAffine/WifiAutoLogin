package com.mikanaffine.wifiautologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager

class BootListener : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        WlanListener.isAutoConnectOn = sharedPreferences.getBoolean("auto_connect", false)
        val appCtx = context.applicationContext
        try {
            appCtx.unregisterReceiver(WlanListener)
        } catch (e: Exception) { }
        appCtx.registerReceiver(WlanListener, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
    }
}