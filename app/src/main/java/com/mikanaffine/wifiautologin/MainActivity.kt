package com.mikanaffine.wifiautologin

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.mikanaffine.wifiautologin.ui.theme.WifiAutoLoginTheme
import kotlinx.coroutines.launch

const val PREF_NAME = "WifiAutoLoginPrefs"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        WlanListener.isAutoConnectOn = sharedPreferences.getBoolean("auto_connect", false)
        try {
            unregisterReceiver(WlanListener)
        } catch (e: Exception) { }
        registerReceiver(WlanListener, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))

        setContent {
            WifiAutoLoginTheme {
                MainScreen(this)
            }
        }
    }
}

@Composable
fun MainScreen(ctx: Context) {
    val sharedPreferences = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    val scope = rememberCoroutineScope()

    var autoConnect by remember { mutableStateOf(sharedPreferences.getBoolean("auto_connect", false)) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "XJTU_STU 自动登录",
            modifier = Modifier.padding(bottom = 24.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showAccountDialog = true },
        ) {
            Text("修改账号")
        }
        if (showAccountDialog) {
            AccountSettingDialog(ctx) {
                showAccountDialog = false
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    Log.i("Login Button", "try login")

                    val username = sharedPreferences.getString("username", null) ?: run {
                        Toast.makeText(ctx, "账号为空", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val password = sharedPreferences.getString("password", null) ?: run {
                        Toast.makeText(ctx, "密码为空", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    SrunClient.login(username, password, ctx)
                    Toast.makeText(ctx, "已尝试登录", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Text("手动登录")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                showLogsDialog = true
            },
        ) {
            Text("显示日志")
        }
        if (showLogsDialog) {
            LogDialog {
                showLogsDialog = false
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "自动连接",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = autoConnect,
                onCheckedChange = { isChecked ->
                    autoConnect = isChecked
                    sharedPreferences.edit {
                        putBoolean("auto_connect", autoConnect)
                    }
                    WlanListener.isAutoConnectOn = autoConnect
                    Toast.makeText(ctx, "已切换自动登录", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun AccountSettingDialog(ctx: Context, dismiss: () -> Unit) {
    val sharedPreferences = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var username by remember { mutableStateOf(sharedPreferences.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(sharedPreferences.getString("password", "") ?: "") }

    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Filled.Settings, null) },
        title = { Text("设置登录使用的账号密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("账号") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    // visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    sharedPreferences.edit(true) {
                        putString("username", username)
                        putString("password", password)
                    }
                    Toast.makeText(ctx, "账号已保存", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}

@Composable
fun LogDialog(dismiss: () -> Unit) {
    val lastLoginResult by SrunClient.lastLoginResult.collectAsState()

    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Filled.MailOutline, null) },
        title = { Text("上次登录的日志") },
        text = {
            Column (
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(lastLoginResult)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    dismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
}
