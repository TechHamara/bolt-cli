import 'package:json_annotation/json_annotation.dart';

part 'minimize.g.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: true,
  includeIfNull: false,
)
class MinimizeConfig {
  @JsonKey(name: 'exclude_dependency', disallowNullValue: true, defaultValue: [])
  final List<String> excludeDependency;

  @JsonKey(name: 'exclude_project', disallowNullValue: true, defaultValue: [])
  final List<String> excludeProject;

  MinimizeConfig({
    required this.excludeDependency,
    required this.excludeProject,
  });

  // ignore: strict_raw_type
  factory MinimizeConfig.fromJson(Map json) => _$MinimizeConfigFromJson(json);
}
