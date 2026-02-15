package com.openclaw.android

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridges runtime permission requests between background tool execution (coroutines)
 * and the Activity UI layer (which owns the permission dialog).
 *
 * Tools declare [requiredPermissions]. Before executing a tool, [AgentRuntime] calls
 * [ensurePermissions]. If any are missing, a [PermissionRequest] is emitted via [requests]
 * and the coroutine suspends until the UI calls [onPermissionResult].
 */
class PermissionManager(private val context: Context) {

    data class PermissionRequest(
        val permissions: List<String>,
        val toolName: String,
        internal val deferred: CompletableDeferred<Boolean>,
    )

    private val _requests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 1)
    val requests: SharedFlow<PermissionRequest> = _requests.asSharedFlow()

    /** Check if all [permissions] are already granted. */
    fun allGranted(permissions: List<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Ensure all [permissions] are granted. Returns immediately if already granted.
     * Otherwise emits a [PermissionRequest] for the UI to handle and suspends until resolved.
     */
    suspend fun ensurePermissions(permissions: List<String>, toolName: String): Boolean {
        if (permissions.isEmpty()) return true

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true

        val deferred = CompletableDeferred<Boolean>()
        val request = PermissionRequest(missing, toolName, deferred)
        _requests.emit(request)
        return deferred.await()
    }

    /** Called by the UI layer after the system permission dialog completes. */
    fun onPermissionResult(request: PermissionRequest, allGranted: Boolean) {
        request.deferred.complete(allGranted)
    }
}
