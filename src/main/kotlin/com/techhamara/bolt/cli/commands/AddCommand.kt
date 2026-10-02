package com.techhamara.bolt.cli.commands

import com.google.gson.JsonParser
import com.techhamara.bolt.cli.Logger
import com.techhamara.bolt.cli.green
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AddCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        val depArg = args.firstOrNull { !it.startsWith("-") }
        if (depArg.isNullOrBlank()) {
            logger.err("Please provide a dependency to add. Format: groupId:artifactId or groupId:artifactId:version")
            return 1
        }

        val parts = depArg.split(":")
        val groupId: String
        val artifactId: String
        var version: String? = null

        when (parts.size) {
            2 -> {
                groupId = parts[0]
                artifactId = parts[1]
            }
            3 -> {
                groupId = parts[0]
                artifactId = parts[1]
                version = parts[2]
            }
            else -> {
                logger.err("Invalid dependency format. Use groupId:artifactId or groupId:artifactId:version")
                return 1
            }
        }

        if (version == null) {
            logger.startTask("Searching Maven Central for $groupId:$artifactId")
            try {
                val query = "g:\"$groupId\" AND a:\"$artifactId\""
                val url = URL("https://search.maven.org/solrsearch/select?q=${URLEncoder.encode(query, "UTF-8")}&rows=1&wt=json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Bolt-CLI")

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JsonParser.parseString(jsonText).asJsonObject
                    val docs = root.getAsJsonObject("response").getAsJsonArray("docs")
                    if (docs.isEmpty) {
                        logger.stopTask(false)
                        logger.err("Dependency not found on Maven Central.")
                        return 1
                    }
                    version = docs.first().asJsonObject.get("latestVersion").asString
                    logger.stopTask(true)
                    logger.info("Found latest version: $version")
                } else {
                    logger.stopTask(false)
                    logger.err("Failed to query Maven Central: HTTP ${conn.responseCode}")
                    return 1
                }
            } catch (e: Exception) {
                logger.stopTask(false)
                logger.err("Network error querying Maven Central: ${e.message}")
                return 1
            }
        }

        val coordinate = "$groupId:$artifactId:$version"
        val projectDir = File(".").canonicalFile
        val configFile = File(projectDir, "bolt.yml")

        if (!configFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        val content = configFile.readText(Charsets.UTF_8)
        val depsRegex = Regex("""^(\s*)dependencies:\s*$""", RegexOption.MULTILINE)

        val newContent = if (depsRegex.containsMatchIn(content)) {
            content.replace(depsRegex) { matchResult ->
                val indent = matchResult.groupValues[1]
                "${matchResult.value}\n$indent  - $coordinate"
            }
        } else {
            "$content\n\ndependencies:\n  - $coordinate\n"
        }

        configFile.writeText(newContent, Charsets.UTF_8)
        logger.info("Added '${coordinate.green()}' to bolt.yml")

        logger.info("Syncing dependencies...")
        return SyncCommand.execute(listOf(), logger)
    }
}
