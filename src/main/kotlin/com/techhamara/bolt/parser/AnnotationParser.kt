package com.techhamara.bolt.parser

import com.google.gson.GsonBuilder
import java.io.File
import java.util.regex.Pattern

/**
 * Pure Kotlin/JVM annotation parser for MIT App Inventor, Kodular, and Niotron extensions.
 * Extracts metadata from Java and Kotlin source files without requiring Android SDK or KAPT.
 */
class AnnotationParser {

    private fun getYailType(type: String): String? {
        val t = type.replace("?", "").trim()
        val simple = if (t.contains(".")) t.substringAfterLast(".") else t
        return when (simple) {
            "void", "Unit" -> null
            "int", "Integer", "float", "Float", "double", "Double", "long", "Long", "short", "Short", "byte", "Byte" -> "number"
            "String", "char", "Character", "CharSequence" -> "text"
            "boolean", "Boolean" -> "boolean"
            "List", "YailList", "ArrayList", "Collection" -> "list"
            "Map", "HashMap", "YailDictionary" -> "dictionary"
            "Object", "Any" -> "any"
            "Component", "AndroidViewComponent", "AndroidNonvisibleComponent", "ComponentContainer",
            "VisibleComponent", "HVArrangement", "HorizontalArrangement", "VerticalArrangement",
            "TableArrangement" -> "component"
            else -> {
                if (simple.endsWith("Component") || simple.endsWith("Arrangement") || simple.endsWith("Container") || simple.endsWith("Layout") || simple.endsWith("View")) {
                    "component"
                } else {
                    "any"
                }
            }
        }
    }

    data class OptionItem(
        val deprecated: String = "false",
        val name: String,
        val description: String = "",
        val value: String
    )

    data class OptionHelperData(
        val defaultOpt: String,
        val underlyingType: String,
        val options: List<OptionItem>,
        val className: String,
        val tag: String,
        val key: String
    )

    data class PropertyHelper(
        val data: OptionHelperData,
        val type: String = "OPTION_LIST"
    )

    data class HelperEnumInfo(
        val name: String,
        val packageName: String,
        val underlyingType: String,
        val options: List<OptionItem>
    ) {
        val fqcn: String get() = if (packageName.isNotEmpty()) "$packageName.$name" else name
        val values: List<String> get() = options.map { it.name }
        fun toPropertyHelper(): PropertyHelper {
            val uType = when (underlyingType) {
                "int", "Integer" -> "java.lang.Integer"
                "String" -> "java.lang.String"
                else -> if (underlyingType.contains(".")) underlyingType else "java.lang.$underlyingType"
            }
            return PropertyHelper(
                data = OptionHelperData(
                    defaultOpt = options.firstOrNull()?.name ?: "",
                    underlyingType = uType,
                    options = options,
                    className = fqcn,
                    tag = name,
                    key = name
                )
            )
        }
    }

    data class Parameter(
        val helper: PropertyHelper? = null,
        val name: String,
        val type: String,
        @Transient val optionsType: String? = null
    )

    data class MethodInfo(
        val deprecated: String = "false",
        val name: String,
        val description: String = "",
        val params: List<Parameter> = emptyList(),
        val returnType: String? = null
    )

    data class EventInfo(
        val deprecated: String = "false",
        val name: String,
        val description: String = "",
        val params: List<Parameter> = emptyList()
    )

    data class DesignerPropertyInfo(
        val defaultValue: String = "",
        val alwaysSend: String = "false",
        val name: String,
        val editorArgs: List<String> = emptyList(),
        val editorType: String = "text"
    )

    data class BlockPropertyInfo(
        val helper: PropertyHelper? = null,
        val rw: String, // "read-only", "write-only", "read-write"
        val deprecated: String = "false",
        val name: String,
        val description: String = "",
        val type: String
    )

    data class SetterPropertyInfo(
        val name: String,
        val description: String = "",
        val type: String,
        val helper: PropertyHelper? = null,
        val deprecated: String = "false"
    )

    data class GetterPropertyInfo(
        val name: String,
        val description: String = "",
        val type: String,
        val helper: PropertyHelper? = null,
        val deprecated: String = "false"
    )

    data class ComponentInfo(
        val categoryString: String = "EXTENSION",
        val dateBuilt: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
        val nonVisible: String = "true",
        val iconName: String = "",
        val methods: List<MethodInfo> = emptyList(),
        val blockProperties: List<BlockPropertyInfo> = emptyList(),
        val helpUrl: String = "",
        val licenseName: String = "",
        val type: String,
        val versionName: String = "1.0",
        val androidMinSdk: Int = 14,
        val version: String = "1",
        val external: String = "true",
        val showOnPalette: String = "true",
        val name: String,
        val helpString: String = "",
        val events: List<EventInfo> = emptyList(),
        val properties: List<DesignerPropertyInfo> = emptyList(),
        @Transient val setters: List<SetterPropertyInfo> = emptyList(),
        @Transient val getters: List<GetterPropertyInfo> = emptyList(),
        @Transient val activities: List<String> = emptyList(),
        @Transient val activityAliases: List<String> = emptyList(),
        @Transient val services: List<String> = emptyList(),
        @Transient val receivers: List<String> = emptyList(),
        @Transient val providers: List<String> = emptyList(),
        @Transient val queries: List<String> = emptyList(),
        @Transient val application: List<String> = emptyList(),
        @Transient val libraries: List<String> = emptyList(),
        @Transient val nativeLibraries: List<String> = emptyList(),
        @Transient val permissions: List<String> = emptyList(),
        @Transient val xmls: List<String> = emptyList(),
        @Transient val rawDescription: String = ""
    )

    fun parseJavaFilesList(srcDir: File): List<ComponentInfo> {
        val components = mutableListOf<ComponentInfo>()
        if (!srcDir.exists()) return components

        val helperEnums = parseHelperEnums(srcDir)

        srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.forEach { file ->
            val content = file.readText(Charsets.UTF_8)
            val component = if (file.extension == "kt") {
                parseKotlinContent(content, helperEnums)
            } else {
                parseJavaContent(content, helperEnums)
            }
            if (component != null) {
                components.add(component)
            }
        }
        return components
    }

