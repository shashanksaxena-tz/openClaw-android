package com.openclaw.android.sandbox

import android.content.Context
import java.io.File

/**
 * Enforces that all file operations stay within the app's designated workspace folder.
 * This is the security boundary — the agent cannot escape this directory.
 *
 * Supports dynamic workspace root for Spaces: when a space is active,
 * all file operations route to that space's files directory instead of the default.
 *
 * Directory structure:
 *   /storage/emulated/0/Android/data/com.openclaw.android/files/
 *     workspace/                <- Default agent working directory
 *     shared/                   <- Incoming shared media (read-only)
 *     spaces/{id}/files/        <- Per-space working directory
 *     spaces/{id}/conversations/
 *     spaces/{id}/knowledge/
 */
class SandboxedFileSystem(context: Context) {

    private val baseWorkspaceDir: File = File(context.getExternalFilesDir(null), "workspace").apply { mkdirs() }
    val sharedDir: File = File(context.getExternalFilesDir(null), "shared").apply { mkdirs() }
    private val spacesRoot: File = File(context.getExternalFilesDir(null), "spaces").apply { mkdirs() }

    /**
     * The effective workspace directory. When a space is active, this points to
     * spaces/{id}/files/ instead of workspace/.
     */
    val workspaceDir: File
        get() = _activeSpaceFilesDir ?: baseWorkspaceDir

    private var _activeSpaceFilesDir: File? = null

    /** Set the active space. All file operations will route to this space's files dir. */
    fun setActiveSpace(spaceId: String?) {
        _activeSpaceFilesDir = if (spaceId != null) {
            File(spacesRoot, "$spaceId/files").apply { mkdirs() }
        } else {
            null
        }
    }

    /** Get the currently active space ID, or null. */
    val activeSpaceId: String?
        get() = _activeSpaceFilesDir?.parentFile?.name

    /** Resolve a path relative to the effective workspace, rejecting escape attempts. */
    fun resolve(relativePath: String): Result<File> {
        val cleaned = relativePath
            .replace("\\", "/")
            .trimStart('/')

        val effectiveDir = workspaceDir
        val resolved = File(effectiveDir, cleaned).canonicalFile

        return if (resolved.path.startsWith(effectiveDir.canonicalPath)) {
            Result.success(resolved)
        } else {
            Result.failure(SecurityException("Path escapes sandbox: $relativePath"))
        }
    }

    /** Resolve a path in the shared directory (read-only access for agent). */
    fun resolveShared(relativePath: String): Result<File> {
        val cleaned = relativePath
            .replace("\\", "/")
            .trimStart('/')

        val resolved = File(sharedDir, cleaned).canonicalFile

        return if (resolved.path.startsWith(sharedDir.canonicalPath)) {
            Result.success(resolved)
        } else {
            Result.failure(SecurityException("Path escapes shared sandbox: $relativePath"))
        }
    }

    /** Check if a file is within any allowed directory. */
    fun isAllowed(file: File): Boolean {
        val canonical = file.canonicalPath
        return canonical.startsWith(workspaceDir.canonicalPath) ||
                canonical.startsWith(sharedDir.canonicalPath)
    }

    /** Check if a file is writable (only workspace files). */
    fun isWritable(file: File): Boolean {
        return file.canonicalPath.startsWith(workspaceDir.canonicalPath)
    }

    /** List files in the effective workspace. */
    fun listWorkspace(subPath: String = ""): Result<List<FileInfo>> {
        return resolve(subPath).map { dir ->
            if (!dir.exists() || !dir.isDirectory) return@map emptyList()
            dir.listFiles()?.map { file ->
                FileInfo(
                    name = file.name,
                    path = file.relativeTo(workspaceDir).path,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                )
            }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        }
    }

    /** List files in shared directory. */
    fun listShared(): List<FileInfo> {
        return sharedDir.listFiles()?.map { file ->
            FileInfo(
                name = file.name,
                path = file.relativeTo(sharedDir).path,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
            )
        }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    /** Get effective workspace disk usage. */
    fun getUsage(): DiskUsage {
        val dir = workspaceDir
        val totalSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fileCount = dir.walkTopDown().filter { it.isFile }.count()
        return DiskUsage(totalBytes = totalSize, fileCount = fileCount)
    }

    data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
    )

    data class DiskUsage(
        val totalBytes: Long,
        val fileCount: Int,
    ) {
        val displaySize: String
            get() = when {
                totalBytes < 1024 -> "$totalBytes B"
                totalBytes < 1024 * 1024 -> "${totalBytes / 1024} KB"
                totalBytes < 1024 * 1024 * 1024 -> "${totalBytes / (1024 * 1024)} MB"
                else -> "${"%.1f".format(totalBytes.toDouble() / (1024 * 1024 * 1024))} GB"
            }
    }
}
