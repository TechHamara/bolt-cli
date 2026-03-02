import 'dart:io';

import 'package:checked_yaml/checked_yaml.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/constants.dart';

part 'android.dart';

part 'kotlin.dart';

part 'config.g.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
  includeIfNull: false,
)
class Config {
  @JsonKey(required: true)
  final String version;

  @JsonKey(disallowNullValue: true)
  final List<String> dependencies;

  @JsonKey(name: 'provided_dependencies', disallowNullValue: true)
  final List<String> providedDependencies;

  @JsonKey(name: 'min_sdk', disallowNullValue: true)
  final int minSdk;

  @JsonKey(disallowNullValue: true)
  final List<String> repositories;

  @JsonKey(disallowNullValue: true)
  final String homepage;

  @JsonKey(disallowNullValue: true)
  final String license;

  @JsonKey(disallowNullValue: true)
  final bool desugar;

  @JsonKey(disallowNullValue: true)
  final List<String> assets;

  @JsonKey(disallowNullValue: true)
  final List<String> authors;

  @JsonKey(disallowNullValue: true)
  final Kotlin kotlin;

  // convenience alias accepted at the top level of bolt.yml.  This field is
  // not used at runtime; it's only here so that `checked_yaml` will permit the
  // key and we can merge it into [kotlin] in the constructor.
  @JsonKey(name: 'kotlin_version', disallowNullValue: true)
  final String? kotlinVersionAlias;

  @JsonKey(disallowNullValue: true)
  final bool proguard;

  // version of proguard to download/use when the shrink task is executed. The
  // value defaults to whatever Bolt itself ships with so that the template can
  // interpolate the constant instead of hard‑coding it.
  @JsonKey(name: 'proguard_version', disallowNullValue: true)
  final String proguardVersion;

  @JsonKey(name: 'R8', disallowNullValue: true)
  final bool r8;

  @JsonKey(name: 'desugar_sources', disallowNullValue: true)
  final bool desugarSources;

  @JsonKey(name: 'desugar_deps', disallowNullValue: true)
  final bool desugarDeps;

  @JsonKey(name: 'desugar_dex', disallowNullValue: true)
  final bool desugarDex;

  @JsonKey(name: 'compile_time', disallowNullValue: true)
  final List<String> compileTime;

  @JsonKey(disallowNullValue: true)
  final List<String> excludes;

  @JsonKey(name: 'gen_docs', disallowNullValue: true)
  final bool genDocs;

  @JsonKey(name: 'auto_version', disallowNullValue: true)
  final bool autoVersion;

  @JsonKey(disallowNullValue: true)
  final bool deannonate;

  @JsonKey(name: 'filter_mit_classes', disallowNullValue: true)
  final bool filterMitClasses;

  @JsonKey(disallowNullValue: true)
  final String author;

  @JsonKey(name: 'android_sdk', disallowNullValue: true)
  final int androidSdk;

  @JsonKey(disallowNullValue: true)
  final bool java8;

  @JsonKey(disallowNullValue: true)
  final bool jetify;

  Config({
    required this.version,
    this.minSdk = 14,
    this.homepage = '',
    this.license = '',
    this.desugar = false,
    this.assets = const [],
    this.authors = const [],
    this.dependencies = const [],
    this.providedDependencies = const [],
    this.repositories = const [],
    // callers may either supply a full Kotlin object or just the shorthand
    // version alias – we resolve that below in the initializer list.
    Kotlin? kotlin,
    this.kotlinVersionAlias,
    this.proguard = false,
    this.proguardVersion = defaultProguardVersion,
    this.r8 = false,
    this.desugarSources = false,
    this.desugarDeps = false,
    this.desugarDex = false,
    this.compileTime = const [],
    this.excludes = const [],
    this.genDocs = false,
    this.autoVersion = false,
    this.deannonate = false,
    this.filterMitClasses = false,
    this.author = '',
    this.androidSdk = 33,
    this.java8 = false,
    this.jetify = false,
  }) : kotlin = kotlinVersionAlias != null
            ? Kotlin(compilerVersion: kotlinVersionAlias)
            : (kotlin ?? const Kotlin(compilerVersion: defaultKtVersion));

  String get authorName {
    if (author.isNotEmpty) return author;
    return authors.join(', ');
  }

  // ignore: strict_raw_type
  factory Config._fromJson(Map json) => _$ConfigFromJson(json);

  static Future<Config?> load(File configFile, Logger lgr) async {
    if (configFile.existsSync()) {
      try {
        return checkedYamlDecode(
            await configFile.readAsString(), (json) => Config._fromJson(json!));
      } catch (e) {
        lgr.err(e.toString());
      }
    }
    return null;
  }
}
