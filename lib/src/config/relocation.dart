import 'package:json_annotation/json_annotation.dart';

part 'relocation.g.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
  includeIfNull: false,
)
class RelocationConfig {
  @JsonKey(name: 'EnableAutoRelocation', disallowNullValue: true, defaultValue: true)
  final bool enableAutoRelocation;

  @JsonKey(name: 'skipStringContants', disallowNullValue: true, defaultValue: true)
  final bool skipStringConstants; // note: keeping the JSON key as skipStringContants for typo compatibility if user meant it, but dart field is skipStringConstants

  @JsonKey(disallowNullValue: true, defaultValue: [])
  final List<String> include;

  @JsonKey(disallowNullValue: true, defaultValue: [])
  final List<String> exclude;

  RelocationConfig({
    required this.enableAutoRelocation,
    required this.skipStringConstants,
    required this.include,
    required this.exclude,
  });

  // ignore: strict_raw_type
  factory RelocationConfig.fromJson(Map json) => _$RelocationConfigFromJson(json);
}
