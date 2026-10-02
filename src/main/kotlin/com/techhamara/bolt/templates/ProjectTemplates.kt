package com.techhamara.bolt.templates

object ProjectTemplates {

    fun getExtensionTempJava(name: String, org: String, author: String = "", template: String? = null): String {
        val authorName = if (author.isNotBlank()) author else "author"
        val description = "Developed by $authorName using Bolt CLI"
        val hasHelper = template != null && (template == "str" || template == "int")
        val helperImport = if (hasHelper) "\nimport $org.helpers.*;\nimport com.google.appinventor.components.annotations.Options;\nimport com.google.appinventor.components.annotations.SimpleProperty;" else ""

        val helperMethod = when (template) {
            "int" -> """
            
            @SimpleProperty(
                description = "Set helper Mode type (0=Demo1, 1=Demo2, 2=Demo3)"
            )
            public void DemoType(@Options(ModeType.class) int style) {
                // add your function here
            }
            """.trimIndent()
            "str" -> """
            
            @SimpleProperty(
                description = "Set helper Interval type (Demo1, Demo2, Demo3)"
            )
            public void DemoInterval(@Options(IntervalType.class) String style) {
                // add your function here
            }
            """.trimIndent()
            else -> ""
        }

        return """
        package $org;

        import com.google.appinventor.components.annotations.DesignerComponent;
        import com.google.appinventor.components.annotations.SimpleFunction;
        import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
        import com.google.appinventor.components.runtime.ComponentContainer;
        import com.google.appinventor.components.runtime.errors.YailRuntimeError;
        import com.google.appinventor.components.runtime.util.YailList;$helperImport

        @DesignerComponent(
                version = 1,
                versionName = "1.0",
                description = "$description",
                iconName = "icon.png"
        )
        public class $name extends AndroidNonvisibleComponent {

            public $name(ComponentContainer container) {
                super(container.${'$'}form());
            }

            @SimpleFunction(description = "Returns the sum of the given list of integers.")
            public int SumAll(YailList listOfInts) {
                int sum = 0;
                for (final Object o : listOfInts.toArray()) {
                    try {
                        sum += Integer.parseInt(o.toString());
                    } catch (NumberFormatException e) {
                        throw new YailRuntimeError(e.toString(), "NumberFormatException");
                    }
                }
                return sum;
            }
            $helperMethod
        }
        """.trimIndent()
    }

    fun getExtensionTempKt(name: String, org: String, author: String = "", template: String? = null): String {
        val authorName = if (author.isNotBlank()) author else "author"
        val description = "Developed by $authorName using Bolt CLI"
        val hasHelper = template != null && (template == "str" || template == "int")
        val helperImport = if (hasHelper) "\nimport $org.helpers.*\nimport com.google.appinventor.components.annotations.Options\nimport com.google.appinventor.components.annotations.SimpleProperty" else ""

        val helperMethod = when (template) {
            "int" -> """
            
            @SimpleProperty(
                description = "Set helper Mode type (0=Demo1, 1=Demo2, 2=Demo3)"
            )
            fun DemoType(@Options(ModeType::class) style: Int) {
                // add your function here
            }
            """.trimIndent()
            "str" -> """
            
            @SimpleProperty(
                description = "Set helper Interval type (Demo1, Demo2, Demo3)"
            )
            fun DemoInterval(@Options(IntervalType::class) style: String) {
                // add your function here
            }
            """.trimIndent()
            else -> ""
        }

        return """
        package $org

        import com.google.appinventor.components.annotations.DesignerComponent
        import com.google.appinventor.components.annotations.SimpleFunction
        import com.google.appinventor.components.runtime.AndroidNonvisibleComponent
        import com.google.appinventor.components.runtime.ComponentContainer
        import com.google.appinventor.components.runtime.util.YailList$helperImport

        @DesignerComponent(
                version = 1,
                versionName = "1.0",
                description = "$description",
                iconName = "icon.png"
        )
        class $name(
            container: ComponentContainer
        ) : AndroidNonvisibleComponent(container.`${'$'}form`()) {

            @SimpleFunction(description = "Returns the sum of the given list of integers.")
            fun SumAll(listOfInts: YailList): Int {
                return listOfInts.sumOf {
                    it.toString().toIntOrNull() ?: 0
                }
            }
            $helperMethod
        }
        """.trimIndent()
    }

