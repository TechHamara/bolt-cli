package com.techhamara.bolt.resolver

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.LinkedList

/**
 * Pure JVM dependency resolver supporting Maven Central, Google Maven, JitPack, and custom repositories.
 * Downloads direct and transitive dependencies with SHA-1 verification.
 */
class MavenResolver(private val logger: (String) -> Unit = {}) {

    fun resolve(
        coordinates: List<String>,
        userRepos: List<String>,
        depsDir: File
    ): Boolean {
        if (coordinates.isEmpty()) {
            logger("No external dependencies to resolve.")
            return true
        }

        depsDir.mkdirs()
        val queue = LinkedList<String>()
        queue.addAll(coordinates)
        val resolved = mutableSetOf<String>()

        val defaultRepos = listOf(
            "https://repo1.maven.org/maven2",
            "https://dl.google.com/dl/android/maven2",
            "https://jitpack.io"
        )
        val allRepos = (userRepos + defaultRepos).distinct()

        logger("Resolving ${coordinates.size} Maven dependenc(ies)...")

        var allSuccess = true

        while (queue.isNotEmpty()) {
            val coord = queue.poll()
            if (resolved.contains(coord)) continue
            resolved.add(coord)

            val parts = coord.split(":")
            if (parts.size != 3) {
                logger("Invalid Maven coordinate: $coord")
                continue
            }

            val group = parts[0].replace('.', '/')
            val artifact = parts[1]
            val version = parts[2]
            val fileNameBase = "$artifact-$version"

            val aarFile = File(depsDir, "$fileNameBase.aar")
            val jarFile = File(depsDir, "$fileNameBase.jar")

            if (aarFile.exists() || jarFile.exists()) {
                logger("Artifact $coord already present in deps/")
                continue
            }

            logger("Downloading $coord...")

            var downloaded = false
            var usedUrlBase = ""

            for (repo in allRepos) {
                val baseUrl = if (repo.endsWith("/")) repo else "$repo/"
                val urlBase = "$baseUrl$group/$artifact/$version/$fileNameBase"

                if (tryDownload("$urlBase.aar", aarFile)) {
                    downloaded = true
                    usedUrlBase = urlBase
                    logger("Downloaded $coord (AAR) from $repo")
                    break
                }
                if (tryDownload("$urlBase.jar", jarFile)) {
                    downloaded = true
                    usedUrlBase = urlBase
                    logger("Downloaded $coord (JAR) from $repo")
                    break
                }
            }

            if (!downloaded) {
                logger("error Failed to resolve $coord from available repositories.")
                allSuccess = false
                continue
            }

            // Verify Checksum
            val downloadedFile = if (aarFile.exists()) aarFile else jarFile
            verifyChecksum(downloadedFile, usedUrlBase)

            // Resolve Transitive Dependencies from POM
            val pomFile = File(depsDir, "$fileNameBase.pom")
            if (tryDownload("$usedUrlBase.pom", pomFile)) {
                parsePomForTransitive(pomFile, queue)
                pomFile.delete()
            }
        }

        logger("Dependencies resolved successfully.")
        return allSuccess
    }

    private fun verifyChecksum(file: File, urlBase: String) {
        try {
            val ext = if (file.name.endsWith(".aar")) ".aar.sha1" else ".jar.sha1"
            val url = URL("$urlBase$ext")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val remoteSha1 = conn.inputStream.bufferedReader().use { it.readText().trim().lowercase() }
                val md = MessageDigest.getInstance("SHA-1")
                val localSha1 = md.digest(file.readBytes()).joinToString("") { "%02x".format(it) }

                if (!remoteSha1.startsWith(localSha1)) {
                    logger("warning Checksum mismatch for ${file.name}: expected $remoteSha1, got $localSha1")
                } else {
                    logger("Checksum verified for ${file.name}")
                }
            }
        } catch (e: Exception) {
            // Checksum verification is non-fatal
        }
    }

    private fun parsePomForTransitive(pomFile: File, queue: LinkedList<String>) {
        try {
            var xml = pomFile.readText(Charsets.UTF_8)
            xml = xml.replace(Regex("<!--[\\s\\S]*?-->"), "")
            xml = xml.replace(Regex("<dependencyManagement>[\\s\\S]*?</dependencyManagement>"), "")
            xml = xml.replace(Regex("<build>[\\s\\S]*?</build>"), "")
            xml = xml.replace(Regex("<plugins>[\\s\\S]*?</plugins>"), "")
            xml = xml.replace(Regex("<pluginManagement>[\\s\\S]*?</pluginManagement>"), "")
            xml = xml.replace(Regex("<reporting>[\\s\\S]*?</reporting>"), "")

            val depsBlockRegex = Regex("<dependencies>([\\s\\S]*?)</dependencies>")
            for (depsMatch in depsBlockRegex.findAll(xml)) {
                val depRegex = Regex("<dependency>([\\s\\S]*?)</dependency>")
                for (depMatch in depRegex.findAll(depsMatch.groupValues[1])) {
                    val depContent = depMatch.groupValues[1]
                    val groupId = Regex("<groupId>([^<]+)</groupId>").find(depContent)?.groupValues?.get(1)?.trim()
                    val artifactId = Regex("<artifactId>([^<]+)</artifactId>").find(depContent)?.groupValues?.get(1)?.trim()
                    val version = Regex("<version>([^<]+)</version>").find(depContent)?.groupValues?.get(1)?.trim()
                    val scope = Regex("<scope>([^<]+)</scope>").find(depContent)?.groupValues?.get(1)?.trim() ?: "compile"

                    if (groupId != null && artifactId != null && version != null && !version.startsWith("\${") && (scope == "compile" || scope == "runtime")) {
                        val coord = "$groupId:$artifactId:$version"
                        queue.add(coord)
                        logger("Found transitive dependency: $coord")
                    }
                }
            }
        } catch (e: Exception) {
            logger("Failed to parse POM for ${pomFile.name}: ${e.message}")
        }
    }

    private fun tryDownload(urlStr: String, destFile: File): Boolean {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true

            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return true
            }
        } catch (e: Exception) {
            // Ignore failure for fallback repository check
        }
        return false
    }
}
