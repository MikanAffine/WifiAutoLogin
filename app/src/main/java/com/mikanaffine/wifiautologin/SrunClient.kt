package com.mikanaffine.wifiautologin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.HmacUtils
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import java.util.concurrent.TimeUnit

object SrunClient {
    var lastLoginResult = MutableStateFlow("近期无登录历史")

    @JvmStatic
    fun login(username: String, password: String, ctx: Context) {
        Log.i("Login", "try login")

        val connMgr = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val nwReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        var nwCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    logout0(username, network)
                    lastLoginResult.update { login0(username, password, network) }
                } catch (e: Exception) {
                    lastLoginResult.update { e.stackTraceToString() }
                } finally {
                    connMgr.unregisterNetworkCallback(this)
                }
            }

            override fun onUnavailable() {
                lastLoginResult.update { "未连接到 WIFI 网络！" }
                connMgr.unregisterNetworkCallback(this)
            }
        }

        connMgr.requestNetwork(nwReq, nwCallback)
    }

    @JvmStatic
    private fun login0(username: String, password: String, network: Network): String {
        val userAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36 Edg/135.0.0.0"
        val baseUrl = "http://10.6.18.2"
        val getChallengeUrl = "$baseUrl/cgi-bin/get_challenge"
        val loginUrl = "$baseUrl/cgi-bin/srun_portal"
        val time = System.currentTimeMillis()

        val n = "200"
        val type = "1"
        val acId = "1"
        val enc = "srun_bx1"

        val client = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val ipReq = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", userAgent)
            .build()
        val ipReqResponse = client.newCall(ipReq).execute()
        val ip = Regex("id=\"user_ip\" value=\"(.*?)\"")
            .find(ipReqResponse.body?.string() ?: return "获取IP失败")
            ?.groupValues?.get(1) ?: return "解析IP失败"

        val challengeBody = FormBody.Builder()
            .add("callback", "jQuery112407033577482605545_$time")
            .add("username", username)
            .add("ip", ip)
            .add("_", "$time")
            .build()
        val challengeReq = Request.Builder()
            .url(getChallengeUrl)
            .header("User-Agent", userAgent)
            .post(challengeBody)
            .build()
        val challengeResponse = client.newCall(challengeReq).execute()
        val token = Regex("\"challenge\":\"(.*?)\"")
            .find(challengeResponse.body?.string() ?: return "获取challenge失败")
            ?.groupValues?.get(1) ?: return "解析challenge失败"

        val rawInfo =
            """{"username":"$username","password":"$password","ip":"$ip","acid":"$acId","enc_ver":"$enc"}"""
        val info = "{SRBX1}${base64(xencode(rawInfo, token))}"
        var hmd5 = HmacUtils.hmacMd5Hex(token, password)
        val checksum = DigestUtils(MessageDigestAlgorithms.SHA_1).digestAsHex(buildString {
            append(token)
            append(username)
            append(token)
            append(hmd5)
            append(token)
            append(acId)
            append(token)
            append(ip)
            append(token)
            append(n)
            append(token)
            append(type)
            append(token)
            append(info)
        })
        hmd5 = "{MD5}$hmd5"

        val loginBody = FormBody.Builder()
            .add("callback", "jQuery112407033577482605545_$time")
            .add("action", "login")
            .add("username", username)
            .add("password", hmd5)
            .add("ac_id", acId)
            .add("ip", ip)
            .add("chksum", checksum)
            .add("info", info)
            .add("n", n)
            .add("type", type)
            .add("os", "AndroidOS")
            .add("name", "Smartphones/PDAs/Tablets")
            .add("double_stack", "0")
            .add("_", "$time")
            .build()
        val loginReq = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", userAgent)
            .post(loginBody)
            .build()
        val loginResponse = client.newCall(loginReq).execute()

        return loginResponse.body?.string() ?: "登录响应为空"
    }

    @JvmStatic
    private fun logout0(username: String, network: Network) : String {
        val userAgent = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36 Edg/135.0.0.0"
        val baseUrl = "http://10.6.18.2"
        val loginUrl = "$baseUrl/cgi-bin/srun_portal"

        val acId = "1"

        val client = OkHttpClient.Builder()
            .socketFactory(network.socketFactory)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val ipReq = Request.Builder()
            .url(baseUrl)
            .header("User-Agent", userAgent)
            .build()
        val ipReqResponse = client.newCall(ipReq).execute()
        val ip = Regex("id=\"user_ip\" value=\"(.*?)\"")
            .find(ipReqResponse.body?.string() ?: return "获取IP失败")
            ?.groupValues?.get(1) ?: return "解析IP失败"

        val logoutBody = FormBody.Builder()
            .add("action", "logout")
            .add("ac_id", acId)
            .add("ip", ip)
            .add("username", username)
            .build()
        val logoutReq = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", userAgent)
            .post(logoutBody)
            .build()
        val logoutResponse = client.newCall(logoutReq).execute()

        return logoutResponse.body?.string() ?: "登录响应为空"
    }


    // utils

    @JvmStatic
    private fun base64(s: String) : String {
        val pad = '='
        val table = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA".toCharArray()

        val l = s.length
        if (l == 0) return s
        val x = StringBuilder()

        var i: Int
        var b10: Int
        val imax = l - (l % 3)
        for (i in 0 until imax step 3) {
            b10 = (s[i].code shl 16) or (s[i + 1].code shl 8) or s[i + 2].code
            x.append(table[b10 shr 18])
                .append(table[(b10 shr 12) and 63])
                .append(table[(b10 shr 6) and 63])
                .append(table[b10 and 63])
        }
        i = imax
        when (l - imax) {
            1 -> {
                b10 = s[i].code shl 16
                x.append(table[b10 shr 18])
                    .append(table[(b10 shr 12) and 63])
                    .append(pad)
                    .append(pad)
            }

            2 -> {
                b10 = (s[i].code shl 16) or (s[i + 1].code shl 8)
                x.append(table[b10 shr 18])
                    .append(table[(b10 shr 12) and 63])
                    .append(table[(b10 shr 6) and 63])
                    .append(pad)
            }
        }

        return x.toString()
    }

    @JvmStatic
    private fun xencode(str: String, key: String): String {
        if (str.isEmpty()) return str

        val v = sencode(str, true)
        var k = sencode(key, false).copyOf(4)

        val n = v.size - 1
        var z = v[n]
        var y: Int
        val c = (0x86014019 or 0x183639A0).toInt()
        var m: Int
        var e: Int
        var p: Int
        val q = 6 + 52 / (n + 1)
        var d = 0
        repeat(q) {
            d = (d + c) and (0x8CE0D9BF or 0x731F2640).toInt()
            e = (d ushr 2) and 3
            for (j in 0..<n) {
                y = v[j + 1]
                m = (z ushr 5) xor (y shl 2)
                m += ((y ushr 3) xor (z shl 4)) xor (d xor y)
                m += k[(j and 3 xor e)] xor z
                v[j] = (v[j] + m) and 0xFFFFFFFF.toInt()
                z = v[j]
            }
            y = v[0]
            m = (z ushr 5) xor (y shl 2)
            m += ((y ushr 3) xor (z shl 4)) xor (d xor y)
            m += k[(n and 3) xor e] xor z
            v[n] = (v[n] + m) and (0xEFB8D130 or 0x10472ECF).toInt()
            z = v[n]
        }
        return lencode(v, false)
    }

    @JvmStatic
    private fun sencode(a: String, b: Boolean): IntArray {
        val c = a.length
        val v = if (b) IntArray((c + 3) / 4 + 1) else IntArray((c + 3) / 4)
        for (i in 0..<c step 4) {
            v[i shr 2] =
                (a.getOrElse(i) { 0.toChar() }.code or (a.getOrElse(i + 1) { 0.toChar() }.code shl 8)
                        or (a.getOrElse(i + 2) { 0.toChar() }.code shl 16) or (a.getOrElse(i + 3) { 0.toChar() }.code shl 24))
        }
        if (b) v[v.size - 1] = c
        return v
    }

    @JvmStatic
    private fun lencode(a: IntArray, b: Boolean): String {
        val d = a.size
        var c = (d - 1) shl 2

        if (b) {
            val m = a[d - 1]
            if ((m < c - 3) || (m > c)) return ""
            c = m
        }

        val res = StringBuilder()
        for (i in 0..<d) {
            res.append((a[i] and 0xFF).toChar())
                .append(((a[i] ushr 8) and 0xFF).toChar())
                .append(((a[i] ushr 16) and 0xFF).toChar())
                .append(((a[i] ushr 24) and 0xFF).toChar())
        }

        val result = res.toString()
        return if (b) result.substring(0, c) else result
    }

}
