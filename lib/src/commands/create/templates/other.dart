import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/utils/constants.dart';

String config(bool enableKt, [String? author]) {
  final authorLine = author != null && author.isNotEmpty
      ? "\n# Author name (shown in generated documentation).\nauthor: '$author'\n"
      : '';
  return '''
# This is the version name of your extension. You should update it everytime you
# publish a new version of your extension.
version: '1.0.0'
$authorLine
# The minimum Android SDK level your extension supports. Minimum SDK defined in
# AndroidManifest.xml is ignored, you should always define it here.
min_sdk: 14

# Define the compile Android SDK API level.
# compile_sdk: 35
# If enabled, the D8 tool will generate desugared jar (classes.dex)
desugar_dex: true
# If enabled, extension will be optimized using R8.
R8: true
# If enabled, extension will be optimized using ProGuard.
proguard: false
# Define specific ProGuard version.
proguard_version: '${pgCoord.split(':').last}'
# If enabled, Kotlin Standard Libraries will be included with the extension.
# kotlin: false
# Kotlin Compiler version.
kotlin_version: '$defaultKtVersion'

# Desuagring allows you to use Java 8 language features in your extension. You 
# also need to enable desugaring if any of your dependencies use Java 8 language
# features.
${!enableKt ? '#' : ''}desugar: true
${!enableKt ? '' : '''

# Kotlin specific configuration.
kotlin:
  compiler_version: '$defaultKtVersion'
'''}
# External libraries your extension depends on. These can be local JARs / AARs
# stored in the "deps" directory or coordinates of remote Maven artifacts in
# <groupId>:<artifactId>:<version> format. 
${enableKt ? 'dependencies:' : '#dependencies:'}
${enableKt ? '- $kotlinGroupId:kotlin-stdlib:$defaultKtVersion\n' : ''}#- example.jar                 # Local JAR or AAR file stored in "deps" directory
#- com.example:foo-bar:1.2.3   # Coordinate of some remote Maven artifact

# Default Maven repositories includes Maven Central, Google Maven, JitPack and
# JCenter. Bolt will automatically add these to the resolver, so you rarely
# need to mention them here. If the library you want to use is not available in
# these repositories, you can add additional ones by specifying their URLs here.
#repositories:
#- https://jitpack.io

# Assets that your extension needs. Every asset file must be stored in the assets
# directory as well as declared here. Assets can be of any type.
#assets:
#- data.json

# Homepage of your extension. This may be the announcement thread on community 
# forums or a link to your GitHub repository.
#homepage: https://github.com/TechHamara/bolt-cli

# Path to the license file of your extension. This should be a path to a local
# file or link to something hosted online.
#license: LICENSE.txt

# Similar to dependencies, except libraries defined as provided are not included
# in the final AIX. This is useful when you want to use a library in your
# extension but don't want to include it in the final AIX because it's already
# included in the App Inventor.
#provided_dependencies:
#- com.example:foo-bar:1.2.3
''';
}

String pgRules(String org) {
  return '''
# Prevents extension classes (annotated with `@Extension`) from being removed, renamed or repackged.
-keep @com.google.appinventor.components.annotations.Extension public class * {
  public *;
}

# Prevents helper classes from being removed, renamed or repackaged
-keepnames class * implements com.google.appinventor.components.common.OptionList {
  *;
}

# ProGuard sometimes (randomly) renames references to the following classes in 
# the extensions, this rule prevents that from happening. Keep this rule even
# if you don't use these classes in your extension.
-keeppackagenames gnu.kawa**, gnu.expr**

# Repackages all the optimized classes into $org.repackaged package in resulting
# AIX. Repackaging is necessary to avoid clashes with the other extensions that
# might be using same libraries as you.
-repackageclasses $org.repacked

# Aggressive optimizations for smaller extension size
-android
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-useuniqueclassmembernames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
''';
}

const String dotGitignore = '''
/out
/.bolt
''';

String androidManifestXml(String org) {
  return '''
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$org">

    <application>
        <!-- You can use any manifest tag that goes inside the <application> tag -->
        <!-- <service android:name="com.example.MyService"> ... </service> -->
    </application>

    <!-- Other than <application> level tags, you can use <uses-permission> & <queries> tags -->
    <!-- <uses-permission android:name="android.permission.SEND_SMS"/> -->
    <!-- <queries> ... </queries> -->

</manifest>
''';
}

// TODO: Add build instructions and other basic info
String readmeMd(String name) {
  return '''
# $name

An App Inventor 2 extension created using Bolt.
''';
}
