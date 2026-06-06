String githubActionsYaml(String extensionName) => '''
name: Build $extensionName

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          distribution: 'zulu'
          java-version: '11'

      - name: Setup Dart
        uses: dart-lang/setup-dart@v1
        
      - name: Install bolt-cli
        run: dart pub global activate --source git https://github.com/TechHamara/bolt-cli.git

      - name: Build Extension
        run: bolt build -o -b
        
      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: $extensionName
          path: out/*.aix
''';
