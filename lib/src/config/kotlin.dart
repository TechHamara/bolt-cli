part of 'config.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
)
class Kotlin {
  // the field we actually store on the object.  as noted in config.dart, the
  // config file may contain `compiler_version` or `kotlin_version` so the
  // custom factory below takes care of both.
  final String compilerVersion;

  const Kotlin({
    required this.compilerVersion,
  });

  factory Kotlin.fromJson(Map<String, dynamic> json) {
    // read either key; fail if neither present (checked_yaml will still catch
    // nulls because of the constructor requirement).
    final value = json['compiler_version'] ?? json['kotlin_version'];
    if (value == null) {
      throw ArgumentError('Missing Kotlin compiler version');
    }
    return Kotlin(compilerVersion: value as String);
  }

  Map<String, dynamic> toJson() => _$KotlinToJson(this);
}
