package com.openclaw.android.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import kotlinx.serialization.json.*

class SettingsControlTool(private val context: Context) : Tool {

    override val name = "device_settings"
    override val description = "Control device settings: volume, brightness, Do Not Disturb, Wi-Fi, " +
            "open system settings pages. Actions: 'get_volume', 'set_volume', 'get_brightness', " +
            "'set_brightness', 'dnd_on', 'dnd_off', 'wifi_status', 'open_settings'."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("get_volume"); add("set_volume")
                    add("get_brightness"); add("set_brightness")
                    add("dnd_on"); add("dnd_off"); add("get_dnd")
                    add("wifi_status"); add("open_settings")
                })
                put("description", "Settings action to perform")
            }
            putJsonObject("value") {
                put("type", "integer")
                put("description", "Value to set (0-100 for volume/brightness percentage)")
            }
            putJsonObject("settings_page") {
                put("type", "string")
                put("description", "Settings page to open: 'wifi', 'bluetooth', 'display', 'sound', 'battery', 'storage', 'apps', 'notifications', 'security', 'accessibility'")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")
        val value = args["value"]?.jsonPrimitive?.intOrNull

        return try {
            when (action) {
                "get_volume" -> getVolume()
                "set_volume" -> setVolume(value ?: return ToolResult.error("Missing 'value' for set_volume"))
                "get_brightness" -> getBrightness()
                "set_brightness" -> setBrightness(value ?: return ToolResult.error("Missing 'value' for set_brightness"))
                "dnd_on" -> setDnd(true)
                "dnd_off" -> setDnd(false)
                "get_dnd" -> getDnd()
                "wifi_status" -> getWifiStatus()
                "open_settings" -> openSettings(args["settings_page"]?.jsonPrimitive?.contentOrNull ?: "main")
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}. You may need to grant the 'Modify System Settings' permission in Android settings.")
        } catch (e: Exception) {
            ToolResult.error("Settings error: ${e.message}")
        }
    }

    private fun getVolume(): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mediaVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringVol = audio.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        val alarmVol = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val alarmMax = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        return ToolResult.success(buildString {
            append("Current volume levels:\n")
            append("- Media: ${(mediaVol * 100 / mediaMax)}%\n")
            append("- Ring: ${(ringVol * 100 / ringMax)}%\n")
            append("- Alarm: ${(alarmVol * 100 / alarmMax)}%\n")
            append("- Ringer mode: ${when (audio.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "Normal"
                AudioManager.RINGER_MODE_SILENT -> "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                else -> "Unknown"
            }}")
        })
    }

    private fun setVolume(percent: Int): ToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) * maxVol / 100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return ToolResult.success("Media volume set to $percent%")
    }

    private fun getBrightness(): ToolResult {
        val brightness = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, 128
        )
        val percent = (brightness * 100 / 255)
        val autoMode = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (e: Exception) { 0 }
        val isAuto = autoMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

        return ToolResult.success("Screen brightness: $percent% (auto-brightness: ${if (isAuto) "ON" else "OFF"})")
    }

    private fun setBrightness(percent: Int): ToolResult {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ToolResult.error("Need 'Modify System Settings' permission. Opening settings page...")
        }

        val value = (percent.coerceIn(0, 100) * 255 / 100)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return ToolResult.success("Screen brightness set to $percent%")
    }

    private fun setDnd(enabled: Boolean): ToolResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return ToolResult.error("Need 'Do Not Disturb' access. Opening settings page...")
        }

        nm.setInterruptionFilter(
            if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return ToolResult.success("Do Not Disturb ${if (enabled) "enabled" else "disabled"}")
    }

    private fun getDnd(): ToolResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val filter = nm.currentInterruptionFilter
        val status = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF (all notifications)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "ON (total silence)"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms only"
            else -> "Unknown"
        }
        return ToolResult.success("Do Not Disturb status: $status")
    }

    @Suppress("DEPRECATION")
    private fun getWifiStatus(): ToolResult {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val enabled = wifi.isWifiEnabled
        val info = wifi.connectionInfo
        val ssid = info?.ssid?.removeSurrounding("\"") ?: "Unknown"

        return ToolResult.success(buildString {
            append("Wi-Fi: ${if (enabled) "ON" else "OFF"}\n")
            if (enabled) {
                append("Connected to: $ssid\n")
                append("Signal: ${WifiManager.calculateSignalLevel(info?.rssi ?: 0, 5)}/4 bars\n")
            }
        })
    }

    private fun openSettings(page: String): ToolResult {
        val action = when (page.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return ToolResult.success("Opened $page settings")
    }
}
