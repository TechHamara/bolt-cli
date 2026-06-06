part of 'old_config.dart';

@JsonSerializable(
  anyMap: true,
  checked: true,
  disallowUnrecognizedKeys: false,
)
class Assets {
  final String? icon;

  @JsonKey(includeIfNull: false)
  final List<String>? other;

  Assets({this.icon, this.other});

  factory Assets.fromJson(Map<String, dynamic> json) => _$AssetsFromJson(json);

  Map<String, dynamic> toJson() => _$AssetsToJson(this);
}
