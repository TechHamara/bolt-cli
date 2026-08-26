import 'package:json_annotation/json_annotation.dart';

part 'strguard.g.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
  includeIfNull: false,
)
class StrGuardConfig {
  @JsonKey(disallowNullValue: true, defaultValue: false)
  final bool enabled;

  @JsonKey(disallowNullValue: true)
  final String? key;

  @JsonKey(disallowNullValue: true, defaultValue: [])
  final List<String> packages;

  StrGuardConfig({
    required this.enabled,
    this.key,
    required this.packages,
  });

  // ignore: strict_raw_type
  factory StrGuardConfig.fromJson(Map json) => _$StrGuardConfigFromJson(json);
}
