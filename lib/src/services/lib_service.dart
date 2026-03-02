import 'dart:io' show File, Directory;

import 'package:get_it/get_it.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:collection/collection.dart';

import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/resolver/artifact.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/utils/file_extension.dart';
import 'package:tint/tint.dart';

const boltApCoord =
    'io.github.techhamara.bolt:processor:$annotationProcVersion';
const r8Coord = 'com.android.tools:r8:3.3.28';
// the coordinate for the ProGuard library.  version component can be
// overridden by the user's configuration when necessary.
const pgCoord = 'com.guardsquare:proguard-base:$defaultProguardVersion';
const desugarCoord = 'io.github.techhamara:desugar:1.0.0';

const manifMergerAndDeps = <String>[
  'com.android.tools.build:manifest-merger:30.0.0',
  'org.w3c:dom:2.3.0-jaxb-1.0.6',
  'xml-apis:xml-apis:1.4.01',
];

const kotlinGroupId = 'org.jetbrains.kotlin';

class LibService {
  static final _fs = GetIt.I<FileService>();
  static final _lgr = GetIt.I<Logger>();

  LibService._() {
    Hive
      ..registerAdapter(ArtifactAdapter())
      ..registerAdapter(ScopeAdapter());

    // Don't init Hive in .bolt dir if we're not in a bolt project
    if (_fs.configFile.existsSync()) {
      Hive.init(_fs.dotBoltDir.path);
    }
  }

  late final LazyBox<Artifact> ai2ProvidedDepsBox;
  late final LazyBox<Artifact> buildLibsBox;
  late final LazyBox<Artifact>? extensionDepsBox;

  static Future<LibService> instantiate() async {
    final instance = LibService._();
    instance.ai2ProvidedDepsBox = await Hive.openLazyBox<Artifact>(
      providedDepsBoxName,
      path: p.join(_fs.boltHomeDir.path, 'cache'),
    );
    instance.buildLibsBox = await Hive.openLazyBox<Artifact>(
      buildLibsBoxName,
      path: p.join(_fs.boltHomeDir.path, 'cache'),
    );

    if (await _fs.configFile.exists()) {
      instance.extensionDepsBox = await Hive.openLazyBox<Artifact>(
        extensionDepsBoxName,
        path: _fs.dotBoltDir.path,
      );
    } else {
      instance.extensionDepsBox = null;
    }
    return instance;
  }

  /// Returns a list of all the artifacts and their dependencies in a box.
  Future<List<Artifact>> _retrieveArtifactsFromBox(
      LazyBox<Artifact> cacheBox) async {
    final artifacts = await Future.wait([
      for (final key in cacheBox.keys) cacheBox.get(key),
    ]);
    return artifacts.nonNulls.toList();
  }

  Future<List<Artifact>> providedDependencies(Config? config) async {
    final local = [
      'android-${config?.androidSdk ?? androidPlatformSdkVersion}.jar',
      'webrtc.jar',
      'kawa.jar',
      'mpandroidchart.jar',
      'osmdroid.jar',
      'physicaloid.jar',
    ].map((el) => Artifact(
          coordinate: el,
          scope: Scope.provided,
          artifactFile: p.join(_fs.libsDir.path, el),
          packaging: 'jar',
          dependencies: [],
          sourcesJar: null,
        ));

    // The annotation JAR is distributed along with the AI2 runtime and is
    // required during compilation; ensure it's always included even if the
    // user doesn't explicitly depend on it.  SyncCommand now fetches it when
    // syncing dev-dependencies, so it should appear in the ai2ProvidedDepsBox.

    if (config == null) {
      return [
        ...await _retrieveArtifactsFromBox(ai2ProvidedDepsBox),
        ...local,
      ];
    }

    final allExtRemoteDeps = await _retrieveArtifactsFromBox(extensionDepsBox!);
    final combinedProvided = [
      ...config.providedDependencies,
      ...config.compileTime
    ];
    final extProvidedDeps = combinedProvided
        .map((el) =>
            allExtRemoteDeps.firstWhereOrNull((dep) => dep.coordinate == el))
        .nonNulls;
    final extLocalProvided = combinedProvided
        .where((el) => el.endsWith('.jar') || el.endsWith('.aar'))
        .map((el) {
      return Artifact(
        scope: Scope.provided,
        coordinate: el,
        artifactFile: p.join(_fs.localDepsDir.path, el),
        packaging: p.extension(el).substring(1),
        dependencies: [],
        sourcesJar: null,
      );
    });

    return [
      ...await _retrieveArtifactsFromBox(ai2ProvidedDepsBox),
      ...extProvidedDeps,
      ...extLocalProvided,
      ...local,
    ];
  }

