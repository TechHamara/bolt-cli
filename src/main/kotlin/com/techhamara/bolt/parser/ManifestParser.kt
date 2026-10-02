package com.techhamara.bolt.parser

import java.io.File
import java.util.regex.Pattern

/**
 * Parses and extracts components, permissions, and shorthand class names from AndroidManifest.xml.
 */
class ManifestParser {

    data class FeatureInfo(
        val name: String,
        val required: Boolean = true,
        val glEsVersion: String? = null
    )

    data class ManifestData(
        val packageName: String,
        val permissions: List<String>,
        val activities: List<String>,
        val activityAliases: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val providers: List<String>,
        val application: List<String>,
        val libraries: List<String>,
        val nativeLibraries: List<String>,
        val features: List<FeatureInfo> = emptyList(),
        val queries: List<String> = emptyList(),
        val allApplicationElements: List<String> = emptyList()
    )

    fun parse(manifestFile: File, fallbackPackageName: String = "com.example.extension"): ManifestData {
        if (!manifestFile.exists()) {
            return ManifestData(fallbackPackageName, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        try {
            val rawText = manifestFile.readText(Charsets.UTF_8)
            // CRITICAL: Strip all XML comments first so that commented-out example tags are never parsed!
            val manifestText = rawText.replace(Regex("""<!--[\s\S]*?-->"""), "")

            val pkgRegex = Regex("""package\s*=\s*"([^"]+)"""")
            val packageName = pkgRegex.find(manifestText)?.groupValues?.get(1) ?: fallbackPackageName

            // Extract permissions
            val permRegex = Regex("""<uses-permission[^>]+android:name="([^"]+)"[^>]*>""")
            val permissions = permRegex.findAll(manifestText)
                .map { it.groupValues[1].replace("\${applicationId}", "%packageName%") }
                .distinct()
                .toList()

            // Extract features (<uses-feature>)
            val featureRegex = Regex("""<uses-feature([^>]+)/>|<uses-feature([^>]+)>.*?</uses-feature>""", RegexOption.DOT_MATCHES_ALL)
            val features = mutableListOf<FeatureInfo>()
            for (match in featureRegex.findAll(manifestText)) {
                val attrs = match.groups[1]?.value ?: match.groups[2]?.value ?: ""
                val nameMatch = Regex("""android:name="([^"]+)"""").find(attrs)
                val reqMatch = Regex("""android:required="([^"]+)"""").find(attrs)
                val glMatch = Regex("""android:glEsVersion="([^"]+)"""").find(attrs)

                val name = nameMatch?.groupValues?.get(1) ?: ""
                val required = reqMatch?.groupValues?.get(1)?.lowercase() != "false"
                val glEs = glMatch?.groupValues?.get(1)

                if (name.isNotEmpty() || glEs != null) {
                    features.add(FeatureInfo(name, required, glEs))
                }
            }

            // Extract queries (<queries>)
            val queriesRegex = Regex("""<queries>(.*?)</queries>""", RegexOption.DOT_MATCHES_ALL)
            val queries = mutableListOf<String>()
            for (match in queriesRegex.findAll(manifestText)) {
                val inner = match.groups[1]?.value?.trim() ?: ""
                if (inner.isNotEmpty()) {
                    // Expand shorthand inside queries
                    var cleanInner = inner.replace(Regex("""android:name="\.\.\.([^"]+)""""), "android:name=\"$packageName.\$1\"")
                    cleanInner = cleanInner.replace(Regex("""android:name="\.([^"]+)""""), "android:name=\"$packageName.\$1\"")
                    cleanInner = cleanInner.replace("\${applicationId}", "%packageName%")
                    queries.add(cleanInner)
                }
            }

            // Extract elements inside <application>
            val elementsRegex = Regex(
                """<(activity|activity-alias|service|receiver|provider|profileable|uses-library|uses-native-library|meta-data)([^>]*)>(.*?)</\1>|<(activity|activity-alias|service|receiver|provider|profileable|uses-library|uses-native-library|meta-data)([^>]*)/>""",
                RegexOption.DOT_MATCHES_ALL
            )

            val activities = mutableListOf<String>()
            val aliases = mutableListOf<String>()
            val services = mutableListOf<String>()
            val receivers = mutableListOf<String>()
            val providers = mutableListOf<String>()
            val application = mutableListOf<String>()
            val libraries = mutableListOf<String>()
            val nativeLibraries = mutableListOf<String>()
            val allAppElements = mutableListOf<String>()

            for (match in elementsRegex.findAll(manifestText)) {
                val tag = if (match.groups[1] != null) match.groups[1]!!.value else match.groups[4]!!.value
                var content = match.value.trim()

                // Resolve shorthand class names and package placeholders
                content = content.replace(Regex("""android:name="\.\.\.([^"]+)""""), "android:name=\"$packageName.\$1\"")
                content = content.replace(Regex("""android:name="\.([^"]+)""""), "android:name=\"$packageName.\$1\"")
                content = content.replace(Regex("""android:targetActivity="\.\.\.([^"]+)""""), "android:targetActivity=\"$packageName.\$1\"")
                content = content.replace(Regex("""android:targetActivity="\.([^"]+)""""), "android:targetActivity=\"$packageName.\$1\"")
                content = content.replace("\${applicationId}", "%packageName%")

                when (tag) {
                    "activity" -> activities.add(content)
                    "activity-alias" -> aliases.add(content)
                    "service" -> services.add(content)
                    "receiver" -> receivers.add(content)
                    "provider" -> providers.add(content)
                    "uses-library" -> libraries.add(content)
                    "uses-native-library" -> nativeLibraries.add(content)
                    "profileable", "meta-data" -> application.add(content)
                }
                allAppElements.add(content)
            }

            return ManifestData(
                packageName = packageName,
                permissions = permissions,
                activities = activities,
                activityAliases = aliases,
                services = services,
                receivers = receivers,
                providers = providers,
                application = application,
                libraries = libraries,
                nativeLibraries = nativeLibraries,
                features = features,
                queries = queries,
                allApplicationElements = allAppElements
            )
        } catch (e: Exception) {
            return ManifestData(fallbackPackageName, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
}