    fun parseJavaFiles(srcDir: File): String {
        val components = parseJavaFilesList(srcDir)
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(components)
    }

    fun parseHelperEnums(srcDir: File): List<HelperEnumInfo> {
        val list = mutableListOf<HelperEnumInfo>()
        if (!srcDir.exists()) return list
        srcDir.walkTopDown().filter { it.isFile && (it.extension == "java" || it.extension == "kt") }.forEach { file ->
            val content = file.readText(Charsets.UTF_8)
            list.addAll(parseHelperEnumsFromContent(content))
        }
        return list.distinctBy { it.name }
    }

    fun parseHelperEnumsFromContent(content: String): List<HelperEnumInfo> {
        val list = mutableListOf<HelperEnumInfo>()
        val packageMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);?").matcher(content)
        val packageName = if (packageMatcher.find()) packageMatcher.group(1).trim() else ""

        // Java & Kotlin: enum IntervalType implements OptionList<String> or enum class ModeType : OptionList<Integer>
        val pattern = Pattern.compile("(?:public\\s+)?enum\\s+(?:class\\s+)?([a-zA-Z0-9_]+)[^{]*?(?:implements|:)[^{]*?OptionList<([a-zA-Z0-9_]+)>[^{]*?\\{([^;]+?)(?:;|\\})", Pattern.DOTALL)
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            val enumName = matcher.group(1).trim()
            val underType = matcher.group(2).trim()
            val rawEntries = matcher.group(3)
            val options = mutableListOf<OptionItem>()
            val entryPattern = Pattern.compile("([a-zA-Z0-9_]+)(?:\\s*\\(([^)]*)\\))?")
            val entryMatcher = entryPattern.matcher(rawEntries)
            var index = 0
            while (entryMatcher.find()) {
                val valName = entryMatcher.group(1).trim()
                if (valName.isNotEmpty() && valName != "public" && valName != "private" && valName != "override" && valName != "val" && valName != "var") {
                    val rawVal = entryMatcher.group(2)?.trim()?.removeSurrounding("\"")
                    val finalVal = if (!rawVal.isNullOrEmpty()) rawVal else index.toString()
                    options.add(OptionItem(
                        deprecated = "false",
                        name = valName,
                        description = "Option for $valName",
                        value = finalVal
                    ))
                    index++
                }
            }
            if (enumName.isNotEmpty()) {
                list.add(HelperEnumInfo(enumName, packageName, underType, options))
            }
        }
        return list
    }

    fun generateMarkdown(
        components: List<ComponentInfo>,
        extAuthor: String = "Unknown",
        helperEnums: List<HelperEnumInfo> = emptyList(),
        boltVersion: String = "2.0.0",
        buildTime: String = "",
        homepage: String = ""
    ): String {
        val sb = java.lang.StringBuilder()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val currentDate = dateFormat.format(java.util.Date())

        for (comp in components) {
            val simpleName = if (comp.name.contains(".")) comp.name.substringAfterLast(".") else comp.name
            val packageName = if (comp.type.contains(".")) comp.type.substringBeforeLast(".") else ""
            val author = extAuthor

            sb.append("<div align=\"center\">\n")
            sb.append("<h1><kbd>🧩 $simpleName</kbd></h1>\n")
            val desc = if (comp.rawDescription.isNotEmpty()) comp.rawDescription else comp.helpString.replace(Regex("<[^>]*>"), "").trim()
            if (desc.isNotEmpty()) {
                sb.append("$desc<br>\n\n")
            }
            sb.append("</div>\n\n")

            sb.append("## 📝 Specifications\n")
            sb.append("👤 **Author:** $author\n")
            sb.append("📦 **Package:** $packageName\n")
            val versionStr = if (comp.versionName.isNotBlank()) comp.versionName else comp.version
            sb.append("⚙️ **Version:** $versionStr\n")
            sb.append("💾 **Size:** [Calculate automatically during build]\n")
            sb.append("📱 **Minimum API Level:** ${comp.androidMinSdk}\n")
            if (buildTime.isNotBlank()) {
                sb.append("⏰ **Build Time:** $buildTime\n")
            }
            if (boltVersion.isNotBlank()) {
                sb.append("⚡ **BOLT Version:** $boltVersion\n")
            }
            if (homepage.isNotBlank()) {
                sb.append("🌐 **Homepage:** [Link]($homepage)\n")
            }
            sb.append("📅 **Updated On:** $currentDate\n")
            sb.append("💻 **Built & documented using:** [Bolt CLI](https://github.com/TechHamara/bolt-cli)\n\n")
            sb.append("---\n\n")

            // 1. Events
            if (comp.events.isNotEmpty()) {
                sb.append("## <kbd>Events:</kbd>\n")
                sb.append("**$simpleName** has total ${comp.events.size} events.\n\n")
                for (event in comp.events) {
                    sb.append("### ${event.name}\n")
                    if (event.deprecated == "true") {
                        sb.append("> ⚠️ **Deprecated**\n\n")
                    }
                    if (event.description.isNotEmpty()) {
                        sb.append("${event.description}\n\n")
                    }
                    if (event.params.isNotEmpty()) {
                        sb.append("| Parameter | Type\n")
                        sb.append("| - | - |\n")
                        val helperEnumsToPrint = mutableListOf<Pair<String, List<String>>>()
                        for (param in event.params) {
                            val helperTag = param.helper?.data?.tag?.ifEmpty { param.helper.data.key } ?: param.optionsType
                            if (helperTag != null) {
                                val matched = param.helper?.data?.options?.map { it.name }
                                    ?: helperEnums.firstOrNull { it.name == helperTag || it.fqcn == helperTag }?.values
                                if (matched != null) {
                                    sb.append("| ${param.name} | $helperTag <small><mark>(helper blocks)</mark></small>\n")
                                    if (helperEnumsToPrint.none { it.first == helperTag }) {
                                        helperEnumsToPrint.add(helperTag to matched)
                                    }
                                } else {
                                    val displayType = if (param.type.contains(".")) param.type.substringAfterLast(".") else param.type
                                    sb.append("| ${param.name} | $displayType <br>*(Options: $helperTag)*\n")
                                }
                            } else {
                                val displayType = if (param.type.contains(".")) param.type.substringAfterLast(".") else param.type
                                sb.append("| ${param.name} | $displayType\n")
                            }
                        }
                        sb.append("\n")
                        for ((hName, hValues) in helperEnumsToPrint) {
                            val optsStr = hValues.joinToString(", ") { "`$it`" }
                            sb.append("* Enums for **$hName**: $optsStr\n\n")
                        }
                    }
                }
            }

            // 2. Methods
            if (comp.methods.isNotEmpty()) {
                sb.append("## <kbd>Methods:</kbd>\n")
                sb.append("**$simpleName** has total ${comp.methods.size} methods.\n\n")
                for (method in comp.methods) {
                    sb.append("### ${method.name}\n")
                    if (method.deprecated == "true") {
                        sb.append("> ⚠️ **Deprecated**\n\n")
                    }
                    if (method.description.isNotEmpty()) {
                        sb.append("${method.description}\n\n")
                    }
                    if (method.params.isNotEmpty()) {
                        sb.append("| Parameter | Type\n")
                        sb.append("| - | - |\n")
                        val helperEnumsToPrint = mutableListOf<Pair<String, List<String>>>()
                        for (param in method.params) {
                            val helperTag = param.helper?.data?.tag?.ifEmpty { param.helper.data.key } ?: param.optionsType
                            if (helperTag != null) {
                                val matched = param.helper?.data?.options?.map { it.name }
                                    ?: helperEnums.firstOrNull { it.name == helperTag || it.fqcn == helperTag }?.values
                                if (matched != null) {
                                    sb.append("| ${param.name} | $helperTag <small><mark>(helper blocks)</mark></small>\n")
                                    if (helperEnumsToPrint.none { it.first == helperTag }) {
                                        helperEnumsToPrint.add(helperTag to matched)
                                    }
                                } else {
                                    val displayType = if (param.type.contains(".")) param.type.substringAfterLast(".") else param.type
                                    sb.append("| ${param.name} | $displayType <br>*(Options: $helperTag)*\n")
                                }
                            } else {
                                val displayType = if (param.type.contains(".")) param.type.substringAfterLast(".") else param.type
                                sb.append("| ${param.name} | $displayType\n")
                            }
                        }
                        sb.append("\n")
                        for ((hName, hValues) in helperEnumsToPrint) {
                            val optsStr = hValues.joinToString(", ") { "`$it`" }
                            sb.append("* Enums for **$hName**: $optsStr\n\n")
                        }
                    }
                    val retDisplay = method.returnType?.let { if (it.isNotEmpty() && it != "void" && it != "Unit" && it != "none") it else null }
                    if (retDisplay != null) {
                        sb.append("* Return type: `$retDisplay`\n\n")
                    }
                }
            }

            // 3. Designer
            if (comp.properties.isNotEmpty()) {
                sb.append("## <kbd>Designer:</kbd>\n")
                sb.append("**$simpleName** has total ${comp.properties.size} designer properties.\n\n")
                for (prop in comp.properties) {
                    sb.append("### ${prop.name}\n\n")
                    sb.append("* Input type: `${prop.editorType}`\n")
                    if (prop.defaultValue.isNotEmpty()) {
                        sb.append("* Default value: `${prop.defaultValue}`\n")
                    }
                    sb.append("\n")
                }
            }

            // 4. Setters
            val setters = if (comp.setters.isNotEmpty()) {
                comp.setters
            } else {
                comp.blockProperties.filter { it.rw.contains("write") }.map {
                    SetterPropertyInfo(
                        name = it.name,
                        description = it.description,
                        type = it.type,
                        helper = it.helper,
                        deprecated = it.deprecated
                    )
                }
            }
            if (setters.isNotEmpty()) {
                sb.append("## <kbd>Setters:</kbd>\n")
                sb.append("**$simpleName** has total ${setters.size} setter properties.\n\n")
                for (setter in setters) {
                    sb.append("### ${setter.name}\n")
                    if (setter.deprecated == "true") {
                        sb.append("> ⚠️ **Deprecated**\n\n")
                    }
                    if (setter.description.isNotEmpty()) {
                        sb.append("${setter.description}\n\n")
                    }
                    sb.append("* Input type: `${setter.type}`\n")
                    if (setter.helper != null) {
                        val hName = setter.helper.data.tag.ifEmpty { setter.helper.data.key }
                        val opts = setter.helper.data.options.joinToString(", ") { "`${it.name}`" }
                        sb.append("* Helper type: `$hName`\n")
                        sb.append("* Helper enums: $opts\n")
                    }
                    sb.append("\n")
                }
            }

            // 5. Getters
            val getters = if (comp.getters.isNotEmpty()) {
                comp.getters
            } else {
                comp.blockProperties.filter { it.rw.contains("read") }.map {
                    GetterPropertyInfo(
                        name = it.name,
                        description = it.description,
                        type = it.type,
                        helper = it.helper,
                        deprecated = it.deprecated
                    )
                }
            }
            if (getters.isNotEmpty()) {
                sb.append("## <kbd>Getters:</kbd>\n")
                sb.append("**$simpleName** has total ${getters.size} getter properties.\n\n")
                for (getter in getters) {
                    sb.append("### ${getter.name}\n")
                    if (getter.deprecated == "true") {
                        sb.append("> ⚠️ **Deprecated**\n\n")
                    }
                    if (getter.description.isNotEmpty()) {
                        sb.append("${getter.description}\n\n")
                    }
                    sb.append("* Return type: `${getter.type}`\n")
                    if (getter.helper != null) {
                        val hName = getter.helper.data.tag.ifEmpty { getter.helper.data.key }
                        val opts = getter.helper.data.options.joinToString(", ") { "`${it.name}`" }
                        sb.append("* Helper type: `$hName`\n")
                        sb.append("* Helper enums: $opts\n")
                    }
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    private fun parseXmls(content: String): List<String> {
        val xmlsList = mutableListOf<String>()
        val usesXmlsPattern = Pattern.compile("@UsesXmls\\s*\\((.*?)(?:\\)\\s*@|\\)\\s*public|\\)\\s*class|\\)\\s*open|\\Z)", Pattern.DOTALL)
        val usesXmlsMatcher = usesXmlsPattern.matcher(content)
        val validDirPattern = Regex("^(layout|values|drawable|mipmap|xml|color|menu|animator|anim)([a-zA-Z0-9-+_]*)?$")

        while (usesXmlsMatcher.find()) {
            val xmlsBody = usesXmlsMatcher.group(1)
            val xmlElementPattern = Pattern.compile("@?XmlElement\\s*\\(((?:[^()]*|\\([^()]*\\))*)\\)", Pattern.DOTALL)
            val xmlElementMatcher = xmlElementPattern.matcher(xmlsBody)
            while (xmlElementMatcher.find()) {
                val params = xmlElementMatcher.group(1)
                var dir = ""
                val dirMatcher = Pattern.compile("dir\\s*=\\s*\"([^\"]+)\"").matcher(params)
                if (dirMatcher.find()) dir = dirMatcher.group(1).trim()

                var name = ""
                val nameMatcher = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"").matcher(params)
                if (nameMatcher.find()) name = nameMatcher.group(1).trim()

                var xmlContent = ""
                val rawMatcher = Pattern.compile("content\\s*=\\s*\"\"\"(.*?)\"\"\"", Pattern.DOTALL).matcher(params)
                if (rawMatcher.find()) {
                    xmlContent = rawMatcher.group(1)
                } else {
                    val contentParamPattern = Pattern.compile("content\\s*=\\s*([\\s\\S]+?)(?:,\\s*[a-zA-Z0-9_]+\\s*=|\\s*\\Z)")
                    val contentParamMatcher = contentParamPattern.matcher(params)
                    if (contentParamMatcher.find()) {
                        val expr = contentParamMatcher.group(1).trim()
                        val strLiteralPattern = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"")
                        val strMatcher = strLiteralPattern.matcher(expr)
                        val builder = StringBuilder()
                        while (strMatcher.find()) {
                            builder.append(strMatcher.group(1))
                        }
                        xmlContent = builder.toString()
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\t", "\t")
                    }
                }

                if (name.isNotEmpty()) {
                    val cleanDir = if (dir.isNotEmpty() && validDirPattern.matches(dir)) dir else "xml"
                    var cleanName = name.substringAfterLast('/').substringAfterLast('\\')
                    if (cleanName.isNotEmpty() && cleanName[0].isUpperCase()) {
                        cleanName = cleanName[0].lowercaseChar() + cleanName.substring(1)
                    }
                    if (!cleanName.endsWith(".xml")) {
                        cleanName += ".xml"
                    }
                    val entry = "$cleanDir/$cleanName:$xmlContent"
                    if (!xmlsList.contains(entry)) {
                        xmlsList.add(entry)
                    }
                }
            }
        }
        return xmlsList
    }

    private fun parseNativeLibraries(content: String): List<String> {
        val nativeLibrariesList = mutableListOf<String>()
        val usesNativeLibrariesPattern = Pattern.compile("@UsesNativeLibraries\\s*\\(((?:[^()]*|\\([^()]*\\))*)\\)", Pattern.DOTALL)
        val usesNativeLibrariesMatcher = usesNativeLibrariesPattern.matcher(content)
        while (usesNativeLibrariesMatcher.find()) {
            val params = usesNativeLibrariesMatcher.group(1)

            fun extractLibs(paramName: String): List<String> {
                val libs = mutableListOf<String>()
                val p = Pattern.compile("$paramName\\s*=\\s*(?:\\{([^}]*)\\}|\\[([^\\]]*)\\]|arrayOf\\(([^\\)]*)\\)|\"([^\"]*)\")", Pattern.DOTALL)
                val m = p.matcher(params)
                if (m.find()) {
                    val raw = m.group(1) ?: m.group(2) ?: m.group(3) ?: m.group(4) ?: ""
                    val strP = Pattern.compile("\"([^\"]+)\"")
                    val strM = strP.matcher(raw)
                    var foundAny = false
                    while (strM.find()) {
                        foundAny = true
                        val lib = strM.group(1).trim()
                        if (lib.isNotEmpty()) libs.add(lib)
                    }
                    if (!foundAny) {
                        for (item in raw.split(",")) {
                            val trimmed = item.replace("\"", "").trim()
                            if (trimmed.isNotEmpty()) libs.add(trimmed)
                        }
                    }
                }
                return libs
            }

            // 1. libraries (generic, no ABI suffix)
            for (lib in extractLibs("libraries")) {
                if (!nativeLibrariesList.contains(lib)) nativeLibrariesList.add(lib)
            }

            // 2. v7aLibraries / armeabi-v7a -> -v7a
            for (lib in extractLibs("v7aLibraries") + extractLibs("armeabiV7aLibraries") + extractLibs("v7a")) {
                val suffixed = if (lib.endsWith("-v7a")) lib else "$lib-v7a"
                if (!nativeLibrariesList.contains(suffixed)) nativeLibrariesList.add(suffixed)
            }

            // 3. v8aLibraries / arm64-v8a -> -v8a
            for (lib in extractLibs("v8aLibraries") + extractLibs("arm64V8aLibraries") + extractLibs("v8a")) {
                val suffixed = if (lib.endsWith("-v8a")) lib else "$lib-v8a"
                if (!nativeLibrariesList.contains(suffixed)) nativeLibrariesList.add(suffixed)
            }

            // 4. x86_64Libraries / x86_64 -> -x86_64
            for (lib in extractLibs("x86_64Libraries") + extractLibs("x86_64")) {
                val suffixed = if (lib.endsWith("-x86_64")) lib else "$lib-x86_64"
                if (!nativeLibrariesList.contains(suffixed)) nativeLibrariesList.add(suffixed)
            }

            // 5. x86Libraries / x86 -> -x86
            for (lib in extractLibs("x86Libraries") + extractLibs("x86")) {
                val suffixed = if (lib.endsWith("-x86")) lib else "$lib-x86"
                if (!nativeLibrariesList.contains(suffixed)) nativeLibrariesList.add(suffixed)
            }

            // 6. armeabiLibraries -> -armeabi
            for (lib in extractLibs("armeabiLibraries")) {
                val suffixed = if (lib.endsWith("-armeabi")) lib else "$lib-armeabi"
                if (!nativeLibrariesList.contains(suffixed)) nativeLibrariesList.add(suffixed)
            }
        }
        return nativeLibrariesList
    }

    private fun parseJavaContent(content: String, helperEnums: List<HelperEnumInfo> = emptyList()): ComponentInfo? {
        val packageMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_\\.]+)(;|\\s)").matcher(content)
        val packageName = if (packageMatcher.find()) packageMatcher.group(1) else ""

        val classMatcher = Pattern.compile("(?:public\\s+)?class\\s+([a-zA-Z0-9_]+)").matcher(content)
        val className = if (classMatcher.find()) classMatcher.group(1) else return null

        val fullClassName = if (packageName.isEmpty()) className else "$packageName.$className"

        var version = "1"
        var versionName = "1.0"
        var category = "EXTENSION"
        var nonVisible = "true"
        var iconName = ""
        var rawDescription = ""

        val designerCompPattern = Pattern.compile("@(?:[a-zA-Z0-9_.]+\\.)?DesignerComponent\\s*\\((.*?)\\)", Pattern.DOTALL)
        val designerCompMatcher = designerCompPattern.matcher(content)
        if (!designerCompMatcher.find()) {
            return null
        }
        val params = designerCompMatcher.group(1)
        version = getValueForParam(params, "version") ?: "1"
        versionName = getValueForParam(params, "versionName") ?: "1.0"
        category = getValueForParam(params, "category")?.replace("ComponentCategory.", "") ?: "EXTENSION"
        nonVisible = getValueForParam(params, "nonVisible") ?: "true"
        val rawIcon = getValueForParam(params, "iconName") ?: getValueForParam(params, "icon") ?: ""
        iconName = if (rawIcon.isNotEmpty() && !rawIcon.startsWith("aiwebres/")) "aiwebres/$rawIcon" else rawIcon
        rawDescription = getValueForParam(params, "description") ?: ""
        val helpUrl = getValueForParam(params, "helpUrl") ?: ""
        val licenseName = getValueForParam(params, "licenseName") ?: ""
        val helpString = if (rawDescription.isNotBlank()) {
            if (rawDescription.trim().startsWith("<p>") || rawDescription.trim().startsWith("<div>")) {
                "$rawDescription\n"
            } else {
                "<p>$rawDescription</p>\n"
            }
        } else ""

        val xmlsList = parseXmls(content)
        val nativeLibrariesList = parseNativeLibraries(content)

        val methods = mutableListOf<MethodInfo>()
        val designerPropertiesList = mutableListOf<DesignerPropertyInfo>()
        val settersList = mutableListOf<SetterPropertyInfo>()
        val gettersList = mutableListOf<GetterPropertyInfo>()
        val blockPropertiesMap = mutableMapOf<String, MutableList<String>>()
        val blockPropertyDescriptions = mutableMapOf<String, String>()
        val blockPropertyTypes = mutableMapOf<String, String>()
        val blockPropertyDeprecated = mutableMapOf<String, String>()
        val blockPropertyHelpers = mutableMapOf<String, PropertyHelper>()
        val events = mutableListOf<EventInfo>()

        val methodBlockPattern = Pattern.compile(
            "((?:@(?:[a-zA-Z0-9_.]+\\.)?(?:SimpleFunction|SimpleProperty|SimpleEvent|DesignerProperty|Deprecated|Options)(?:\\s*\\((?:[^()]*|\\([^()]*\\))*\\))?\\s*)+)" +
            "(?:public|protected|private|static|final|synchronized|\\s)*\\s+([a-zA-Z0-9_<>,\\.\\[\\]\\s]+?)\\s+([a-zA-Z0-9_]+)\\s*\\(((?:[^()]|\\([^()]*\\))*)\\)",
            Pattern.DOTALL
        )
        val methodMatcher = methodBlockPattern.matcher(content)

        while (methodMatcher.find()) {
            val annotationsBlock = methodMatcher.group(1)
            val rawReturnType = methodMatcher.group(2).trim().split(Regex("\\s+")).last()
            val methodName = methodMatcher.group(3).trim()
            val rawParams = methodMatcher.group(4)

            val isDeprecated = if (annotationsBlock.contains("@Deprecated")) "true" else "false"
            val parameters = parseParameters(rawParams, helperEnums)

            if (annotationsBlock.contains("SimpleFunction")) {
                val funcDesc = extractParamFromAnnotation(annotationsBlock, "SimpleFunction", "description") ?: ""
                methods.add(MethodInfo(
                    deprecated = isDeprecated,
                    name = methodName,
                    description = funcDesc,
                    params = parameters,
                    returnType = getYailType(rawReturnType)
                ))
            }

            if (annotationsBlock.contains("SimpleEvent")) {
                val eventDesc = extractParamFromAnnotation(annotationsBlock, "SimpleEvent", "description") ?: ""
                events.add(EventInfo(
                    deprecated = isDeprecated,
                    name = methodName,
                    description = eventDesc,
                    params = parameters
                ))
            }

            if (annotationsBlock.contains("DesignerProperty")) {
                val defaultVal = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "defaultValue") ?: ""
                val editorType = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorType") ?: "text"
                val alwaysSend = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "alwaysSend") ?: "false"
                val rawEditorArgs = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorArgs")
                val editorArgsList = if (!rawEditorArgs.isNullOrBlank()) {
                    rawEditorArgs.replace("{", "").replace("}", "").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                } else emptyList()

                designerPropertiesList.add(DesignerPropertyInfo(
                    defaultValue = defaultVal,
                    alwaysSend = alwaysSend,
                    name = methodName,
                    editorArgs = editorArgsList,
                    editorType = editorType
                ))
            }

            if (annotationsBlock.contains("SimpleProperty")) {
                val propDesc = extractParamFromAnnotation(annotationsBlock, "SimpleProperty", "description") ?: ""
                val isGetter = rawReturnType != "void" && parameters.isEmpty()
                val isSetter = rawReturnType == "void" && parameters.size == 1

                val propType = if (isGetter) (getYailType(rawReturnType) ?: rawReturnType) else if (isSetter && parameters.isNotEmpty()) parameters[0].type else "text"
                blockPropertyTypes[methodName] = propType
                if (propDesc.isNotEmpty()) {
                    blockPropertyDescriptions[methodName] = propDesc
                }
                if (isDeprecated == "true") {
                    blockPropertyDeprecated[methodName] = "true"
                }

                var helper: PropertyHelper? = null
                if (isSetter && parameters.isNotEmpty() && parameters[0].optionsType != null) {
                    val optName = parameters[0].optionsType!!
                    val matchedHelper = helperEnums.firstOrNull { it.name == optName || it.fqcn == optName }
                    if (matchedHelper != null) {
                        helper = matchedHelper.toPropertyHelper()
                        blockPropertyHelpers[methodName] = helper
                    }
                }

                if (isGetter) {
                    blockPropertiesMap.getOrPut(methodName) { mutableListOf() }.add("read")
                    gettersList.add(
                        GetterPropertyInfo(
                            name = methodName,
                            description = propDesc,
                            type = propType,
                            helper = helper ?: blockPropertyHelpers[methodName],
                            deprecated = isDeprecated
                        )
                    )
                }
                if (isSetter) {
                    blockPropertiesMap.getOrPut(methodName) { mutableListOf() }.add("write")
                    settersList.add(
                        SetterPropertyInfo(
                            name = methodName,
                            description = propDesc,
                            type = propType,
                            helper = helper,
                            deprecated = isDeprecated
                        )
                    )
                }
            }
        }

        val finalizedGetters = gettersList.map { getter ->
            if (getter.helper == null && blockPropertyHelpers.containsKey(getter.name)) {
                getter.copy(helper = blockPropertyHelpers[getter.name])
            } else {
                getter
            }
        }

        val blockPropertiesList = blockPropertiesMap.map { (name, modes) ->
            val rw = if (modes.contains("read") && modes.contains("write")) {
                "read-write"
            } else if (modes.contains("read")) {
                "read-only"
            } else {
                "write-only"
            }
            BlockPropertyInfo(
                helper = blockPropertyHelpers[name],
                rw = rw,
                deprecated = blockPropertyDeprecated[name] ?: "false",
                name = name,
                description = blockPropertyDescriptions[name] ?: "",
                type = blockPropertyTypes[name] ?: "text"
            )
        }

        return ComponentInfo(
            categoryString = category,
            dateBuilt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
            nonVisible = nonVisible,
            iconName = iconName,
            methods = methods,
            blockProperties = blockPropertiesList,
            helpUrl = helpUrl,
            licenseName = licenseName,
            type = fullClassName,
            versionName = versionName,
            androidMinSdk = 14,
            version = version,
            external = "true",
            showOnPalette = "true",
            name = className,
            helpString = helpString,
            events = events,
            properties = designerPropertiesList,
            setters = settersList,
            getters = finalizedGetters,
            xmls = xmlsList,
            nativeLibraries = nativeLibrariesList,
            rawDescription = rawDescription
        )
    }

    private fun extractParamFromAnnotation(block: String, annotationName: String, param: String): String? {
        val simpleName = annotationName.removePrefix("@")
        val p = Pattern.compile("@(?:[a-zA-Z0-9_.]+\\.)?$simpleName\\s*\\(((?:[^()]*|\\([^()]*\\))*)\\)", Pattern.DOTALL)
        val m = p.matcher(block)
        if (m.find()) {
            return getValueForParam(m.group(1), param)
        }
        return null
    }

    private fun parseParameters(rawParams: String, helperEnums: List<HelperEnumInfo> = emptyList()): List<Parameter> {
        if (rawParams.trim().isEmpty()) return emptyList()
        return rawParams.split(",").map { param ->
            var trimmed = param.trim()

            var optionsType: String? = null
            val optionsPattern = Pattern.compile("@Options\\s*\\(\\s*([^\\)]+)\\s*\\)")
            val matcher = optionsPattern.matcher(trimmed)
            if (matcher.find()) {
                optionsType = matcher.group(1)?.replace(".class", "")?.replace("::class", "")?.trim()
                trimmed = matcher.replaceFirst("").trim()
            }

            val matchedHelper = if (optionsType != null) {
                helperEnums.firstOrNull { it.name == optionsType || it.fqcn == optionsType }?.toPropertyHelper()
            } else null

            val spaceIndex = trimmed.lastIndexOf(' ')
            if (spaceIndex != -1) {
                val type = trimmed.substring(0, spaceIndex).trim().split(Regex("\\s+")).last()
                val name = trimmed.substring(spaceIndex + 1).trim()
                Parameter(matchedHelper, name, getYailType(type) ?: "any", optionsType)
            } else {
                Parameter(matchedHelper, trimmed, "any", optionsType)
            }
        }
    }

    private fun getValueForParam(params: String, paramName: String): String? {
        val pattern = Pattern.compile("$paramName\\s*=\\s*(?:\"([^\"]*)\"|(\\{[^}]*\\})|([^,\\(\\)]+))")
        val matcher = pattern.matcher(params)
        if (matcher.find()) {
            return matcher.group(1) ?: matcher.group(2) ?: matcher.group(3)?.trim()
        }
        return null
    }

    private fun parseKotlinContent(content: String, helperEnums: List<HelperEnumInfo> = emptyList()): ComponentInfo? {
        val packageMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_\\.]+)").matcher(content)
        val packageName = if (packageMatcher.find()) packageMatcher.group(1) else ""

        val classMatcher = Pattern.compile("(?:public\\s+)?(?:open\\s+)?class\\s+([a-zA-Z0-9_]+)").matcher(content)
        val className = if (classMatcher.find()) classMatcher.group(1) else return null

        val fullClassName = if (packageName.isEmpty()) className else "$packageName.$className"

        var version = "1"
        var versionName = "1.0"
        var category = "EXTENSION"
        var nonVisible = "true"
        var iconName = ""
        var rawDescription = ""

        val designerCompPattern = Pattern.compile("@(?:[a-zA-Z0-9_.]+\\.)?DesignerComponent\\s*\\((.*?)\\)", Pattern.DOTALL)
        val designerCompMatcher = designerCompPattern.matcher(content)
        if (!designerCompMatcher.find()) {
            return null
        }
        val params = designerCompMatcher.group(1)
        version = getValueForParam(params, "version") ?: "1"
        versionName = getValueForParam(params, "versionName") ?: "1.0"
        category = getValueForParam(params, "category")?.replace("ComponentCategory.", "") ?: "EXTENSION"
        nonVisible = getValueForParam(params, "nonVisible") ?: "true"
        val rawIcon = getValueForParam(params, "iconName") ?: getValueForParam(params, "icon") ?: ""
        iconName = if (rawIcon.isNotEmpty() && !rawIcon.startsWith("aiwebres/")) "aiwebres/$rawIcon" else rawIcon
        rawDescription = getValueForParam(params, "description") ?: ""
        val helpUrl = getValueForParam(params, "helpUrl") ?: ""
        val licenseName = getValueForParam(params, "licenseName") ?: ""
        val helpString = if (rawDescription.isNotBlank()) {
            if (rawDescription.trim().startsWith("<p>") || rawDescription.trim().startsWith("<div>")) {
                "$rawDescription\n"
            } else {
                "<p>$rawDescription</p>\n"
            }
        } else ""

        val xmlsList = parseXmls(content)
        val nativeLibrariesList = parseNativeLibraries(content)

        val methods = mutableListOf<MethodInfo>()
        val designerPropertiesList = mutableListOf<DesignerPropertyInfo>()
        val settersList = mutableListOf<SetterPropertyInfo>()
        val gettersList = mutableListOf<GetterPropertyInfo>()
        val blockPropertiesMap = mutableMapOf<String, MutableList<String>>()
        val blockPropertyDescriptions = mutableMapOf<String, String>()
        val blockPropertyTypes = mutableMapOf<String, String>()
        val blockPropertyDeprecated = mutableMapOf<String, String>()
        val blockPropertyHelpers = mutableMapOf<String, PropertyHelper>()
        val events = mutableListOf<EventInfo>()

        val kotlinMethodPattern = Pattern.compile(
            "((?:@(?:[a-zA-Z0-9_.]+\\.)?(?:SimpleFunction|SimpleProperty|SimpleEvent|DesignerProperty|Deprecated|Options)(?:\\s*\\((?:[^()]*|\\([^()]*\\))*\\))?\\s*)+)" +
            "(?:public\\s+|private\\s+|protected\\s+|internal\\s+|override\\s+)*fun\\s+([a-zA-Z0-9_]+)\\s*\\(((?:[^()]|\\([^()]*\\))*)\\)(?:\\s*:\\s*([a-zA-Z0-9_<>,.?\\[\\]]+))?"
        )
        val methodMatcher = kotlinMethodPattern.matcher(content)

        while (methodMatcher.find()) {
            val annotationsBlock = methodMatcher.group(1)
            val methodName = methodMatcher.group(2).trim()
            val rawParams = methodMatcher.group(3)
            val returnType = methodMatcher.group(4)?.trim() ?: "Unit"

            val isDeprecated = if (annotationsBlock.contains("@Deprecated")) "true" else "false"
            val parameters = parseKotlinParameters(rawParams, helperEnums)

            if (annotationsBlock.contains("SimpleFunction")) {
                val funcDesc = extractParamFromAnnotation(annotationsBlock, "SimpleFunction", "description") ?: ""
                methods.add(MethodInfo(
                    deprecated = isDeprecated,
                    name = methodName,
                    description = funcDesc,
                    params = parameters,
                    returnType = getYailType(returnType)
                ))
            }

            if (annotationsBlock.contains("SimpleEvent")) {
                val eventDesc = extractParamFromAnnotation(annotationsBlock, "SimpleEvent", "description") ?: ""
                events.add(EventInfo(
                    deprecated = isDeprecated,
                    name = methodName,
                    description = eventDesc,
                    params = parameters
                ))
            }

            if (annotationsBlock.contains("DesignerProperty")) {
                val defaultVal = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "defaultValue") ?: ""
                val editorType = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorType") ?: "text"
                val alwaysSend = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "alwaysSend") ?: "false"
                val rawEditorArgs = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorArgs")
                val editorArgsList = if (!rawEditorArgs.isNullOrBlank()) {
                    rawEditorArgs.replace("{", "").replace("}", "").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                } else emptyList()

                designerPropertiesList.add(DesignerPropertyInfo(
                    defaultValue = defaultVal,
                    alwaysSend = alwaysSend,
                    name = methodName,
                    editorArgs = editorArgsList,
                    editorType = editorType
                ))
            }

            if (annotationsBlock.contains("SimpleProperty")) {
                val propDesc = extractParamFromAnnotation(annotationsBlock, "SimpleProperty", "description") ?: ""
                val isGetter = returnType != "Unit" && returnType != "void" && parameters.isEmpty()
                val isSetter = (returnType == "Unit" || returnType == "void") && parameters.size == 1

                val propType = if (isGetter) (getYailType(returnType) ?: returnType) else if (isSetter && parameters.isNotEmpty()) parameters[0].type else "text"
                blockPropertyTypes[methodName] = propType
                if (propDesc.isNotEmpty()) {
                    blockPropertyDescriptions[methodName] = propDesc
                }
                if (isDeprecated == "true") {
                    blockPropertyDeprecated[methodName] = "true"
                }

                var helper: PropertyHelper? = null
                if (isSetter && parameters.isNotEmpty() && parameters[0].optionsType != null) {
                    val optName = parameters[0].optionsType!!
                    val matchedHelper = helperEnums.firstOrNull { it.name == optName || it.fqcn == optName }
                    if (matchedHelper != null) {
                        helper = matchedHelper.toPropertyHelper()
                        blockPropertyHelpers[methodName] = helper
                    }
                }

                if (isGetter) {
                    blockPropertiesMap.getOrPut(methodName) { mutableListOf() }.add("read")
                    gettersList.add(
                        GetterPropertyInfo(
                            name = methodName,
                            description = propDesc,
                            type = propType,
                            helper = helper ?: blockPropertyHelpers[methodName],
                            deprecated = isDeprecated
                        )
                    )
                }
                if (isSetter) {
                    blockPropertiesMap.getOrPut(methodName) { mutableListOf() }.add("write")
                    settersList.add(
                        SetterPropertyInfo(
                            name = methodName,
                            description = propDesc,
                            type = propType,
                            helper = helper,
                            deprecated = isDeprecated
                        )
                    )
                }
            }
        }

        val kotlinVarPattern = Pattern.compile(
            "((?:@(?:[a-zA-Z0-9_.]+\\.)?(?:SimpleProperty|DesignerProperty)(?:\\s*\\([^)]*\\))?\\s*)+)" +
            "(?:public\\s+|private\\s+|protected\\s+|internal\\s+|lateinit\\s+)*(var|val)\\s+([a-zA-Z0-9_]+)(?:\\s*:\\s*([a-zA-Z0-9_<>,.?\\[\\]]+))?"
        )
        val varMatcher = kotlinVarPattern.matcher(content)

        while (varMatcher.find()) {
            val annotationsBlock = varMatcher.group(1)
            val isVar = varMatcher.group(2) == "var"
            val propName = varMatcher.group(3)
            val propType = varMatcher.group(4)?.trim() ?: "any"

            if (annotationsBlock.contains("DesignerProperty")) {
                val defaultVal = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "defaultValue") ?: ""
                val editorType = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorType") ?: "text"
                val alwaysSend = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "alwaysSend") ?: "false"
                val rawEditorArgs = extractParamFromAnnotation(annotationsBlock, "DesignerProperty", "editorArgs")
                val editorArgsList = if (!rawEditorArgs.isNullOrBlank()) {
                    rawEditorArgs.replace("{", "").replace("}", "").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                } else emptyList()

                designerPropertiesList.add(DesignerPropertyInfo(
                    defaultValue = defaultVal,
                    alwaysSend = alwaysSend,
                    name = propName,
                    editorArgs = editorArgsList,
                    editorType = editorType
                ))
            }

            if (annotationsBlock.contains("SimpleProperty")) {
                val desc = extractParamFromAnnotation(annotationsBlock, "SimpleProperty", "description") ?: ""
                val pType = getYailType(propType) ?: propType
                blockPropertyTypes[propName] = pType
                if (desc.isNotEmpty()) {
                    blockPropertyDescriptions[propName] = desc
                }
                blockPropertiesMap.getOrPut(propName) { mutableListOf() }.add("read")
                gettersList.add(
                    GetterPropertyInfo(
                        name = propName,
                        description = desc,
                        type = pType,
                        helper = blockPropertyHelpers[propName]
                    )
                )
                if (isVar) {
                    blockPropertiesMap.getOrPut(propName) { mutableListOf() }.add("write")
                    settersList.add(
                        SetterPropertyInfo(
                            name = propName,
                            description = desc,
                            type = pType,
                            helper = blockPropertyHelpers[propName]
                        )
                    )
                }
            }
        }

        val finalizedGetters = gettersList.map { getter ->
            if (getter.helper == null && blockPropertyHelpers.containsKey(getter.name)) {
                getter.copy(helper = blockPropertyHelpers[getter.name])
            } else {
                getter
            }
        }

        val blockPropertiesList = blockPropertiesMap.map { (name, modes) ->
            val rw = if (modes.contains("read") && modes.contains("write")) {
                "read-write"
            } else if (modes.contains("read")) {
                "read-only"
            } else {
                "write-only"
            }
            BlockPropertyInfo(
                helper = blockPropertyHelpers[name],
                rw = rw,
                deprecated = blockPropertyDeprecated[name] ?: "false",
                name = name,
                description = blockPropertyDescriptions[name] ?: "",
                type = blockPropertyTypes[name] ?: "text"
            )
        }

        return ComponentInfo(
            categoryString = category,
            dateBuilt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
            nonVisible = nonVisible,
            iconName = iconName,
            methods = methods,
            blockProperties = blockPropertiesList,
            helpUrl = helpUrl,
            licenseName = licenseName,
            type = fullClassName,
            versionName = versionName,
            androidMinSdk = 14,
            version = version,
            external = "true",
            showOnPalette = "true",
            name = className,
            helpString = helpString,
            events = events,
            properties = designerPropertiesList,
            setters = settersList,
            getters = finalizedGetters,
            xmls = xmlsList,
            nativeLibraries = nativeLibrariesList,
            rawDescription = rawDescription
        )
    }

    private fun parseKotlinParameters(rawParams: String, helperEnums: List<HelperEnumInfo> = emptyList()): List<Parameter> {
        if (rawParams.trim().isEmpty()) return emptyList()
        return rawParams.split(",").map { param ->
            var trimmed = param.trim()

            var optionsType: String? = null
            val optionsPattern = Pattern.compile("@Options\\s*\\(\\s*([^\\)]+)\\s*\\)")
            val matcher = optionsPattern.matcher(trimmed)
            if (matcher.find()) {
                optionsType = matcher.group(1)?.replace(".class", "")?.replace("::class", "")?.trim()
                trimmed = matcher.replaceFirst("").trim()
            }

            val matchedHelper = if (optionsType != null) {
                helperEnums.firstOrNull { it.name == optionsType || it.fqcn == optionsType }?.toPropertyHelper()
            } else null

            val colonIndex = trimmed.indexOf(':')
            if (colonIndex != -1) {
                val name = trimmed.substring(0, colonIndex).trim()
                val type = trimmed.substring(colonIndex + 1).trim()
                Parameter(matchedHelper, name, getYailType(type) ?: "any", optionsType)
            } else {
                Parameter(matchedHelper, trimmed, "any", optionsType)
            }
        }
    }
}
