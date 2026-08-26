import 'package:json_annotation/json_annotation.dart';

part 'ndk.g.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
  includeIfNull: false,
)
class Ndk {
  @JsonKey(disallowNullValue: true)
  final bool enabled;

  @JsonKey(disallowNullValue: true)
  final String? version;

  @JsonKey(name: 'build_system', disallowNullValue: true)
  final String buildSystem; // e.g. "ndk-build" or "cmake"

  Ndk({
    this.enabled = false,
    this.version,
    this.buildSystem = 'ndk-build',
  });

  factory Ndk.fromJson(Map json) => _$NdkFromJson(json);
  Map<String, dynamic> toJson() => _$NdkToJson(this);
}
