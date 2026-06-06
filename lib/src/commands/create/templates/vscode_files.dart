String getVscodeSettingsJson(Iterable<String> classesJars) {
  final entries = [
    '"deps/**/*.jar"',
    ...classesJars.map((el) => '"${el.replaceAll('\\', '/')}"')
  ].join(',\n    ');

  return '''
{
  "java.project.sourcePaths": ["src"],
  "java.project.outputPath": "build/classes",
  "java.project.referencedLibraries": [
    $entries
  ]
}
''';
}

final vscodeSettingsJson = getVscodeSettingsJson([]);

