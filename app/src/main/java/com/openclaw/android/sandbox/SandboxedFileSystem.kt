package com.openclaw.android.sandbox

import android.content.Context
import java.io.File

/**
 * Enforces that all file operations stay within the app's designated workspace folder.
 * This is the security boundary — the agent cannot escape this directory.
 *
 * Directory structure:
 *   /storage/emulated/0/Android/data/com.openclaw.android/files/
 *     workspace/     <- Agent's working directory (full read/write)
 *     shared/        <- Incoming shared media (read-only to agent, written by share receiver)
 */
class SandboxedFileSystem(context: Context) {

    val workspaceDir: File = File(context.getExternalFilesDir(null), "workspace").apply { mkdirs() }
    val sharedDir: File = File(context.getExternalFilesDir(null), "shared").apply { mkdirs() }

    /** Resolve a path relative to workspace, rejecting any escape attempts. */
    fun resolve(relativePath: String): Result<File> {
        val cleaned = relativePath
            .replace("\\", "/")
            .trimStart('/')

        val resolved = File(workspaceDir, cleaned).canonicalFile

        return if (resolved.path.startsWith(workspaceDir.canonicalPath)) {
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

    /** List files in workspace. */
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

    /** Get workspace disk usage. */
    fun getUsage(): DiskUsage {
        val totalSize = workspaceDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fileCount = workspaceDir.walkTopDown().filter { it.isFile }.count()
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
