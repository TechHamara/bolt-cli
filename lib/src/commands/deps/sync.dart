import 'dart:convert' show jsonDecode, jsonEncode;
import 'dart:io';

import 'package:args/command_runner.dart';
import 'package:collection/collection.dart';
import 'package:get_it/get_it.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import 'package:xrange/xrange.dart';

import 'package:tint/tint.dart';

import 'package:bolt/src/commands/build/utils.dart';
import 'package:bolt/src/commands/create/templates/eclipse_files.dart';
import 'package:bolt/src/commands/create/templates/intellij_files.dart';
import 'package:bolt/src/commands/create/templates/vscode_files.dart';
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/resolver/artifact.dart';
import 'package:bolt/src/resolver/resolver.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/lib_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';
import 'package:bolt/src/utils/file_extension.dart';

const ai2RuntimeCoord =
    'io.github.techhamara.bolt:runtime:$ai2RuntimeVersion';
const _buildToolCoords = [
  boltApCoord,
  r8Coord,
  pgCoord,
  desugarCoord,
  ...manifMergerAndDeps,
];

class SyncCommand extends Command<int> {
  static final _fs = GetIt.I<FileService>();
  static final _lgr = GetIt.I<Logger>();

  final _stopwatch = Stopwatch();

  SyncCommand() {
    argParser
      ..addFlag('dev-deps', abbr: 'd', help: 'Syncs only the dev-dependencies.')
      ..addFlag('project-deps',
          abbr: 'p', help: 'Syncs only the project dependencies.')
      ..addFlag('force',
          abbr: 'f',
          help:
              'Forcefully syncs all the dependencies even if they are up-to-date.');
  }

  @override
  String get description => 'Syncs dev and project dependencies.';

  @override
  String get name => 'sync';

  void _logFinalLine(bool success, [int libraryCount = 0]) {
    var line = '\n';
    line += '> ';
    line += success ? 'SYNC SUCCESSFUL '.green() : 'SYNC FAILED '.red();
    final timeMs = _stopwatch.elapsedMilliseconds;
    final sec = (timeMs / 1000).floor();
    final ms = timeMs % 1000;
    line += 'in ${sec}s ${ms}ms'.grey();
    if (success && libraryCount > 0) {
      line += ' ($libraryCount ${libraryCount == 1 ? 'library' : 'libraries'} synced)'.grey();
    }
    _lgr.log(line);
  }

