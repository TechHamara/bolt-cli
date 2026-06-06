import 'dart:io' show File;

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

    final localAnnPath = p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'annotations.jar');
    final localRuntimePath = p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'runtime.jar');
    final localOverrides = <Artifact>[];

    final cachedDeps = await _retrieveArtifactsFromBox(ai2ProvidedDepsBox);

    if (File(localAnnPath).existsSync()) {
      cachedDeps.removeWhere((el) => el.coordinate.startsWith('io.github.techhamara.bolt:annotations:'));
      localOverrides.add(Artifact(
        coordinate: 'io.github.techhamara.bolt:annotations:$ai2AnnotationVersion',
        scope: Scope.provided,
        artifactFile: localAnnPath,
        packaging: 'jar',
        dependencies: [],
        sourcesJar: null,
      ));
    }

    if (File(localRuntimePath).existsSync()) {
      cachedDeps.removeWhere((el) => el.coordinate.startsWith('io.github.techhamara.bolt:runtime:'));
      localOverrides.add(Artifact(
        coordinate: 'io.github.techhamara.bolt:runtime:$ai2RuntimeVersion',
        scope: Scope.provided,
        artifactFile: localRuntimePath,
        packaging: 'jar',
        dependencies: [],
        sourcesJar: null,
      ));
    }

    if (config == null) {
      return [
        ...cachedDeps,
        ...local,
        ...localOverrides,
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
    }).toList();

    final localProvidedAars = extLocalProvided
        .where((el) => el.packaging == 'aar')
        .map((el) => el.artifactFile);
    if (localProvidedAars.isNotEmpty) {
      await BuildUtils.extractAars(localProvidedAars);
    }

    return [
      ...cachedDeps,
      ...extProvidedDeps,
      ...extLocalProvided,
      ...local,
      ...localOverrides,
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
    final fs = GetIt.I<FileService>();
    final localProc =
        p.join(fs.boltHomeDir.path, 'libs', 'tools', 'processor.jar');
    if (File(localProc).existsSync()) {
      return localProc;
    }
    return (await _findArtifact(buildLibsBox, boltApCoord)).classesJar;
  }

  Future<String> r8Jar() async {
    final fs = GetIt.I<FileService>();
    final localR8 =
        p.join(fs.boltHomeDir.path, 'libs', 'tools', 'r8.jar');
    if (File(localR8).existsSync()) {
      return localR8;
    }
    return (await _findArtifact(buildLibsBox, r8Coord)).classesJar;
  }

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
    final fs = GetIt.I<FileService>();
    final localDesugar =
        p.join(fs.boltHomeDir.path, 'libs', 'tools', 'desugar.jar');
    if (File(localDesugar).existsSync()) {
      return localDesugar;
    }
    return (await _findArtifact(buildLibsBox, desugarCoord)).classesJar;
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
