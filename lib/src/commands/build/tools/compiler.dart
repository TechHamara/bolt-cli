import 'dart:io' show File, Process;

import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:get_it/get_it.dart';

import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';
import 'package:bolt/src/utils/process_runner.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/commands/build/utils.dart';

const helpersTimestampKey = 'helper-enums';

class Compiler {
  static final _fs = GetIt.I<FileService>();
  static final _libService = GetIt.I<LibService>();
  static final _lgr = GetIt.I<Logger>();

  static final _processRunner = ProcessRunner();

  /// Ensure that the classpath set contains a definition for
  /// `com.google.appinventor.components.annotations.DesignerComponent`.
  ///
  /// The official `annotations` artifact shipped on Maven (2.0.1) lacks this
  /// class, which causes user projects importing the annotation to fail with
  /// "cannot find symbol" errors.  We remedy the situation by generating a
  /// tiny supplemental JAR containing the missing annotation from the CLI's
  /// own source tree and appending it to the classpath at build time.  The
  /// generated JAR is cached in the build-files directory so the work only
  /// happens once per project.
  static Future<void> patchAnnotationJarIfNecessary(
      Set<String> classpathJars) async {
    /// _lgr.info(
    ///    'patchAnnotationJarIfNecessary called; initial classpath: $classpathJars');
    const designerEntry =
        'com/google/appinventor/components/annotations/DesignerComponent.class';

    // allow a locally installed annotations JAR to override the one pulled
    // in from Maven.  installations that mirror the CLI's repository layout
    // normally keep tools in `$BOLT_HOME/libs/tools`; if the user has placed a
    // copy of `annotations.jar` there, prefer it and ensure it appears on the
    // classpath.  this satisfies the request to "set default annotations path
    // to C:\Users\\kapil\\.bolt\\libs\\tools\\annotations.jar".  when
    // the local JAR is present we *remove* any other bolt/annotations artifact
    // from the classpath so it is never consulted.
    final localAnnPath =
        p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'annotations.jar');
    final bool haveLocal = File(localAnnPath).existsSync();
    if (haveLocal) {
      ///  _lgr.info('using local annotations JAR at $localAnnPath');
      // drop any other candidate jars
      classpathJars.removeWhere((j) {
        if (p.equals(j, localAnnPath)) return false;
        final name = p.basename(j).toLowerCase();
        final norm = j.replaceAll(r'\\', '/').toLowerCase();
        return name.startsWith('annotations') ||
            norm.contains('/bolt/annotations/');
      });
      classpathJars.add(localAnnPath);
    }

    // First, if any existing jar already contains the missing entry, we're
    // done.  This check also has the side‑effect of scanning the actual
    // archives, which avoids incorrect assumptions about filenames.
    for (final jar in classpathJars) {
      if (!jar.toLowerCase().endsWith('.jar')) continue;
      if (await _jarContains(jar, designerEntry)) {
        _lgr.info(
            'classpath already contains DesignerComponent in $jar; no patch needed');
        return;
      }
    }

    // No jar provided the class – prepare to generate a supplemental jar.  We
    // still attempt to locate the original bolt annotations jar so that the
    // Java compiler can reference it during compilation of the extra class;
    // absence of the jar is not fatal, but may lead to compilation failures if
    // there are other annotation types referenced.
    String? annJar;
    if (haveLocal) {
      // local override has already been added above; use that exclusively
      annJar = localAnnPath;
    } else {
      for (final jar in classpathJars) {
        final name = p.basename(jar).toLowerCase();
        _lgr.dbg('examining classpath entry for annotation jar: $jar');
        // Accept either the traditional artifact name prefix (bolt-annotations)
        // or the canonical Maven repository path containing
        // .../bolt/annotations/.../annotations-<version>.jar.
        final normPath = jar.replaceAll(r'\\', '/').toLowerCase();
        if ((name.startsWith('annotations') && name.endsWith('.jar')) ||
            (normPath.contains('/bolt/annotations/') &&
                normPath.endsWith('.jar'))) {
          annJar = jar;
          _lgr.dbg('identified bolt annotations jar as $jar');
          break;
        }
      }
    }
    if (annJar == null) {
      _lgr.info(
          'did not locate a bolt annotations JAR; patch will be compiled without it');
    } else {
      ///  _lgr.info('annotation jar detected at $annJar');
    }

