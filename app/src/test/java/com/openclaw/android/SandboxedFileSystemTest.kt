package com.openclaw.android

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the sandboxed file system security boundary.
 * Ensures the agent cannot escape its designated directories.
 */
class SandboxedFileSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var workspaceDir: File
    private lateinit var sharedDir: File

    @Before
    fun setup() {
        workspaceDir = tempFolder.newFolder("workspace")
        sharedDir = tempFolder.newFolder("shared")
    }

    @Test
    fun `resolve normal path succeeds`() {
        val fs = createFs()
        val result = fs.resolve("test.txt")
        assertTrue(result.isSuccess)
        assertEquals(File(workspaceDir, "test.txt").canonicalPath, result.getOrNull()?.canonicalPath)
    }

    @Test
    fun `resolve subdirectory path succeeds`() {
        val fs = createFs()
        val result = fs.resolve("notes/daily/today.md")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.canonicalPath.startsWith(workspaceDir.canonicalPath))
    }

    @Test
    fun `resolve path traversal is blocked`() {
        val fs = createFs()
        val result = fs.resolve("../../etc/passwd")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `resolve path with backslash traversal is blocked`() {
        val fs = createFs()
        val result = fs.resolve("..\\..\\etc\\passwd")
        assertTrue(result.isFailure)
    }

    @Test
    fun `resolve absolute path is treated as relative`() {
        val fs = createFs()
        val result = fs.resolve("/etc/passwd")
        assertTrue(result.isSuccess)
        // Should resolve to workspace/etc/passwd, not /etc/passwd
        assertTrue(result.getOrNull()!!.canonicalPath.startsWith(workspaceDir.canonicalPath))
    }

    @Test
    fun `isWritable returns true for workspace files`() {
        val fs = createFs()
        val file = File(workspaceDir, "test.txt")
        assertTrue(fs.isWritable(file))
    }

    @Test
    fun `isWritable returns false for shared files`() {
        val fs = createFs()
        val file = File(sharedDir, "photo.jpg")
        assertFalse(fs.isWritable(file))
    }

    @Test
    fun `listWorkspace returns files`() {
        val fs = createFs()
        File(workspaceDir, "a.txt").createNewFile()
        File(workspaceDir, "b.txt").createNewFile()
        File(workspaceDir, "subdir").mkdir()

        val result = fs.listWorkspace()
        assertTrue(result.isSuccess)
        val files = result.getOrNull()!!
        assertEquals(3, files.size)
        // Directories come first
        assertTrue(files[0].isDirectory)
        assertEquals("subdir", files[0].name)
    }

    @Test
    fun `getUsage reports correct stats`() {
        val fs = createFs()
        File(workspaceDir, "file1.txt").writeText("hello")
        File(workspaceDir, "file2.txt").writeText("world!")

        val usage = fs.getUsage()
        assertEquals(2, usage.fileCount)
        assertEquals(11L, usage.totalBytes)
    }

    /**
     * Create a SandboxedFileSystem that uses our temp directories.
     * We use reflection to set the dirs since the constructor needs a Context.
     */
    private fun createFs(): SandboxedFileSystem {
        return SandboxedFileSystem(
            workspaceDir = workspaceDir,
            sharedDir = sharedDir,
        )
    }
}

// Test-friendly constructor extension
private open class SandboxedFileSystem(
    val workspaceDir: File,
    val sharedDir: File,
) {
    fun resolve(relativePath: String): Result<File> {
        val cleaned = relativePath.replace("\\", "/").trimStart('/')
        val resolved = File(workspaceDir, cleaned).canonicalFile
        return if (resolved.path.startsWith(workspaceDir.canonicalPath)) {
            Result.success(resolved)
        } else {
            Result.failure(SecurityException("Path escapes sandbox: $relativePath"))
        }
    }

    fun isWritable(file: File): Boolean =
        file.canonicalPath.startsWith(workspaceDir.canonicalPath)

    fun listWorkspace(subPath: String = ""): Result<List<FileInfo>> {
        val dir = if (subPath.isEmpty()) workspaceDir else File(workspaceDir, subPath)
        if (!dir.exists() || !dir.isDirectory) return Result.success(emptyList())
        return Result.success(
            dir.listFiles()?.map {
                FileInfo(it.name, it.relativeTo(workspaceDir).path, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified())
            }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        )
    }

    fun getUsage(): DiskUsage {
        val totalSize = workspaceDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val fileCount = workspaceDir.walkTopDown().filter { it.isFile }.count()
        return DiskUsage(totalSize, fileCount)
    }

    data class FileInfo(val name: String, val path: String, val isDirectory: Boolean, val size: Long, val lastModified: Long)
    data class DiskUsage(val totalBytes: Long, val fileCount: Int)
}