    fun configYaml(enableKt: Boolean, orgName: String, author: String = ""): String {
        val authorLine = if (author.isNotBlank()) "author: '$author'\n\n" else ""
        val ktComment = if (!enableKt) "#" else ""
        val timeFormatter = java.text.SimpleDateFormat("h.mma dd.MM.yyyy", java.util.Locale.ENGLISH)
        val buildTimeStr = timeFormatter.format(java.util.Date()).lowercase()
        val ktBlock = if (enableKt) {
            """
            # Kotlin specific configuration.
            kotlin:
              compiler_version: '1.9.22'

            """.trimIndent()
        } else {
            ""
        }
        val ktDep = if (enableKt) "#- org.jetbrains.kotlin:kotlin-stdlib:1.9.22\n" else ""

        return """
# Author name.
${authorLine}# Bolt build metadata.
bolt_version: '2.0.0'
build_time: '$buildTimeStr'

# The minimum Android SDK level your extension supports.
min_sdk: 14

# Define the compile Android SDK API level.
# compile_sdk: 35

# If enabled, the D8/R8 tool will generate desugared dex (classes.dex)
desugar_dex: true

# If enabled, extension will be optimized using R8.
R8: false

# If enabled, extension will be optimized using ProGuard.
proguard: false

# If enabled, extension annotations will be stripped for smaller size.
deannonate: true

# Kotlin Compiler version.
kotlin_version: '1.9.22'

${ktComment}desugar: true
$ktBlock# External libraries your extension depends on.
#dependencies:
#- example.jar                 # Local JAR or AAR file stored in 'deps' directory
#- com.example:foo-bar:1.2.3   # Coordinate of some remote Maven artifact
$ktDep
# Compile-time dependencies resolving for GradleResolver/MavenResolver [Local Only]
# compile_time:
#   - mylibrary.jar

# Default Maven repositories includes Maven Central, Google Maven, JitPack and
# JCenter. Bolt will automatically add these to the resolver, so you rarely
# need to mention them here. If the library you want to use is not available in
# these repositories, you can add additional ones by specifying their URLs here.
# repositories:
#   - https://jitpack.io

# Assets that your extension needs. Every asset file must be stored in the assets
# directory as well as declared here. Assets can be of any type.
# assets:
#   - data.json

# Attach custom XML to bundle it with APK resources (e.g. network security, file provider paths).
# [Add your XML files in assets/xml/, assets/layout/, or assets/values/ folders]
# xmls: 
#   - xml/network_security_config.xml
#   - xml/provider_paths.xml

# Similar to dependencies, except libraries defined as provided are not included
# in the final AIX. This is useful when you want to use a library in your
# extension but don't want to include it in the final AIX because it's already
# included in the App Inventor.
# provided_dependencies:
#   - com.example:foo-bar:1.2.3

# Minimization Exclusions explicitly exclude dependencies that use reflection/dynamic loading from being minimized.
# minimize:
#   exclude_dependency:
#     - org.slf4j:slf4j-simple:.*
#   exclude_project:
#     - :api

# Enable to increment the version number of each component during build.
auto_version: true

# Homepage of your extension. This may be the announcement thread on community 
# forums or a link to your GitHub repository.
# homepage: https://github.com/TechHamara/bolt-cli

# Bytecode-level string obfuscation tool, to protect hardcoded strings.
strguard:
  enabled: false
  key: "TechHamara-MyKey-2026-Secret"
  packages:
    - "$orgName"

# Implement Package Relocation (Shading).
relocation:
  EnableAutoRelocation: true
  skipStringConstants: true

# Enable modern Java API support on older devices default
coreLibraryDesugaring: false

# Native C/C++ (JNI & NDK) Support
# ndk:
#   enabled: true
#   module: native-lib
#   abis:
#     - armeabi-v7a
#     - arm64-v8a
""".trimIndent()
    }

    fun sampleCpp(packageName: String, className: String): String {
        val jniFunc = "Java_" + packageName.replace(".", "_") + "_" + className + "_StringFromJNI"
        return """
        #include <jni.h>
        #include <string>

        extern "C" JNIEXPORT jstring JNICALL
        $jniFunc(JNIEnv* env, jobject /* this */) {
            std::string hello = "Hello from Native C++ Bolt!";
            return env->NewStringUTF(hello.c_str());
        }
        """.trimIndent()
    }

    fun androidManifestXml(orgName: String): String {
        return """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="$orgName">
            <application>
                <!-- You can use any manifest tag that goes inside the <application> tag -->
                <!-- <service android:name="com.example.MyService"> ... </service> -->
            </application>

            <!-- Other than <application> level tags, you can use <uses-permission> & <queries> tags -->
            <!-- <uses-permission android:name="android.permission.SEND_SMS"/> -->
            <!-- <queries> ... </queries> -->
        </manifest>
        """.trimIndent()
    }

    fun pgRules(orgName: String): String {
        return """
        # Repackages all the optimized classes into $orgName.repackaged package in resulting
        # AIX. Repackaging is necessary to avoid clashes with other extensions.
        -repackageclasses $orgName.repackaged

        # Aggressive optimizations for smaller extension size
        -android
        -optimizationpasses 5
        -allowaccessmodification
        -mergeinterfacesaggressively
        -overloadaggressively
        -useuniqueclassmembernames
        -dontskipnonpubliclibraryclasses
        -dontskipnonpubliclibraryclassmembers
        """.trimIndent()
    }