  @override
  Future<int> run({String title = 'Initializing', bool showSummary = true}) async {
    _stopwatch.reset();
    _stopwatch.start();
    _lgr.startTask(title);

    var syncedCount = 0;

    final onlyDevDeps = (argResults?['dev-deps'] ?? false) as bool;
    final onlyExtDeps = (argResults?['project-deps'] ?? false) as bool;
    final useForce = (argResults?['force'] ?? false) as bool;

    final config = await Config.load(_fs.configFile, _lgr);
    if (config == null && !onlyDevDeps) {
      _lgr.warn('Not in a Bolt project, only dev-dependencies will be synced.');
    }

    final envCacheFile = p.join(_fs.dotBoltDir.path, 'env.json').asFile();
    String gradleVersion = 'not installed';
    String mavenVersion = 'not installed';

    bool hasEnvCache = false;
    if (envCacheFile.existsSync()) {
      try {
        final cache = jsonDecode(envCacheFile.readAsStringSync());
        gradleVersion = cache['gradleVersion'] ?? 'not installed';
        mavenVersion = cache['mavenVersion'] ?? 'not installed';
        hasEnvCache = true;
      } catch (_) {}
    }

    if (!hasEnvCache) {
      final results = await Future.wait([
        Process.run('gradle', ['--version'], runInShell: true).catchError((_) => null),
        Process.run('mvn', ['--version'], runInShell: true).catchError((_) => null),
      ]);

      if (results[0] != null && results[0]!.exitCode == 0) {
        final output = results[0]!.stdout.toString();
        final match = RegExp(r'Gradle\s+([0-9.]+)').firstMatch(output);
        if (match != null) {
          gradleVersion = match.group(1)!;
        }
      }

      if (results[1] != null && results[1]!.exitCode == 0) {
        final output = results[1]!.stdout.toString();
        final match = RegExp(r'Apache Maven\s+([0-9.]+)').firstMatch(output);
        if (match != null) {
          mavenVersion = match.group(1)!;
        }
      }

      try {
        await envCacheFile.parent.create(recursive: true);
        await envCacheFile.writeAsString(jsonEncode({
          'gradleVersion': gradleVersion,
          'mavenVersion': mavenVersion,
        }));
      } catch (_) {}
    }

    String? customGradleLibPath;
    try {
      final homeDir = Platform.isWindows ? Platform.environment['UserProfile'] : Platform.environment['HOME'];
      if (homeDir != null) {
        final libsDir = Directory(p.join(homeDir, '.bolt', 'libs'));
        if (libsDir.existsSync()) {
          final gradleDirs = libsDir.listSync()
              .whereType<Directory>()
              .where((d) => p.basename(d.path).startsWith('gradle-'))
              .toList();
          if (gradleDirs.isNotEmpty) {
            gradleDirs.sort((a, b) => p.basename(b.path).compareTo(p.basename(a.path)));
            final libPath = p.join(gradleDirs.first.path, 'lib');
            if (Directory(libPath).existsSync()) {
              customGradleLibPath = libPath;
            }
          }
        }
        if (customGradleLibPath == null) {
          final exactPath = p.join(homeDir, '.bolt', 'libs', 'gradle-8.14.5', 'lib');
          if (Directory(exactPath).existsSync()) {
            customGradleLibPath = exactPath;
          }
        }
      }
    } catch (_) {}

    if (gradleVersion == 'not installed' && customGradleLibPath != null) {
      final match = RegExp('gradle-([0-9.]+)').firstMatch(customGradleLibPath);
      if (match != null) {
        gradleVersion = match.group(1)!;
      }
    }

    _lgr.info('Gradle Version: $gradleVersion', console: false);
    _lgr.info('Maven Version: $mavenVersion', console: false);
    _lgr.info('Maven Resolver: v2.0.18', console: false);

    await GetIt.I.isReady<LibService>();
    final libService = GetIt.I<LibService>();

    // Clear all the cache if force is used.
    if (useForce) {
      if (!onlyExtDeps) {
        await libService.ai2ProvidedDepsBox.clear();
        await libService.buildLibsBox.clear();
      }
      if (!onlyDevDeps && config != null) {
        await libService.extensionDepsBox!.clear();
      }
    }

    final ktVersion = config?.kotlin.compilerVersion ?? defaultKtVersion;
    final pgVersion = config?.proguardVersion ?? defaultProguardVersion;
    // rebuild the base tool list so that the proguard entry respects the
    // user's choice.  we can't mutate the const list defined above.
    // if a local ProGuard JAR exists under `$BOLT_HOME/libs/tools`, we
    // won't bother downloading anything from Maven; the runtime will prefer
    // the local file instead (handled by lib_service.pgJars()).
    final toolsCoord = <String>[
      boltApCoord,
      r8Coord,
      desugarCoord,
      ...manifMergerAndDeps,
      // kotlin compiler/annotation-processing pair
      '$kotlinGroupId:kotlin-compiler-embeddable:$ktVersion',
      '$kotlinGroupId:kotlin-annotation-processing-embeddable:$ktVersion',
    ];
    final localPgPath =
        p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'proguard.jar');
    if (!File(localPgPath).existsSync()) {
      toolsCoord.insert(2, 'com.guardsquare:proguard-base:$pgVersion');
    } else {
      ///   _lgr.info(
      ///     'local ProGuard override detected at $localPgPath; skipping download');
    }

    // Dev deps to be resolved
    final ai2ProvidedDepsToFetch = <String>{};
    final toolsToFetch = <String>{};

