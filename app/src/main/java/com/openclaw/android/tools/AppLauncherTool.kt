package com.openclaw.android.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.serialization.json.*

class AppLauncherTool(private val context: Context) : Tool {

    override val name = "app_launcher"
    override val description = "Launch installed apps, search for apps, or open specific URLs/deep links. " +
            "Actions: 'launch' (open an app by name), 'search' (find installed apps), 'list' (list all apps), 'open_url' (open a URL)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("launch"); add("search"); add("list"); add("open_url") })
                put("description", "Action to perform")
            }
            putJsonObject("app_name") {
                put("type", "string")
                put("description", "App name to launch or search for")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Package name to launch directly (e.g. com.spotify.music)")
            }
            putJsonObject("url") {
                put("type", "string")
                put("description", "URL or deep link to open")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "launch" -> {
                    val packageName = args["package_name"]?.jsonPrimitive?.contentOrNull
                    val appName = args["app_name"]?.jsonPrimitive?.contentOrNull
                    when {
                        packageName != null -> launchByPackage(packageName)
                        appName != null -> launchByName(appName)
                        else -> ToolResult.error("Provide 'app_name' or 'package_name'")
                    }
                }
                "search" -> {
                    val query = args["app_name"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'app_name' for search")
                    searchApps(query)
                }
                "list" -> listApps()
                "open_url" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'url'")
                    openUrl(url)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("App launcher error: ${e.message}")
        }
    }

    private fun launchByPackage(packageName: String): ToolResult {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.error("App not found: $packageName")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return ToolResult.success("Launched $packageName")
    }

    private fun launchByName(appName: String): ToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.find {
            pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true)
        } ?: apps.find {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        } ?: return ToolResult.error("App not found: '$appName'. Try 'search' to find installed apps.")

        val intent = pm.getLaunchIntentForPackage(match.packageName)
            ?: return ToolResult.error("Cannot launch ${match.packageName} — no launcher activity")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        val label = pm.getApplicationLabel(match)
        return ToolResult.success("Launched $label (${match.packageName})")
    }

    private fun searchApps(query: String): ToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { pm.getApplicationLabel(it).toString().contains(query, ignoreCase = true) }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        if (apps.isEmpty()) return ToolResult.success("No apps matching '$query'.")

        val sb = StringBuilder("Apps matching '$query':\n\n")
        apps.take(20).forEach {
            sb.append("- **${pm.getApplicationLabel(it)}** (${it.packageName})\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun listApps(): ToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val sb = StringBuilder("Installed apps (${apps.size}):\n\n")
        apps.forEach {
            sb.append("- ${pm.getApplicationLabel(it)} (${it.packageName})\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun openUrl(url: String): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolResult.success("Opened URL: $url")
    }
}