  List<Artifact> _requiredDeps(
      Iterable<Artifact> allDeps, Iterable<Artifact> directDeps) {
    final res = <Artifact>{};
    for (final dep in directDeps) {
      final depArtifacts = dep.dependencies
          .map((el) => allDeps.firstWhereOrNull((a) => a.coordinate == el))
          .nonNulls;
      res
        ..add(dep)
        ..addAll(_requiredDeps(allDeps, depArtifacts));
    }
    return res.toList();
  }

  Future<List<Artifact>> extensionDependencies(
    Config config, {
    bool includeAi2ProvidedDeps = false,
    bool includeProjectProvidedDeps = false,
    bool includeLocal = true,
  }) async {
    // support the user temporarily disabling dependency evaluation by
    // adding a commented line like `#dependencies: false` to bolt.yml.  in
    // that case we simply pretend there are no dependencies and don't even
    // open the cache box (which can be slow on larger projects).
    final rawCfg = _fs.configFile.readAsStringSync();
    if (RegExp(r'^\s*#\s*dependencies\s*:\s*false', multiLine: true)
            .hasMatch(rawCfg) &&
        config.dependencies.isEmpty) {
      _lgr.dbg('commented out dependencies; skipping cache lookup');
      return [];
    }

    final allExtRemoteDeps = await _retrieveArtifactsFromBox(extensionDepsBox!);

    final projectDeps = config.dependencies
        .map((el) =>
            allExtRemoteDeps.firstWhereOrNull((dep) => dep.coordinate == el))
        .nonNulls;
    final requiredDeps = _requiredDeps(allExtRemoteDeps, projectDeps);

    final projectProvidedDeps = config.providedDependencies
        .map((el) =>
            allExtRemoteDeps.firstWhereOrNull((dep) => dep.coordinate == el))
        .nonNulls;
    final requiredProjectProvidedDeps =
        _requiredDeps(allExtRemoteDeps, projectProvidedDeps);

    final localDeps = config.dependencies
        .where((el) => el.endsWith('.jar') || el.endsWith('.aar'))
        .map((el) {
      return Artifact(
        scope: Scope.compile,
        coordinate: el,
        artifactFile: p.join(_fs.localDepsDir.path, el),
        packaging: p.extension(el).substring(1),
        dependencies: [],
        sourcesJar: null,
      );
    });
    await BuildUtils.extractAars(localDeps
        .where((el) => el.packaging == 'aar')
        .map((el) => el.artifactFile));

    var resolvedArtifacts = [
      ...requiredDeps,
      if (includeLocal) ...localDeps,
      if (includeAi2ProvidedDeps) ...await providedDependencies(config),
      if (includeProjectProvidedDeps) ...requiredProjectProvidedDeps,
    ];

    if (config.excludes.isNotEmpty) {
      resolvedArtifacts = resolvedArtifacts.where((artifact) {
        return !config.excludes.any(
            (excludePattern) => artifact.coordinate.contains(excludePattern));
      }).toList();
    }

    return resolvedArtifacts;
  }

  Future<List<Artifact>> buildLibArtifacts() async =>
      (await _retrieveArtifactsFromBox(buildLibsBox)).toList();

  Future<Artifact> _findArtifact(LazyBox<Artifact> box, String coord) async {
    final artifact = await box.get(coord);
    if (artifact == null || !await artifact.artifactFile.asFile().exists()) {
      _lgr
        ..err('Unable to find a required library in cache: $coord')
        ..log('Try running `bolt deps sync`', 'help  '.green());
      throw Exception();
    }
    return artifact;
  }