    var ai2ProvidedDepArtifacts = await libService.providedDependencies(null);
    var buildLibArtifacts = await libService.buildLibArtifacts();

    final localAnnExists = File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'annotations.jar')).existsSync();
    final localRuntimeExists = File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'runtime.jar')).existsSync();

    // Add every un-cached dev dep to fetch list.  We also need the AI2
    // annotations JAR so that the compiler has the annotation classes on the
    // classpath (previously only the annotation *processor* was downloaded).
    if (!localRuntimeExists && ai2ProvidedDepArtifacts
        .none((el) => el.coordinate == ai2RuntimeCoord)) {
      ai2ProvidedDepsToFetch.add(ai2RuntimeCoord);
    }
    // ensure annotation jar is always available; it lives alongside the runtime
    // but is not added by default earlier, which resulted in imports like
    // `com.google.appinventor.components.annotations.DesignerComponent`
    // failing at compile time.
    final annCoord =
        'io.github.techhamara.bolt:annotations:$ai2AnnotationVersion';
    if (!localAnnExists && ai2ProvidedDepArtifacts.none((el) => el.coordinate == annCoord)) {
      ai2ProvidedDepsToFetch.add(annCoord);
    }

    for (final coord in toolsCoord) {
      bool isLocalOverride = false;
      if (coord == boltApCoord && File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'processor.jar')).existsSync()) {
        isLocalOverride = true;
      } else if (coord == r8Coord && File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'r8.jar')).existsSync()) {
        isLocalOverride = true;
      } else if (coord == desugarCoord && File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'desugar.jar')).existsSync()) {
        isLocalOverride = true;
      }

      if (!isLocalOverride && buildLibArtifacts.none((el) => el.coordinate == coord)) {
        toolsToFetch.add(coord);
      }
    }

    // Add every non existent dev dep to the fetch list. This can happen when
    // the said dep was deleted or the local Maven repo location was changed.
    ai2ProvidedDepsToFetch.addAll(
      ai2ProvidedDepArtifacts
          .where((el) => !el.artifactFile.asFile().existsSync())
          .map((el) => el.coordinate)
          .where((el) => el.trim().isNotEmpty && el.contains(':')),
    );
    toolsToFetch.addAll(
      buildLibArtifacts
          .where((el) => !el.artifactFile.asFile().existsSync())
          .map((el) => el.coordinate)
          .where((el) => el.trim().isNotEmpty && el.contains(':')),
    );

    if (localRuntimeExists) {
      ai2ProvidedDepsToFetch.remove(ai2RuntimeCoord);
    }
    if (localAnnExists) {
      ai2ProvidedDepsToFetch.remove(annCoord);
    }
    if (File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'processor.jar')).existsSync()) {
      toolsToFetch.remove(boltApCoord);
    }
    if (File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'r8.jar')).existsSync()) {
      toolsToFetch.remove(r8Coord);
    }
    if (File(p.join(_fs.boltHomeDir.path, 'libs', 'tools', 'desugar.jar')).existsSync()) {
      toolsToFetch.remove(desugarCoord);
    }

    // Stop the init task
    _lgr.stopTask();

    if (!onlyExtDeps &&
        (ai2ProvidedDepsToFetch.isNotEmpty || toolsToFetch.isNotEmpty)) {
      _lgr.startTask('Syncing dev-dependencies');
      try {
        final results = await Future.wait([
          sync(
            cacheBox: libService.ai2ProvidedDepsBox,
            coordinates: {Scope.compile: ai2ProvidedDepsToFetch},
            downloadSources: true,
            excludeCoordinates: ['com.google.android:android:2.1.2'],
          ),
          sync(
            cacheBox: libService.buildLibsBox,
            coordinates: {Scope.compile: toolsToFetch},
          ),
        ]);
        syncedCount += results[0].length + results[1].length;
      } catch (_) {
        _lgr.stopTask(false);
        _stopwatch.stop();
        if (showSummary) _logFinalLine(false);
        return 1;
      }

      await Future.wait([
        _removeRogueDeps([
          ai2RuntimeCoord,
          'android-${config?.androidSdk ?? androidPlatformSdkVersion}.jar',
          'kawa.jar',
          'physicaloid.jar'
        ], libService.ai2ProvidedDepsBox),
      ]);
      _lgr.stopTask();
    } else if (!onlyExtDeps) {
      _lgr
        ..startTask('Syncing dev-dependencies')
        ..stopTask();
    }
    await BuildUtils.extractAars(
      ai2ProvidedDepArtifacts
          .where((el) => el.artifactFile.endsWith('.aar'))
          .where((el) =>
              !el.classesJar.asFile().existsSync() ||
              !BuildUtils.resourceFromExtractedAar(
                      el.artifactFile, 'AndroidManifest.xml')
                  .existsSync())
          .map((el) => el.artifactFile),
    );

    // Exit if this is not a Bolt project.
    if (config == null) {
      _stopwatch.stop();
      if (showSummary) {
        _logFinalLine(true, syncedCount);
      }
      return 0;
    }

    // Update the vars after syncing dev deps.
    ai2ProvidedDepArtifacts = await libService.providedDependencies(null);
    buildLibArtifacts = await libService.buildLibArtifacts();

    Hive.init(_fs.dotBoltDir.path);
    final timestampBox = await Hive.openLazyBox<DateTime>(timestampBoxName);

    final extensionDeps = await libService.extensionDependencies(config);
    final needSync = await _doProjectDepsNeedSync(timestampBox, extensionDeps);
    if (useForce || (!onlyDevDeps && needSync)) {
      _lgr.startTask('Syncing project dependencies');

      final extDepCoords = config.dependencies
          .where((el) => !el.endsWith('.jar') && !el.endsWith('.aar'));
      final extProvidedDepCoords = config.providedDependencies
          .where((el) => !el.endsWith('.jar') && !el.endsWith('.aar'));

      try {
        final res1 = await sync(
          cacheBox: libService.extensionDepsBox!,
          coordinates: {Scope.provided: extProvidedDepCoords},
          repositories: config.repositories,
          downloadSources: true,
        );
        syncedCount += res1.length;

        final providedDepArtifacts =
            await libService.providedDependencies(config);
        final res2 = await sync(
          cacheBox: libService.extensionDepsBox!,
          coordinates: {Scope.compile: extDepCoords},
          repositories: config.repositories,
          providedArtifacts: providedDepArtifacts,
          downloadSources: true,
        );
        syncedCount += res2.length;
        await timestampBox.put(configTimestampKey, DateTime.now());
      } catch (_) {
        _lgr.stopTask(false);
        _stopwatch.stop();
        if (showSummary) _logFinalLine(false);
        return 1;
      }
      await _removeRogueDeps({...extDepCoords, ...extProvidedDepCoords},
          libService.extensionDepsBox!);
      _lgr.stopTask();
    } else if (!onlyDevDeps) {
      _lgr
        ..startTask('Syncing project dependencies')
        ..stopTask();
    }

    _lgr.startTask('Adding resolved dependencies to your IDE\'s lib index');

    try {
      final extensionDeps = await libService.extensionDependencies(config,
          includeProjectProvidedDeps: true);

      await BuildUtils.extractAars(
        extensionDeps
            .where((el) => el.artifactFile.endsWith('.aar'))
            .where((el) =>
                !el.classesJar.asFile().existsSync() ||
                !BuildUtils.resourceFromExtractedAar(
                        el.artifactFile, 'AndroidManifest.xml')
                    .existsSync())
            .map((el) => el.artifactFile),
      );

      await _updateIntellijLibIndex(ai2ProvidedDepArtifacts, extensionDeps);
      await _updateEclipseClasspath(ai2ProvidedDepArtifacts, extensionDeps);
      await _updateVscodeSettings(ai2ProvidedDepArtifacts, extensionDeps);
    } catch (_) {
      _lgr.stopTask(false);
      _stopwatch.stop();
      if (showSummary) _logFinalLine(false);
      return 1;
    }

    _lgr.stopTask();
    _stopwatch.stop();
    if (showSummary) {
      _logFinalLine(true, syncedCount);
    }
    return 0;
  }

  static Future<bool> _doProjectDepsNeedSync(
      LazyBox<DateTime> timestampBox, List<Artifact> projectDeps) async {
    // Re-fetch deps if they are outdated, ie, if the config file is modified
    // or if the dep artifacts are missing
    final configFileModified = (await timestampBox.get(configTimestampKey))
            ?.isBefore(_fs.configFile.lastModifiedSync()) ??
        true;
    final isAnyDepMissing = projectDeps.any((el) =>
        !el.artifactFile.endsWith('.pom') &&
        !el.artifactFile.asFile().existsSync());
    return configFileModified || isAnyDepMissing;
  }

  static Future<Iterable<Artifact>> _removeRogueDeps(
      Iterable<String> primaryArtifactCoords, LazyBox<Artifact> cache,
      [bool putInCache = true]) async {
    final actualDeps = <Artifact>{};

    for (final el in primaryArtifactCoords) {
      final artifact = await cache.get(el);
      if (artifact == null) {
        continue;
      }

      actualDeps.add(artifact);

      final depArtifacts = await Future.wait([
        for (final dep in artifact.dependencies) cache.get(dep),
      ]);
      actualDeps.addAll(depArtifacts.nonNulls);

      final transDepArtifacts = await Future.wait([
        for (final dep in depArtifacts.nonNulls)
          _removeRogueDeps(dep.dependencies, cache, false),
      ]);
      actualDeps.addAll(transDepArtifacts.nonNulls.flattened);
    }

    if (putInCache) {
      await cache.clear();
      await cache.putAll({
        for (final el in actualDeps) el.coordinate: el,
      });
    }
    return actualDeps;
  }

  Future<List<Artifact>> sync({
    required LazyBox<Artifact> cacheBox,
    required Map<Scope, Iterable<String>> coordinates,
    Iterable<String> repositories = const [],
    Iterable<Artifact> providedArtifacts = const [],
    bool downloadSources = false,
    List<String> excludeCoordinates = const [],
  }) async {
    _lgr.info('Resolving ${coordinates.values.flattened.length} artifacts...');
    final resolver = ArtifactResolver(repos: repositories.toSet());

    List<Artifact> resolvedDeps = [];
    try {
      resolvedDeps = (await Future.wait([
        for (final entry in coordinates.entries)
          for (final coord in entry.value)
            resolver.resolveArtifact(coord, entry.key,
                exclude: excludeCoordinates),
      ]))
          .flattened
          .toList(growable: true);
    } catch (e, s) {
      resolver.closeHttpConn();
      _lgr
        ..err(e.toString())
        ..dbg(s.toString());
      rethrow;
    }

    final directDeps =
        {for (final entry in coordinates.entries) entry.value}.flattened;

    try {
      // Resolve version conflicts
      _lgr.info('Resolving version conflicts...');
      resolvedDeps =
          (await _resolveVersionConflicts(resolvedDeps, directDeps, resolver))
              .toList(growable: true);
    } catch (e) {
      resolver.closeHttpConn();
      rethrow;
    }

    // When resolving extension deps, remove AI2 provided deps from dependencies
    // field and add them to the providedDependencies field of each artifact.
    if (providedArtifacts.isNotEmpty) {
      final providedDeps = <String>{};
      resolvedDeps.removeWhere((el) {
        final provided = _providedAlternative(
          '${el.groupId}:${el.artifactId}',
          providedArtifacts,
          coordinates.values.flattened,
        );
        if (provided != null) {
          providedDeps.add(el.coordinate);
          _lgr.dbg(
              'Provided alternative found for ${el.coordinate}: ${provided.coordinate}');
          return true;
        }
        return false;
      });
      for (final el in resolvedDeps) {
        el.dependencies.removeWhere((coord) => providedDeps.contains(coord));
      }
    }

    // Update the versions of transitive dependencies once the version conflicts
    // are resolved.
    resolvedDeps = resolvedDeps.map((dep) {
      dep.dependencies = List.of(dep.dependencies)
          .map((el) {
            final artifact = resolvedDeps.firstWhereOrNull((art) =>
                '${art.groupId}:${art.artifactId}' ==
                el.split(':').take(2).join(':'));
            return artifact?.coordinate;
          })
          .nonNulls
          .toList();
      return dep;
    }).toList();

    // Download the artifacts and then add them to the cache
    _lgr.info('Downloading resolved artifacts...');
    try {
      await Future.wait([
        for (final dep in resolvedDeps) resolver.downloadArtifact(dep),
        if (downloadSources)
          for (final dep in resolvedDeps) resolver.downloadSourcesJar(dep),
        cacheBox.putAll({
          for (final dep in resolvedDeps) dep.coordinate: dep,
        }),
      ]);
    } catch (e) {
      resolver.closeHttpConn();
      rethrow;
    }

    resolver.closeHttpConn();
    return resolvedDeps;
  }

  Future<Iterable<Artifact>> _resolveVersionConflicts(
      Iterable<Artifact> resolvedArtifacts,
      Iterable<String> directDeps,
      ArtifactResolver resolver) async {
    final sameArtifacts = resolvedArtifacts
        .groupListsBy((el) => '${el.groupId}:${el.artifactId}')
        .entries;

    if (!sameArtifacts.any((el) => el.value.length > 1)) {
      return resolvedArtifacts;
    }

    final result = <Artifact>[];
    final newCoordsToReResolve = <String, Scope>{};

    for (final entry in sameArtifacts) {
      // Filter deps that have ranges defined
      final rangedVersionDeps =
          entry.value.where((el) => el.version.range != null).toSet();

      final updatedScope = entry.value.any((el) => el.scope == Scope.compile)
          ? Scope.compile
          : Scope.runtime;

      if (rangedVersionDeps.isNotEmpty) {
        _lgr.dbg('Total ranged: ${rangedVersionDeps.length}');

        // A singleton range is a range that allows only one exact value.
        // Eg: [1.2.3]
        final singletonVersionDeps = rangedVersionDeps
            .where((el) => el.version.range!.isSingleton)
            .toSet();

        // In ranged version deps, select the singleton version if there exist
        // "only one" and if it doesn't conflict with other ranges. Otherwise
        // it's an error.
        if (singletonVersionDeps.isNotEmpty) {
          // The singleton must be a part of each range for it to not conflict.
          final everyRangeContainsSingleton = rangedVersionDeps.every((el) => el
              .version.range!
              .encloses(singletonVersionDeps.first.version.range!));

          if (singletonVersionDeps.length > 1 || !everyRangeContainsSingleton) {
            throw Exception(
                'Unable to resolve version conflict for ${entry.key}:\n'
                'multiple versions found: ${singletonVersionDeps.map((e) => e.version.range).join(', ')}');
          } else {
            final pickedArtifact = singletonVersionDeps.first;
            // Update coordinate with ranged version to the final picked version.
            // For eg: com.example:[1.2.3] -> com.example:1.2.3
            pickedArtifact.coordinate = [
              ...pickedArtifact.coordinate.split(':').take(2),
              pickedArtifact.version.range!.upper!
            ].join(':');

            _lgr.dbg(
                '${entry.value.length} versions for ${entry.key} found; using ${pickedArtifact.version} because its a direct dep');
            result.add(pickedArtifact..scope = updatedScope);
            continue;
          }
        }

        // If there's no singleton version, then we need to first find the
        // intersection of all the ranges and then pick a version that falls
        // in it.
        final intersection =
            _intersection(rangedVersionDeps.map((e) => e.version.range!));
        if (intersection == null) {
          throw Exception(
              'Unable to resolve version conflict for ${entry.key}:\n'
              'multiple versions found: ${rangedVersionDeps.map((e) => e.version.range).join(', ')}');
        }

        Version? pickedVersion;
        if (intersection.upperBounded) {
          pickedVersion = intersection.upper!;
        } else if (intersection.lowerBounded) {
          pickedVersion = intersection.lower!;
        } else {
          // If the intersection is all infinity, then iterate through all the
          // ranges and pick any version - upper or lower - that we find first.
          for (final dep in rangedVersionDeps) {
            if (dep.version.range!.upperBounded) {
              pickedVersion = dep.version.range!.upper!;
              break;
            } else if (dep.version.range!.lowerBounded) {
              pickedVersion = dep.version.range!.lower!;
              break;
            }
          }
          if (pickedVersion == null) {
            throw Exception(
                'Unable to resolve version conflict for ${entry.key}:\n'
                'multiple versions found: ${rangedVersionDeps.map((e) => e.version.range).join(', ')}');
          }
        }

        final pickedCoordinate = '${entry.key}:$pickedVersion';
        final pickedArtifacts = rangedVersionDeps
            .where((el) => el.coordinate == pickedCoordinate)
            .toList(growable: true);

        // If `pickedArtifacts` is empty, then it means that this version of
        // this artifact wasn't resolved. We store such artifacts and there deps
        // in the `newArtifactsToReResolve` list and resolve them later.
        if (pickedArtifacts.isEmpty) {
          newCoordsToReResolve.putIfAbsent(
              pickedCoordinate, () => updatedScope);
          continue;
        }

        result.add(pickedArtifacts.first..scope = updatedScope);
        continue;
      }

      if (entry.value.length == 1) {
        result.add(entry.value.first..scope = updatedScope);
        continue;
      }

      // If this artifact is defined as a direct dep, use that version.
      // Note: when this method is called from the build command, the `coordinates`
      // are the direct deps.
      final directDep = {
        for (final coord in directDeps)
          if (coord.split(':').take(2).join(':') == '${entry.key}:') coord
      };
      if (directDep.isNotEmpty) {
        _lgr.dbg(
            '${entry.value.length} versions for ${entry.key} found; using ${directDep.first.split(':').last} because its a direct dep');
        final artifact =
            entry.value.firstWhere((el) => el.coordinate == directDep.first);
        result.add(artifact..scope = updatedScope);
        continue;
      }

      // If no version is ranged, select the highest version
      final nonRangedVersionDeps =
          entry.value.where((el) => el.version.range == null);
      final highestVersionDep = nonRangedVersionDeps
          .sorted((a, b) => a.version.compareTo(b.version))
          .last;
      _lgr.dbg(
          '${entry.value.length} versions for ${entry.key} found; using ${highestVersionDep.version} because its the highest');
      result.add(highestVersionDep..scope = updatedScope);
    }

    // Resolve any new coordinates that were added to the `newArtifactsToReResolve`
    if (newCoordsToReResolve.isNotEmpty) {
      _lgr.dbg(
          'Fetching new resolved versions for ${newCoordsToReResolve.keys.length} coordinates');

      List<List<Artifact>> resolvedArtifactsNew;
      try {
        resolvedArtifactsNew = await Future.wait([
          for (final entry in newCoordsToReResolve.entries)
            resolver.resolveArtifact(entry.key, entry.value),
        ]);
      } catch (e, s) {
        _lgr
          ..err(e.toString())
          ..dbg(s.toString());
        rethrow;
      }

      return await _resolveVersionConflicts(
        [...resolvedArtifactsNew.flattened, ...result],
        directDeps,
        resolver,
      );
    }

    return result;
  }

  static Artifact? _providedAlternative(
    String artifactIdent,
    Iterable<Artifact> providedDepArtifacts,
    Iterable<String> primaryArtifactCoords,
  ) {
    for (final val in providedDepArtifacts) {
      if (val.coordinate.startsWith(artifactIdent) &&
          !primaryArtifactCoords.contains(val.coordinate)) {
        return val;
      }
    }
    return null;
  }

  Range<T>? _intersection<T extends Comparable<T>>(Iterable<Range<T>> ranges) {
    var result = ranges.first;
    var previous = ranges.first;
    for (final range in ranges) {
      if (!range.connectedTo(previous)) {
        return null;
      } else {
        result = Range<T>.encloseAll([previous, range]);
      }
      previous = range;
    }
    return result;
  }

  Future<void> _updateEclipseClasspath(
      Iterable<Artifact> providedDeps, Iterable<Artifact> extensionDeps) async {
    final dotClasspathFile = p.join(_fs.cwd, '.classpath').asFile();
    if (!await dotClasspathFile.exists()) {
      return;
    }

    final classesJars = [
      ...providedDeps.map((el) => el.classesJar).nonNulls,
      ...extensionDeps.map((el) => el.classesJar).nonNulls,
    ];
    final sourcesJars = [
      ...providedDeps.map((el) => el.sourcesJar).nonNulls,
      ...extensionDeps.map((el) => el.sourcesJar).nonNulls,
    ];
    await dotClasspathFile
        .writeAsString(dotClasspath(classesJars, sourcesJars));
  }

  Future<void> _updateIntellijLibIndex(
      Iterable<Artifact> providedDeps, Iterable<Artifact> extensionDeps) async {
    final ideaDir = p.join(_fs.cwd, '.idea').asDir();
    if (!await ideaDir.exists()) {
      return;
    }

    final providedDepsLibXml =
        p.join(_fs.cwd, '.idea', 'libraries', 'provided-deps.xml').asFile(true);
    await providedDepsLibXml.writeAsString(
      ijProvidedDepsXml(
        providedDeps.map((el) => el.classesJar).nonNulls,
        providedDeps.map((el) => el.sourcesJar).nonNulls,
      ),
    );

    final libNames = <String>['deps', 'provided-deps'];
    for (final lib in extensionDeps) {
      final fileName = lib.coordinate.replaceAll(RegExp(r'(:|\.)'), '_');
      final xml =
          p.join(_fs.cwd, '.idea', 'libraries', '$fileName.xml').asFile(true);

      await xml.writeAsString('''
<component name="libraryTable">
  <library name="${lib.coordinate}">
    <CLASSES>
      <root url="jar://${lib.classesJar}!/" />
    </CLASSES>
    <SOURCES>
      ${lib.sourcesJar != null ? '<root url="jar://${lib.sourcesJar!}!/" />' : ''}
    </SOURCES>
    <JAVADOC />
  </library>
</component>
''');

      libNames.add(lib.coordinate);
    }

    final imlXml = p
        .join(_fs.cwd, '.idea')
        .asDir()
        .listSync()
        .firstWhereOrNull((el) => el is File && p.extension(el.path) == '.iml');
    if (imlXml == null) {
      throw Exception('Unable to find project\'s .iml file in .idea directory');
    }

    await imlXml.path.asFile().writeAsString(ijImlXml(libNames));
  }

  Future<void> _updateVscodeSettings(
      Iterable<Artifact> providedDeps, Iterable<Artifact> extensionDeps) async {
    final vscodeDir = p.join(_fs.cwd, '.vscode').asDir();
    if (!await vscodeDir.exists()) {
      return;
    }
    final vscodeSettingsFile = p.join(vscodeDir.path, 'settings.json').asFile();

    final classesJars = [
      ...providedDeps.map((el) => el.classesJar).nonNulls,
      ...extensionDeps.map((el) => el.classesJar).nonNulls,
    ];

    await vscodeSettingsFile
        .writeAsString(getVscodeSettingsJson(classesJars));
  }
}
