package com.andrerinas.openheadunit.connection

import android.content.Context
import com.andrerinas.openheadunit.utils.AppLog
import java.io.File

/**
 * 把车机当前热点配置同步给 Xposed 模块 com.xbutler.iccoafix。
 *
 * 模块在 xAuto 进程内读取同一份 iccoa.conf，用于绕过 ICCOA SDK 对
 * WifiManager.getSoftApConfiguration() 的 signature 权限要求。
 * 无 root / 读不到密码时只记录日志，不影响任何功能。
 */
object IccoaHotspotConfig {

    @Volatile private var done = false

    fun sync(context: Context) {
        if (done) return
        done = true
        val app = context.applicationContext
        Thread({
            try {
                val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                val pwd = prefs.getString("hotspot-password", "").orEmpty()
                val prefSsid = prefs.getString("hotspot-ssid", "").orEmpty()
                if (pwd.isEmpty()) {
                    AppLog.i("ICCOA cfg skip no-password")
                    return@Thread
                }
                val dump = root("dumpsys wifi").orEmpty()
                val ssid = Regex("mApConfig\\.SoftApConfiguration\\.SSID:\\s*(.+)")
                    .find(dump)?.groupValues?.get(1)?.trim().orEmpty().ifEmpty { prefSsid }
                val freq = Regex("frequency=\\s*(\\d+)")
                    .find(dump)?.groupValues?.get(1)?.toIntOrNull() ?: 5180
                if (ssid.isEmpty()) {
                    AppLog.i("ICCOA cfg skip no-ssid")
                    return@Thread
                }
                val channel = when {
                    freq in 2400..2500 -> (freq - 2407) / 5
                    freq in 5000..5900 -> (freq - 5000) / 5
                    else -> 36
                }
                val band = if (freq in 2400..2500) 1 else 2
                val dir = app.getExternalFilesDir(null)
                dir?.mkdirs()
                val f = File(dir, "iccoa.conf")
                f.writeText("ssid=$ssid\npassword=$pwd\nband=$band\nchannel=$channel\n")
                AppLog.i("ICCOA cfg write ssid=$ssid band=$band channel=$channel root=1")
            } catch (t: Throwable) {
                AppLog.w("ICCOA cfg fail ${t.javaClass.simpleName}: ${t.message}")
            }
        }, "iccoa-conf").start()
    }

    private fun root(cmd: String): String? = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        if (p.exitValue() == 0) out else null
    } catch (t: Throwable) {
        null
    }
}