  Future<String> processorJar() async {
    // Check if user has placed processor.jar in the local processor directory
    // This allows using custom or pre-built processor versions
    final fs = GetIt.I<FileService>();
    final localProcessorDir = p.join(fs.boltHomeDir.path, 'libs', 'processor');
    final localProcessor = p.join(localProcessorDir, 'processor.jar');

    if (File(localProcessor).existsSync()) {
      _lgr.dbg('using local processor JAR at $localProcessor');

      // If using local processor, include all JARs in the processor directory
      // PLUS common dependencies that might be in the main libs directory
      final processorJars = <String>{};

      // Add all JARs from processor directory
      final processorDir = Directory(localProcessorDir);
      if (processorDir.existsSync()) {
        final allJars = processorDir
            .listSync()
            .whereType<File>()
            .where((f) => f.path.endsWith('.jar'))
            .map((f) => f.path)
            .toList();
        processorJars.addAll(allJars);
      }

      // Also check for common processor dependencies in the main libs directory
      final libsDir = Directory(fs.libsDir.path);
      if (libsDir.existsSync()) {
        // Include common annotation processor dependencies
        final dependencyPatterns = [
          'guava',
          'auto-service',
          'auto-common',
          'javapoet',
          'jackson',
          'gson',
          'fastjson',
          'protobuf',
          'compile-testing'
        ];

        final allLibs = libsDir
            .listSync()
            .whereType<File>()
            .where((f) => f.path.endsWith('.jar'))
            .toList();

        for (final jar in allLibs) {
          final name = p.basename(jar.path).toLowerCase();
          if (dependencyPatterns.any((pattern) => name.contains(pattern))) {
            processorJars.add(jar.path);
          }
        }
      }

      if (processorJars.isNotEmpty) {
        _lgr.dbg('processor classpath includes ${processorJars.length} JARs');
        return processorJars.join(BuildUtils.cpSeparator);
      }

      return localProcessor;
    }

    // Fall back to Maven-resolved processor artifact if local copy not found
    return (await _findArtifact(buildLibsBox, boltApCoord)).classesJar;
  }

  Future<String> r8Jar() async =>
      (await _findArtifact(buildLibsBox, r8Coord)).classesJar;

  /// Returns the classpath jars for ProGuard.  If the caller passes a
  /// non-null [version] it will be used to build the coordinate, otherwise the
  /// bundled default is returned.
  Future<Set<String>> pgJars([String? version]) async {
    // if the user has placed a ProGuard distribution in their Bolt tools
    // directory, always use that rather than downloading the Maven artifact.
    // this mirrors the behaviour we added for annotations and allows easy
    // customization of the ProGuard binary.
    final fs = GetIt.I<FileService>();
    final localPg =
        p.join(fs.boltHomeDir.path, 'libs', 'tools', 'proguard.jar');
    if (File(localPg).existsSync()) {
      //  _lgr.info('using local ProGuard JAR at $localPg');
      return {localPg};
    }

    final coord =
        version == null ? pgCoord : 'com.guardsquare:proguard-base:$version';
    return (await _findArtifact(buildLibsBox, coord))
        .classpathJars(await buildLibArtifacts());
  }

  Future<String> desugarJar() async {
    // Check if user has placed desugar.jar in the local tools directory
    // This allows easy customization and local override of the desugar tool
    final fs = GetIt.I<FileService>();
    final localDesugar =
        p.join(fs.boltHomeDir.path, 'libs', 'tools', 'desugar.jar');
    if (File(localDesugar).existsSync()) {
      _lgr.dbg('using local desugar JAR at $localDesugar');
      return localDesugar;
    }

    // Fall back to Maven-resolved desugar artifact if local copy not found
    return (await _findArtifact(buildLibsBox, desugarCoord)).classesJar;
  }

  /// Returns the path to AndroidRuntime.jar (AI2 runtime)
  /// First checks for local override at $BOLT_HOME/libs/AndroidRuntime.jar,
  /// then falls back to the Maven-resolved artifact from the cache.
  Future<String> androidRuntimeJar() async {
    // Check if user has placed AndroidRuntime.jar in the local libs directory
    // This allows using custom or pre-built Android runtime versions
    final fs = GetIt.I<FileService>();
    final localAndroidRuntime =
        p.join(fs.boltHomeDir.path, 'libs', 'AndroidRuntime.jar');
    if (File(localAndroidRuntime).existsSync()) {
      _lgr.dbg('using local AndroidRuntime JAR at $localAndroidRuntime');
      return localAndroidRuntime;
    }

    // Fall back to Maven-resolved runtime artifact if local copy not found
    return (await _findArtifact(buildLibsBox, ai2RuntimeCoord)).classesJar;
  }

  Future<Set<String>> manifMergerJars() async => [
        for (final lib in manifMergerAndDeps)
          (await _findArtifact(buildLibsBox, lib))
              .classpathJars(await buildLibArtifacts())
      ].flattened.toSet();

  Future<Set<String>> kotlincJars(String ktVersion) async =>
      (await _findArtifact(
        buildLibsBox,
        '$kotlinGroupId:kotlin-compiler-embeddable:$ktVersion',
      ))
          .classpathJars(await buildLibArtifacts());

  Future<Set<String>> kaptJars(String ktVersion) async => (await _findArtifact(
        buildLibsBox,
        '$kotlinGroupId:kotlin-annotation-processing-embeddable:$ktVersion',
      ))
          .classpathJars(await buildLibArtifacts());
}