    // DesignerComponent annotation patch is no longer needed as all recent
    // versions of the annotations artifact include the missing class definition.
    // Skip creating temporary directories and patch JAR files.
  }

  static Future<bool> _jarContains(String jarPath, String entry) async {
    try {
      final result =
          await Process.run(BuildUtils.javaExe(), ['-jar', jarPath, 'tf']);
      if (result.exitCode != 0) return false;
      return (result.stdout as String)
          .split(RegExp(r'\r?\n'))
          .any((line) => line.trim() == entry);
    } catch (_) {
      return false;
    }
  }

  static Future<void> _compileHelpers(
    Set<String> classpathJars,
    LazyBox<DateTime> timestampBox, {
    String? ktVersion,
    bool java8 = false,
    Config? config,
    bool buildBlocks = false,
  }) async {
    // Only the files that reside directly under the "com.sth.helpers" package
    // are considered as helpers. We could probably lift this restriction in
    // future, but because this how AI2 does it, we'll stick to it for now.
    final helperFiles =
        _fs.srcDir.listSync(recursive: true).whereType<File>().where((el) {
      // Historically we also treated every file containing an `@Options`
      // annotation as a helper, but that caused the compiler to attempt to
      // pre‑compile the main extension class itself when it happened to expose
      // an options parameter (see https://github.com/TechHamara/bolt-cli/issues/…).
      // The original AI2 behaviour only pre‑compiled files that lived directly
      // under a `helpers` package, which is enough for the enum classes we
      // care about.  Drop the content-based heuristic and rely solely on the
      // directory name – developers who choose a different layout can always
      // move their helpers into a `helpers` folder.
      return el.path.split(p.separator).contains('helpers');
    });

    final helpersModTime = await timestampBox.get(helpersTimestampKey);
    final helpersModified = helpersModTime == null
        ? true
        : helperFiles
            .any((el) => el.lastModifiedSync().isAfter(helpersModTime));
    if (helperFiles.isEmpty || !helpersModified) {
      return;
    }

    _lgr.info('Pre-compiling helper enums...');
    final hasKtFiles = helperFiles.any((el) => p.extension(el.path) == '.kt');

    final List<String> args;
    if (hasKtFiles) {
      args = await _kotlincArgs(
        helperFiles.first.parent.path,
        classpathJars,
        ktVersion!,
        config,
        buildBlocks,
        files: helperFiles.map((e) => e.path).toSet(),
        withProc: false,
      );
    } else {
      args = await _javacArgs(
        helperFiles.map((e) => e.path).toSet(),
        classpathJars,
        java8,
        config,
        buildBlocks,
        withProc: false,
      );
    }

    final compilationStartedOn = DateTime.now();
    try {
      await _processRunner.runExecutable(BuildUtils.javaExe(!hasKtFiles), args);
    } catch (e) {
      rethrow;
    }

    await _cleanUpOldClassFiles(compilationStartedOn, keepHelpers: true);
    await timestampBox.put(helpersTimestampKey, DateTime.now());
  }

  static Future<void> _cleanUpOldClassFiles(DateTime compilationStartedOn,
      {bool keepHelpers = false}) async {
    final files = _fs.buildClassesDir
        .listSync(recursive: true)
        .where((el) => p.extension(el.path) == '.class' && !keepHelpers
            ? !el.path.split(p.separator).contains('helpers')
            : true)
        .whereType<File>();

    for (final file in files) {
      if ((await file.lastModified()).isBefore(compilationStartedOn)) {
        await file.delete();
      }
    }
  }

  /// Compile a set of Java source files.
  ///
  /// If [javaFiles] is provided we use it directly; otherwise we perform a
  /// recursive scan of the project's `src` directory.  Passing the set from the
  /// caller guarantees that the list of files used for compilation exactly
  /// matches the list reported earlier (see BuildCommand).
  static Future<void> compileJavaFiles(
    Set<String> classpathJars,
    bool supportJava8,
    LazyBox<DateTime> timestampBox,
    Config config,
    bool buildBlocks, {
    Set<String>? javaFiles,
  }) async {
    javaFiles ??= _fs.srcDir
        .listSync(recursive: true)
        .where((el) => el is File && p.extension(el.path) == '.java')
        .map((el) => el.path)
        .toSet();

    // log the amount we are about to compile; callers already print a summary
    // but the compiler itself can also help later when debugging.
    final fileCount = javaFiles.length;
    if (fileCount > 0) {
      _lgr.info('Compiling $fileCount Java file${fileCount > 1 ? 's' : ''}');
    }

    final DateTime compilationStartedOn;
    try {
      await _compileHelpers(classpathJars, timestampBox,
          java8: supportJava8, config: config, buildBlocks: buildBlocks);
      final args = _javacArgs(
          javaFiles, classpathJars, supportJava8, config, buildBlocks);
      compilationStartedOn = DateTime.now();
      await _processRunner.runExecutable(BuildUtils.javaExe(true), await args);
    } catch (e) {
      rethrow;
    }
    await _cleanUpOldClassFiles(compilationStartedOn);
  }

  /// Determine whether any of the provided Java files contain language
  /// constructs that require Java 8 (lambda expressions or method references).
  /// This is used for automatic detection when the user does not specify
  /// `java8:` in bolt.yml.
  static Future<bool> hasJava8Features(Set<String> javaFiles) async {
    for (final path in javaFiles) {
      try {
        final content = await File(path).readAsString();
        if (content.contains('->') || content.contains('::')) {
          return true;
        }
      } catch (_) {
        // ignore I/O problems; skip file
      }
    }
    return false;
  }

  static Future<List<String>> _javacArgs(
    Set<String> files,
    Set<String> classpathJars,
    bool supportJava8,
    Config? config,
    bool buildBlocks, {
    bool withProc = true,
  }) async {
    final classpath = classpathJars.join(BuildUtils.cpSeparator);
    final procClasspath = await _libService.processorJar();
    final javaHome = await BuildUtils.javaHomeDir();
    final bootstrapPath = p.join(javaHome, 'jre', 'lib', 'rt.jar');

    final args = <String>[
      ...['-source', supportJava8 ? '1.8' : '1.7'],
      ...['-target', supportJava8 ? '1.8' : '1.7'],
      ...['-encoding', 'UTF8'],
      if (!supportJava8) ...['-bootclasspath', '"$bootstrapPath"'],
      if (config != null && config.genDocs) '-Agen_docs=true',
      if (config != null && config.autoVersion) '-Aauto_version=true',
      // buildBlocks is only passed to the annotation processor via kapt;
      // javac itself does not understand the flag and will error if we supply
      // it (see build failure when running `bolt build --build-blocks`).
      // The Kotlin compiler invocation already takes care of passing the
      // option when withProc is true, so we can safely omit it here.
      ...['-d', '"${_fs.buildClassesDir.path}"'],
      ...['-cp', '"$classpath"'],
      if (withProc) ...['-processorpath', '"$procClasspath"'],
      ...files.map((el) => '"$el"'),
    ].map((el) => el.replaceAll('\\', '/')).join('\n');
    await _fs.javacArgsFile.writeAsString(args);
    return ['@${_fs.javacArgsFile.path}'];
  }

  static Future<void> compileKtFiles(
    Set<String> classpathJars,
    String kotlinVersion,
    LazyBox<DateTime> timestampBox,
    Config config,
    bool buildBlocks,
  ) async {
    final DateTime compilationStartedOn;
    try {
      await _compileHelpers(classpathJars, timestampBox,
          ktVersion: kotlinVersion, config: config, buildBlocks: buildBlocks);
      final kotlincArgs = await _kotlincArgs(
          _fs.srcDir.path, classpathJars, kotlinVersion, config, buildBlocks);
      compilationStartedOn = DateTime.now();
      await _processRunner.runExecutable(BuildUtils.javaExe(), kotlincArgs);
    } catch (e) {
      rethrow;
    }
    await _cleanUpOldClassFiles(compilationStartedOn);
  }

  static Future<List<String>> _kotlincArgs(
    String srcDir,
    Set<String> classpathJars,
    String kotlinVersion,
    Config? config,
    bool buildBlocks, {
    bool withProc = true,
    Set<String>? files,
  }) async {
    final kaptJar = (await _libService.kaptJars(kotlinVersion)).first;
    final duplicateKaptJar =
        p.join(p.dirname(kaptJar), 'kotlin-annotation-processing.jar');
    await kaptJar.asFile().copy(duplicateKaptJar);

    final toolsJar =
        p.join(await BuildUtils.javaHomeDir(), 'lib', 'tools.jar').asFile();

    final classpath = [
      ...(await _libService.kotlincJars(kotlinVersion)),
      if (withProc) ...(await _libService.kaptJars(kotlinVersion)),
      if (withProc && await toolsJar.exists()) toolsJar.path,
    ].join(BuildUtils.cpSeparator);

    final procClasspath = await _libService.processorJar();

    // Options that should be passed to annotation processors.  During
    // Kotlin compilation we used to lump them under
    // `-Kapt-javac-options` which are then forwarded to the embedded `javac`.
    //
    // The problem: when using the kapt CLI this flag is **not** interpreted and
    // ends up being given verbatim to `javac`.  The compiler run below will
    // therefore fail with "invalid argument: -Kapt-javac-options=…" as seen in
    // the user's project (author `th`).  Rather than fighting the kapt
    // implementation we simply emit processor options directly; kapt itself
    // understands `-A…` flags in both Kotlin and Java modes.
    final kaptOptions = <String>[];
    if (config != null && config.genDocs) kaptOptions.add('-Agen_docs=true');
    if (config != null && config.autoVersion) {
      kaptOptions.add('-Aauto_version=true');
    }
    // `author` value used to be passed to the annotation processor but
    // the processor itself reads it directly from the Bolt configuration file.
    // Sending it via command line has repeatedly broken builds (see
    // https://github.com/TechHamara/bolt-cli/issues/…), so omit it.
    // buildBlocks flag only affects post-compilation block rendering; it
    // does not need to be sent to the annotation processor.  passing it
    // earlier caused kotlinc to fail with "invalid argument: -Abuild_blocks=…".

    final kotlincArgs = <String>[
      // classpath for the compiler itself (this will also be supplied on the
      // java command line, but the compiler wants to know the compilation
      // classpath separately when resolving references inside Kotlin sources)
      ...['-cp', classpathJars.join(BuildUtils.cpSeparator)],
      if (withProc) ...[
        '-Kapt-classes=${_fs.buildKaptDir.path}',
        '-Kapt-sources=${_fs.buildKaptDir.path}',
        '-Kapt-stubs=${_fs.buildKaptDir.path}',
        '-Kapt-classpath=$procClasspath',
        // previously all options were stuffed into a single -Kapt-javac-
        // option which the kotlin CLI failed to consume.  Instead give them
        // to the compiler verbatim; kapt will forward them appropriately.
        if (kaptOptions.isNotEmpty) ...kaptOptions,
        '-Kapt-mode=compile',
        '-Kapt-strip-metadata=true',
        '-Kapt-use-light-analysis=true',
      ],
      '-no-stdlib',
      ...['-d', _fs.buildClassesDir.path],
      if (files != null) ...files else srcDir,
    ].map((el) => el.replaceAll('\\', '/')).join('\n');

    final argsFile = _fs.kotlincArgsFile;
    await argsFile.writeAsString(kotlincArgs);
    return <String>[
      ...['-cp', classpath],
      if (withProc)
        'org.jetbrains.kotlin.kapt.cli.KaptCli'
      else
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
      '@${argsFile.path}',
    ].map((el) => el.replaceAll('\\', '/')).toList();
  }
}
