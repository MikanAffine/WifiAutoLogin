package com.mikanaffine.wifiautologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log

object WlanListener : BroadcastReceiver() {
    var isAutoConnectOn = false

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

        if (!isAutoConnectOn) return
        Log.i("WifiWatcher", "try login")

        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
        if (networkInfo == null || !networkInfo.isConnected) return

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid?.replace("\"", "") ?: return

        // 检查是否是目标 WiFi 网络
        if (ssid.uppercase() != "XJTU_STU") return

        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val username = sharedPreferences.getString("username", null) ?: return
        val password = sharedPreferences.getString("password", null) ?: return

        SrunClient.login(username, password, context)
    }
}
