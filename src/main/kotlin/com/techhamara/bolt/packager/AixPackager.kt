package com.techhamara.bolt.packager

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Creates valid, specification-compliant App Inventor Extension (.aix) packages.
 */
object AixPackager {

    fun zipDirectory(rootDir: File, destZipFile: File, excludeExtensions: Set<String> = emptySet()) {
        destZipFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
            zipFolder(rootDir, rootDir, zos, excludeExtensions)
        }
    }

    private fun zipFolder(rootDir: File, currentDir: File, zos: ZipOutputStream, excludeExtensions: Set<String> = emptySet()) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipFolder(rootDir, file, zos, excludeExtensions)
            } else {
                if (excludeExtensions.contains(file.extension.lowercase())) continue
                val relPath = rootDir.toURI().relativize(file.toURI()).path.replace('\\', '/')
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos, 64 * 1024)
                }
                zos.closeEntry()
            }
        }
    }

    fun extractAarClasses(aarFile: File, destJarFile: File): Boolean {
        try {
            ZipInputStream(FileInputStream(aarFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "classes.jar") {
                        destJarFile.parentFile?.mkdirs()
                        FileOutputStream(destJarFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        return true
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            // Ignore error
        }
        return false
    }

    fun unzipClassesOnly(zipFile: File, destDir: File) {
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        val outFile = File(destDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun zipSingleFile(sourceFile: File, entryName: String, destZipFile: File) {
        destZipFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zos ->
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(sourceFile).use { fis ->
                fis.copyTo(zos, 64 * 1024)
            }
            zos.closeEntry()
        }
    }
}
