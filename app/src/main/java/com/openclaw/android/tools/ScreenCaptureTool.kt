package com.openclaw.android.tools

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureTool(
    private val context: Context,
    private val fs: SandboxedFileSystem,
    private val windowProvider: () -> Window?,
) : Tool {

    override val name = "screenshot"
    override val description = "Capture a screenshot of the current screen and save it to the workspace. " +
            "Returns the file path of the saved screenshot."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("filename") {
                put("type", "string")
                put("description", "Optional filename (without extension). Defaults to screenshot_<timestamp>")
            }
        }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val baseName = args["filename"]?.jsonPrimitive?.contentOrNull
            ?: "screenshot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"

        val window = windowProvider()
            ?: return ToolResult.error("Cannot capture screenshot: no active window")

        return try {
            val view = window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

            // Use PixelCopy for reliable capture
            val latch = java.util.concurrent.CountDownLatch(1)
            var copyResult = PixelCopy.ERROR_UNKNOWN
            PixelCopy.request(window, bitmap, { result ->
                copyResult = result
                latch.countDown()
            }, Handler(Looper.getMainLooper()))

            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

            if (copyResult != PixelCopy.SUCCESS) {
                // Fallback: draw from view cache
                view.isDrawingCacheEnabled = true
                view.buildDrawingCache()
                val cacheBitmap = view.drawingCache
                if (cacheBitmap != null) {
                    bitmap.recycle()
                    return saveScreenshot(cacheBitmap, baseName)
                }
                return ToolResult.error("Screenshot capture failed (code: $copyResult)")
            }

            saveScreenshot(bitmap, baseName)
        } catch (e: Exception) {
            ToolResult.error("Screenshot error: ${e.message}")
        }
    }

    private fun saveScreenshot(bitmap: Bitmap, baseName: String): ToolResult {
        val file = fs.resolve("screenshots/$baseName.png")
            .getOrElse { return ToolResult.error("Cannot save: ${it.message}") }

        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bitmap.recycle()

        val sizeKb = file.length() / 1024
        return ToolResult.success("Screenshot saved: workspace/screenshots/$baseName.png (${sizeKb}KB)")
    }
}