    fun readmeMd(name: String): String {
        return """
        # $name

        An App Inventor 2 Extension built with [Bolt CLI](https://github.com/TechHamara/bolt-cli).

        ## Usage
        - Run `bolt build` to compile your extension.
        - Run `bolt run` to start live hot-reloading to a mobile device.
        - Run `bolt tree` to visualize your project structure.
        """.trimIndent()
    }

    val dotGitignore: String = """
    # Bolt Build & Cache Directories
    .bolt/
    build/
    out/
    *.aix
    tree.txt

    # IDE files
    .idea/
    *.iml
    .vscode/
    .project
    .classpath
    .settings/
    """.trimIndent()

    fun githubActionsYaml(name: String): String {
        return """
        name: Build Extension
        on: [push, pull_request]

        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - name: Set up JDK 17
                uses: actions/setup-java@v4
                with:
                  java-version: '17'
                  distribution: 'temurin'
              - name: Install Bolt CLI
                run: |
                  curl https://raw.githubusercontent.com/TechHamara/bolt-cli/main/scripts/install/install.sh -fsSL | sh
              - name: Build Extension
                run: bolt build
              - name: Upload AIX Artifact
                uses: actions/upload-artifact@v4
                with:
                  name: $name-AIX
                  path: out/*.aix
        """.trimIndent()
    }

    fun ijMiscXml(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="ProjectRootManager" version="2" languageLevel="JDK_11" project-jdk-name="11" project-jdk-type="JavaSDK">
        <output url="file://${'$'}PROJECT_DIR${'$'}/build/classes" />
      </component>
    </project>
    """.trimIndent()

    fun ijModulesXml(paramCaseName: String): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="ProjectModuleManager">
        <modules>
          <module fileurl="file://${'$'}PROJECT_DIR${'$'}/.idea/$paramCaseName.iml" filepath="${'$'}PROJECT_DIR${'$'}/.idea/$paramCaseName.iml" />
        </modules>
      </component>
    </project>
    """.trimIndent()

    fun ijImlXml(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <module type="JAVA_MODULE" version="4">
      <component name="NewModuleRootManager" inherit-compiler-output="true">
        <exclude-output />
        <content url="file://${'$'}MODULE_DIR${'$'}/..">
          <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/../src" isTestSource="false" />
          <sourceFolder url="file://${'$'}MODULE_DIR${'$'}/../assets" type="java-resource" />
          <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/../build" />
          <excludeFolder url="file://${'$'}MODULE_DIR${'$'}/../out" />
        </content>
        <orderEntry type="inheritedJdk" />
        <orderEntry type="sourceFolder" forTests="false" />
        <orderEntry type="library" name="local-deps" level="project" />
      </component>
    </module>
    """.trimIndent()

    fun ijLocalDepsXml(): String = """
    <component name="libraryTable">
      <library name="local-deps">
        <CLASSES>
          <root url="file://${'$'}PROJECT_DIR${'$'}/deps" />
        </CLASSES>
        <JAVADOC />
        <SOURCES />
        <jarDirectory url="file://${'$'}PROJECT_DIR${'$'}/deps" recursive="true" />
      </library>
    </component>
    """.trimIndent()

    fun vscodeSettingsJson(): String = """
    {
      "java.project.sourcePaths": ["src"],
      "java.project.outputPath": "build/classes",
      "java.project.referencedLibraries": [
        "deps/**/*.jar"
      ]
    }
    """.trimIndent()

    fun dotProject(paramCaseName: String): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <projectDescription>
        <name>$paramCaseName</name>
        <comment></comment>
        <projects></projects>
        <buildSpec>
            <buildCommand>
                <name>org.eclipse.jdt.core.javabuilder</name>
                <arguments></arguments>
            </buildCommand>
        </buildSpec>
        <natures>
            <nature>org.eclipse.jdt.core.javanature</nature>
        </natures>
    </projectDescription>
    """.trimIndent()

    fun dotClasspath(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <classpath>
        <classpathentry kind="src" path="src"/>
        <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
        <classpathentry kind="output" path="build/classes"/>
    </classpath>
    """.trimIndent()

    // Official Bolt extension icon.png
    private const val OFFICIAL_ICON_BASE64: String =
        "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" +
        "VlcAAAAASUVORK5CYII="

    val iconBytes: ByteArray = java.util.Base64.getDecoder().decode(OFFICIAL_ICON_BASE64)
    val minimalIconBytes: ByteArray = iconBytes
}
