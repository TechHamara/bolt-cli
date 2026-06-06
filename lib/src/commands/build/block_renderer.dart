import 'dart:io';
import 'dart:math';

import 'package:image/image.dart' as img;
import 'package:path/path.dart' as p;

/// Generates block images for App Inventor extension documentation.
/// Exactly replicates authentic AI2 block SVG definitions, including precise
/// puzzle-piece socket cutouts and tabs for interconnected blocks.
///
/// **Bolt Branding:** All blocks use golden/neon colors for the Bolt CLI identity:
/// - Gold (#FFD700) for event blocks
/// - Neon Green (#00FF41) for method blocks
/// - Neon Cyan (#00D9FF) for property getters
/// - Neon Magenta (#FF00FF) for property setters
class BlockRenderer {
  // ======================== COLORS ========================
  // Golden/Neon Bright Colors for Bolt Theme
  static const _eventColor = '#FFD700'; // Golden
  static const _eventDark = '#FFA500'; // Dark Orange-Gold
  static const _methodColor = '#00FF41'; // Neon Green
  static const _methodDark = '#00CC33'; // Dark Neon Green
  static const _getterColor = '#00D9FF'; // Neon Cyan
  static const _getterDark = '#00AACC'; // Dark Neon Cyan
  static const _setterColor = '#FF00FF'; // Neon Magenta
  static const _setterDark = '#CC00CC'; // Dark Neon Magenta
  static const _helperColor = '#FFB300'; // Bright Amber
  static const _helperDark = '#FF9100'; // Dark Amber

  // App Inventor badge backgrounds (lighter than block)
  static const _eventBadgeFill = '#FFFACD'; // Light Golden
  static const _methodBadgeFill = '#E0FFE0'; // Light Green
  static const _getterSetterBadgeFill = '#E0FFFF'; // Light Cyan

  // Font metrics (approximate for sans-serif 11-12px)
  static double _measureText(String text) => text.length * 6.5;

  /// Generate all block PNGs and SVGs for a component.
  static Future<void> generateBlocks({
    required Map<String, dynamic> componentData,
    required String outputDir,
  }) async {
    final String componentName = componentData['name'] ?? 'Unknown';
    final blocksDir = Directory(p.join(outputDir, componentName));
    if (!blocksDir.existsSync()) {
      blocksDir.createSync(recursive: true);
    }

    final safeName = componentName.replaceAll(RegExp('[^a-zA-Z0-9]'), '_');

    // Events
    final events = componentData['events'] as List? ?? [];
    for (final e in events) {
      final eventData = e as Map<String, dynamic>;
      final name = eventData['name'] as String? ?? 'Event';
      final params =
          (eventData['params'] as List?)?.cast<Map<String, dynamic>>() ?? [];
      final svg = _createEventBlock(name, componentName, params);
      await _writeSvgAndPng(blocksDir.path, 'event_${safeName}_$name', svg);
    }

    // Methods
    final methods = componentData['methods'] as List? ?? [];
    for (final m in methods) {
      final methodData = m as Map<String, dynamic>;
      final name = methodData['name'] as String? ?? 'Method';
      final params =
          (methodData['params'] as List?)?.cast<Map<String, dynamic>>() ?? [];
      final svg = _createMethodBlock(name, componentName, params);
      await _writeSvgAndPng(blocksDir.path, 'method_${safeName}_$name', svg);
    }

    // Properties
    final rawProperties = componentData['blockProperties'] as List? ??
        componentData['properties'] as List? ??
        [];
    for (final prop in rawProperties) {
      final propData = prop as Map<String, dynamic>;
      final name = propData['name'] as String? ?? 'Property';
      final rw = propData['rw'] as String? ?? 'read-write';

      // Getter
      if (rw == 'read' || rw == 'read-write' || rw == 'read-only') {
        final svg = _createPropertyGetterBlock(name, componentName);
        await _writeSvgAndPng(
            blocksDir.path, 'property_get_${safeName}_$name', svg);
      }

      // Setter (with optional helper)
      if (rw == 'write' || rw == 'read-write' || rw == 'write-only') {
        final helper = propData['helper'] as Map<String, dynamic>?;
        final svg =
            _createPropertySetterBlock(name, componentName, helper: helper);
        await _writeSvgAndPng(
            blocksDir.path, 'property_set_${safeName}_$name', svg);
      }
    }
  }

  // ======================== EVENT BLOCK ========================
  // Gold hat-shaped block, inline salmon variables, internal 'do' cutout
  static String _createEventBlock(String eventName, String componentName,
      List<Map<String, dynamic>> params) {
    final instanceName = '${componentName}1';

    final double titleWidth =
        _measureText('when  $instanceName  .$eventName') + 60;
    final double paramWidth = params.isNotEmpty
        ? params
            .map((p) => _measureText(p['name'] as String? ?? '') + 100)
            .reduce(max)
        : 0;
    final double w = max(220.0, max(titleWidth, paramWidth));
    final double baseHeight = 45;
    final double paramHeight = params.length * 28.0;
    final double h = baseHeight + paramHeight + 15;

    final sb = StringBuffer();
    sb.writeln(_svgHeader(w + 10, h + 15));

    sb.writeln(
        '<path d="${_pathEvent(5, 5, w, h)}" fill="$_eventColor" stroke="$_eventDark" stroke-width="1"/>');

    sb.writeln(_text('when', 14, 25, bold: true));
    final double badgeX = 55;
    sb.writeln(
        _componentBadge(instanceName, badgeX, 12, fill: _eventBadgeFill));
    final double badgeWidth = _measureText(instanceName) + 20;
    final double eventX = badgeX + badgeWidth + 4;
    sb.writeln(_text('.$eventName', eventX, 25, bold: true));

    final double doY = 45 + paramHeight + 4;
    sb.writeln(_text('do', 14, doY, color: 'rgba(255,255,255,0.7)', size: 11));

    double y = 60;
    for (final p in params) {
      final pName = p['name'] as String? ?? '';
      sb.writeln(_text(pName, 24, y, size: 11));
      sb.writeln(_paramSocket(w - 60, y - 12, 50, 18));
      y += 28;
    }

    sb.writeln('</svg>');
    return sb.toString();
  }

  // ======================== METHOD BLOCK ========================
  // Purple block, right edge stacked puzzle sockets
  static String _createMethodBlock(String methodName, String componentName,
      List<Map<String, dynamic>> params) {
    final instanceName = '${componentName}1';

    final double titleWidth =
        _measureText('call  $instanceName  .$methodName') + 60;

    double maxParamWidth = 0;
    for (final p in params) {
      double pw = _measureText(p['name'] as String? ?? '') + 100;
      if (p.containsKey('helper') && p['helper'] != null) {
        final helperData = _getHelperData(p['helper'] as Map<String, dynamic>);
        pw = _measureText(p['name'] as String? ?? '') +
            _measureText(helperData['tag']!) +
            _measureText(helperData['value']!) +
            75;
      }
      maxParamWidth = max(maxParamWidth, pw);
    }

    final double w = max(200.0, max(titleWidth, maxParamWidth));
    final double baseHeight = 35;
    final double paramHeight = params.length * 28.0;
    final double h = baseHeight + paramHeight + (params.isNotEmpty ? 10 : 0);

    final sb = StringBuffer();
    sb.writeln(_svgHeader(w + 10, h + 20));

    sb.writeln(
        '<path d="${_pathMethod(5, 10, w, h)}" fill="$_methodColor" stroke="$_methodDark" stroke-width="1"/>');

    sb.writeln(_text('call', 14, 28, bold: true));
    final double badgeX = 50;
    sb.writeln(
        _componentBadge(instanceName, badgeX, 14, fill: _methodBadgeFill));
    final double badgeWidth = _measureText(instanceName) + 20;
    final double methodX = badgeX + badgeWidth + 4;
    sb.writeln(_text('.$methodName', methodX, 28, bold: true));

    double y = 55;
    for (final p in params) {
      final pName = p['name'] as String? ?? '';
      sb.writeln(_text(pName, 24, y, size: 11));

      if (p.containsKey('helper') && p['helper'] != null) {
        final helperData = _getHelperData(p['helper'] as Map<String, dynamic>);
        final double helperX = _measureText(pName) + 35;
        sb.writeln(_helperBadge(
            helperData['tag']!, helperData['value']!, helperX, y - 12));
      } else {
        sb.writeln(_paramSocket(w - 60, y - 12, 50, 18));
      }
      y += 28;
    }

    sb.writeln('</svg>');
    return sb.toString();
  }

  // ======================== PROPERTY GETTER ========================
  // Green block, left value tab
  static String _createPropertyGetterBlock(
      String propName, String componentName) {
    final instanceName = '${componentName}1';
    final double labelWidth = _measureText('$instanceName  .  $propName') + 50;
    final double w = max(150.0, labelWidth);
    final double h = 32;

    final sb = StringBuffer();
    sb.writeln(_svgHeader(w + 10, h + 15));

    sb.writeln(
        '<path d="${_pathGetter(5, 5, w, h)}" fill="$_getterColor" stroke="$_getterDark" stroke-width="1"/>');

    final double badgeX = 30;
    const badgeFill = _getterSetterBadgeFill;
    sb.writeln(_componentBadge(instanceName, badgeX, 8, fill: badgeFill));
    final double badgeWidth = _measureText(instanceName) + 20;
    final double propX = badgeX + badgeWidth + 2;
    sb.writeln(_propertyDropdown(propName, propX, 8, fill: badgeFill));

    sb.writeln('</svg>');
    return sb.toString();
  }

  static String _createPropertySetterBlock(
      String propName, String componentName,
      {Map<String, dynamic>? helper}) {
    final instanceName = '${componentName}1';

    final double labelWidth =
        _measureText('set  $instanceName  .  $propName  to') + 80;
    double helperWidth = 0;
    Map<String, String>? helperData;
    if (helper != null) {
      helperData = _getHelperData(helper);
      helperWidth = _measureText(helperData['tag']!) +
          _measureText(helperData['value']!) +
          60;
    }

    final double w =
        max(220.0, labelWidth + (helper != null ? helperWidth - 30 : 0));
    final double h = 35;

    final sb = StringBuffer();
    sb.writeln(_svgHeader(w + 10, h + 20));

    sb.writeln(
        '<path d="${_pathSetter(5, 10, w, h)}" fill="$_setterColor" stroke="$_setterDark" stroke-width="1"/>');

    sb.writeln(_text('set', 20, 28, bold: true));
    final double badgeX = 48;
    const setterBadgeFill = _getterSetterBadgeFill;
    sb.writeln(
        _componentBadge(instanceName, badgeX, 12, fill: setterBadgeFill));
    final double badgeWidth = _measureText(instanceName) + 20;
    final double propX = badgeX + badgeWidth + 2;
    sb.writeln(_propertyDropdown(propName, propX, 12, fill: setterBadgeFill));

    final double propDropdownWidth = _measureText(propName) + 20;
    final double toX = propX + propDropdownWidth + 8;
    sb.writeln(_text('to', toX, 28, color: 'rgba(255,255,255,0.9)', size: 11));

    if (helperData != null) {
      final double helperX = toX + 25;
      sb.writeln(
          _helperBadge(helperData['tag']!, helperData['value']!, helperX, 10));
    } else {
      sb.writeln(_paramSocket(w - 50, 13, 45, 18));
    }

    sb.writeln('</svg>');
    return sb.toString();
  }

  // ======================== PATH GENERATORS (App Inventor style) ========================
  // Event: hat top, large statement slot at bottom for "do" blocks
  static String _pathEvent(double dx, double dy, double w, double h) {
    const hatRadius = 20.0;
    const slotDepth = 14.0;
    const slotInset = 24.0;
    return 'M $dx,${dy + hatRadius} Q $dx,$dy ${dx + hatRadius},$dy '
        'L ${dx + w - hatRadius},$dy Q ${dx + w},$dy ${dx + w},${dy + hatRadius} '
        'L ${dx + w},${dy + h - slotDepth} '
        'L ${dx + w - slotInset},${dy + h - slotDepth} L ${dx + w - slotInset},${dy + h} '
        'L ${dx + slotInset},${dy + h} L ${dx + slotInset},${dy + h - slotDepth} '
        'L $dx,${dy + h - slotDepth} Z';
  }

  // Method: top-center tab (connects above), bottom-center socket (connects below), rounded feel
  static String _pathMethod(double dx, double dy, double w, double h) {
    const tw = 15.0;
    const th = 8.0;
    final mid = dx + w / 2;
    return 'M $dx,$dy L ${mid - tw / 2 - 2},$dy '
        'l 4,-$th l $tw,0 l 4,$th '
        'L ${dx + w},$dy L ${dx + w},${dy + h} '
        'L ${mid + tw / 2 + 2},${dy + h} l -4,$th l -$tw,0 l -4,-$th '
        'L $dx,${dy + h} Z';
  }

  // Setter: left tab (for "set"), top socket, bottom socket, flat right
  static String _pathSetter(double dx, double dy, double w, double h) {
    const tabW = 15.0;
    const tabH = 8.0;
    const notchW = 15.0;
    const notchH = 8.0;
    final mid = dx + w / 2;
    return 'M ${dx + tabW},$dy L ${mid - notchW / 2 - 2},$dy '
        'l 4,$notchH l $notchW,0 l 4,-$notchH '
        'L ${dx + w},$dy L ${dx + w},${dy + h} '
        'L ${mid + notchW / 2 + 2},${dy + h} l -4,-$notchH l -$notchW,0 l -4,$notchH '
        'L ${dx + tabW},${dy + h} L ${dx + tabW},${dy + h / 2 + tabH} '
        'l -$tabW,0 l 0,-$tabH l $tabW,0 '
        'L ${dx + tabW},$dy Z';
  }

  // Getter: pill with left value tab (output plug)
  static String _pathGetter(double dx, double dy, double w, double h) {
    final r = h / 2;
    const tabW = 12.0;
    const tabH = 8.0;
    return 'M ${dx + r + tabW},$dy L ${dx + w - r},$dy '
        'A $r,$r 0 0 1 ${dx + w - r},${dy + h} L ${dx + r + tabW},${dy + h} '
        'L ${dx + r + tabW},${dy + h / 2 + tabH} l -$tabW,0 l 0,-$tabH l $tabW,0 '
        'L ${dx + r + tabW},$dy Z';
  }

  // ======================== SVG PRIMITIVES ========================

  static String _svgHeader(double width, double height) {
    return '<svg xmlns="http://www.w3.org/2000/svg" '
        'xmlns:xlink="http://www.w3.org/1999/xlink" '
        'width="${width.toInt()}" height="${height.toInt()}" '
        'viewBox="0 0 ${width.toInt()} ${height.toInt()}">'
        '<defs>'
        '<filter id="goldenGlow" x="-50%" y="-50%" width="200%" height="200%">'
        '<feGaussianBlur in="SourceGraphic" stdDeviation="1.5"/>'
        '<feComponentTransfer><feFuncA type="linear" slope="0.7"/></feComponentTransfer>'
        '</filter>'
        '<filter id="neonGlow" x="-50%" y="-50%" width="200%" height="200%">'
        '<feGaussianBlur in="SourceGraphic" stdDeviation="2"/>'
        '<feComponentTransfer><feFuncA type="linear" slope="0.8"/></feComponentTransfer>'
        '</filter>'
        '</defs>';
  }

  static String _text(String content, double x, double y,
      {bool bold = false, int size = 12, String color = '#FFFFFF'}) {
    final weight = bold ? 'bold' : 'normal';
    final escaped = _escapeXml(content);
    return '<text x="${x.toInt()}" y="${y.toInt()}" fill="$color" font-family="sans-serif" '
        'font-size="$size" font-weight="$weight" alignment-baseline="middle">$escaped</text>';
  }

  static String _componentBadge(String name, double x, double y,
      {String fill = '#FFFFFF'}) {
    final textWidth = _measureText(name).toInt();
    final badgeW = textWidth + 20;
    const badgeH = 18;
    return '<rect x="$x" y="$y" width="$badgeW" height="$badgeH" '
        'rx="3" ry="3" fill="$fill" stroke="rgba(0,0,0,0.15)" stroke-width="1"/>'
        '${_text(name, x + 6, y + 10, color: '#333333', size: 11)}'
        '<polygon points="${x + textWidth + 10},${y + 7} ${x + textWidth + 14},${y + 7} '
        '${x + textWidth + 12},${y + 11}" fill="#666666"/>';
  }

  static String _propertyDropdown(String name, double x, double y,
      {String fill = '#FFFFFF'}) {
    final textWidth = _measureText(name).toInt();
    final badgeW = textWidth + 20;
    const badgeH = 18;
    return '<rect x="$x" y="$y" width="$badgeW" height="$badgeH" '
        'rx="3" ry="3" fill="$fill" stroke="rgba(0,0,0,0.15)" stroke-width="1"/>'
        '${_text(name, x + 6, y + 10, color: '#333333', size: 11)}'
        '<polygon points="${x + textWidth + 10},${y + 7} ${x + textWidth + 14},${y + 7} '
        '${x + textWidth + 12},${y + 11}" fill="#666666"/>';
  }

  static String _paramSocket(double x, double y, double w, double h) {
    return '<rect x="$x" y="$y" width="$w" height="$h" rx="4" '
        'fill="rgba(255,255,255,0.25)" stroke="rgba(255,255,255,0.3)" stroke-width="1"/>';
  }

  static String _helperBadge(String tag, String value, double x, double y) {
    final double tagW = _measureText(tag);
    final double valW = _measureText(value);
    final double w = tagW + valW + 55;
    final double h = 22;

    final sb = StringBuffer();
    sb.writeln(
        '<rect x="$x" y="$y" width="$w" height="$h" rx="3" ry="3" fill="$_helperColor" stroke="$_helperDark" stroke-width="1"/>');
    sb.writeln(_text(tag, x + 8, y + 12, bold: true, size: 10));

    final double badgeX = x + tagW + 14;
    final double badgeW = valW + 18;
    sb.writeln(
        '<rect x="$badgeX" y="${y + 3}" width="$badgeW" height="16" rx="3" ry="3" fill="#FFFFFF"/>');
    sb.writeln(_text(value, badgeX + 5, y + 11, color: '#333333', size: 9));

    final double triX = badgeX + valW + 7;
    sb.writeln(
        '<polygon points="$triX,${y + 6} ${triX + 4},${y + 6} ${triX + 2},${y + 10}" fill="#666666"/>');
    return sb.toString();
  }

  static Map<String, String> _getHelperData(Map<String, dynamic> helper) {
    final helperType = helper['type'] as String?;
    final helperData = helper['data'] as Map<String, dynamic>?;

    String tag;
    String value;

    if (helperType == 'ASSET') {
      tag = 'Asset';
      value = 'example.png';
    } else if (helperType == 'SCREEN') {
      tag = 'Screen';
      value = 'Screen1';
    } else if (helperType == 'PROVIDER') {
      tag = 'ChatBot';
      value = 'ChatBot1';
    } else if (helperType == 'PROVIDER_MODEL') {
      tag = 'Model';
      value = 'Gemini';
    } else {
      tag = helperData?['tag'] as String? ??
          helperData?['key'] as String? ??
          'Option';
      final options = helperData?['options'] as List?;
      value = helperData?['defaultOpt'] as String? ??
          (options != null && options.isNotEmpty
              ? (options[0] as Map<String, dynamic>)['name'] as String? ?? ''
              : '');
    }

    return {'tag': tag, 'value': value};
  }

  static String _escapeXml(String text) {
    return text
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&apos;');
  }

  // ======================== OUTPUT ========================

  static Future<void> _writeSvgAndPng(
      String dir, String name, String svgContent) async {
    final svgFile = File(p.join(dir, '$name.svg'));
    await svgFile.writeAsString(svgContent);

    try {
      final pngImage = _renderSvgToPng(svgContent);
      final pngFile = File(p.join(dir, '$name.png'));
      await pngFile.writeAsBytes(img.encodePng(pngImage));
    } catch (_) {}
  }

  static img.Image _renderSvgToPng(String svgContent) {
    final widthMatch = RegExp(r'width="([\d.]+)"').firstMatch(svgContent);
    final heightMatch = RegExp(r'height="([\d.]+)"').firstMatch(svgContent);
    final svgWidth = double.tryParse(widthMatch?.group(1) ?? '300') ?? 300.0;
    final svgHeight = double.tryParse(heightMatch?.group(1) ?? '100') ?? 100.0;

    const targetScale = 2;
    const internalScale = 8; // Supersampling factor
    final canvasWidth = (svgWidth * internalScale).toInt();
    final canvasHeight = (svgHeight * internalScale).toInt();

    final image = img.Image(
      width: canvasWidth,
      height: canvasHeight,
      numChannels: 4,
    );
    img.fill(image, color: img.ColorRgba8(0, 0, 0, 0));

    // Paths
    for (final pathMatch
        in RegExp('<path d="([^"]+)" fill="([^"]+)" stroke="([^"]+)"')
            .allMatches(svgContent)) {
      final fill = _parseColor(pathMatch.group(2)!);
      final stroke = _parseColor(pathMatch.group(3)!);
      _drawPathApprox(image, pathMatch.group(1)!, fill, internalScale,
          stroke: stroke);
    }

    // Rects
    for (final rectMatch in RegExp(
            '<rect x="([^"]+)" y="([^"]+)" width="([^"]+)" height="([^"]+)"[^>]*fill="([^"]+)"')
        .allMatches(svgContent)) {
      final x = (double.tryParse(rectMatch.group(1)!) ?? 0.0) * internalScale;
      final y = (double.tryParse(rectMatch.group(2)!) ?? 0.0) * internalScale;
      final w = (double.tryParse(rectMatch.group(3)!) ?? 0.0) * internalScale;
      final h = (double.tryParse(rectMatch.group(4)!) ?? 0.0) * internalScale;
      final fill = _parseColor(rectMatch.group(5)!);

      final rxMatch = RegExp('rx="([^"]+)"').firstMatch(rectMatch.group(0)!);
      final rx =
          ((double.tryParse(rxMatch?.group(1) ?? '0') ?? 0.0) * internalScale)
              .toInt();

      img.fillRect(image,
          x1: x.toInt(),
          y1: y.toInt(),
          x2: (x + w).toInt(),
          y2: (y + h).toInt(),
          color: fill,
          radius: rx);

      final strokeMatch =
          RegExp('stroke="([^"]+)"').firstMatch(rectMatch.group(0)!);
      if (strokeMatch != null) {
        final stroke = _parseColor(strokeMatch.group(1)!);
        img.drawRect(image,
            x1: x.toInt(),
            y1: y.toInt(),
            x2: (x + w).toInt(),
            y2: (y + h).toInt(),
            color: stroke,
            thickness: internalScale ~/ 4);
      }
    }

    // Text
    for (final textMatch in RegExp(
            r'<text x="([\d.]+)" y="([\d.]+)" fill="([^"]+)"[^>]*>([^<]+)</text>')
        .allMatches(svgContent)) {
      final x = (double.tryParse(textMatch.group(1)!) ?? 0.0) * internalScale;
      final y = (double.tryParse(textMatch.group(2)!) ?? 0.0) * internalScale;
      final color = _parseColor(textMatch.group(3)!);
      final content = _unescapeXml(textMatch.group(4)!);

      // Alignment adjustment
      img.drawString(image, content,
          font: img.arial24, // Use larger font for supersampling
          x: x.toInt(),
          y: (y - 12 * internalScale).toInt(), // Visual adjustment for baseline
          color: color);
    }

    // Polygons
    for (final polyMatch in RegExp('<polygon points="([^"]+)" fill="([^"]+)"')
        .allMatches(svgContent)) {
      final fill = _parseColor(polyMatch.group(2)!);
      final pointsStr = polyMatch.group(1)!;
      final points = pointsStr.split(RegExp(r'\s+')).map((p) {
        final parts = p.split(',');
        return [
          (double.tryParse(parts[0]) ?? 0.0) * internalScale,
          (double.tryParse(parts[1]) ?? 0.0) * internalScale
        ];
      }).toList();
      if (points.length >= 3) {
        _fillPolygon(image, points, fill);
      }
    }

    // Downsample for anti-aliasing
    final output = img.copyResize(
      image,
      width: (svgWidth * targetScale).toInt(),
      height: (svgHeight * targetScale).toInt(),
      interpolation: img.Interpolation.linear,
    );

    return output;
  }

  static void _drawPathApprox(
      img.Image image, String pathData, img.Color fill, int scale,
      {img.Color? stroke}) {
    final points = <List<double>>[];
    final commands =
        RegExp('[MLQAZlqaz][^MLQAZlqaz]*').allMatches(pathData.trim());

    double cx = 0, cy = 0;
    double startX = 0, startY = 0;

    for (final cmd in commands) {
      final s = cmd.group(0)!.trim();
      if (s.isEmpty) continue;
      final op = s[0];
      final nums = RegExp(r'-?[\d.]+')
          .allMatches(s.substring(1))
          .map((m) => double.tryParse(m.group(0)!) ?? 0.0)
          .toList();

      switch (op) {
        case 'M':
          if (nums.length >= 2) {
            cx = nums[0];
            cy = nums[1];
            startX = cx;
            startY = cy;
            points.add([cx * scale, cy * scale]);
          }
          break;
        case 'L':
          if (nums.length >= 2) {
            cx = nums[0];
            cy = nums[1];
            points.add([cx * scale, cy * scale]);
          }
          break;
        case 'l':
          for (int i = 0; i + 1 < nums.length; i += 2) {
            cx += nums[i];
            cy += nums[i + 1];
            points.add([cx * scale, cy * scale]);
          }
          break;
        case 'Q':
          if (nums.length >= 4) {
            final x1 = nums[0] * scale;
            final y1 = nums[1] * scale;
            final x2 = nums[2] * scale;
            final y2 = nums[3] * scale;
            final sX = cx * scale;
            final sY = cy * scale;

            for (double t = 0.02; t <= 1.0; t += 0.02) {
              final x =
                  (1 - t) * (1 - t) * sX + 2 * (1 - t) * t * x1 + t * t * x2;
              final y =
                  (1 - t) * (1 - t) * sY + 2 * (1 - t) * t * y1 + t * t * y2;
              points.add([x, y]);
            }
            cx = nums[2];
            cy = nums[3];
          }
          break;
        case 'A':
          // Simplified Arc handling (just use end point)
          if (nums.length >= 7) {
            cx = nums[5];
            cy = nums[6];
            points.add([cx * scale, cy * scale]);
          }
          break;
        case 'Z':
        case 'z':
          points.add([startX * scale, startY * scale]);
          break;
      }
    }

    if (points.length >= 3) {
      _fillPolygon(image, points, fill);
      if (stroke != null) {
        for (int i = 0; i < points.length - 1; i++) {
          img.drawLine(image,
              x1: points[i][0].toInt(),
              y1: points[i][1].toInt(),
              x2: points[i + 1][0].toInt(),
              y2: points[i + 1][1].toInt(),
              color: stroke);
        }
      }
    }
  }

  static void _fillPolygon(
      img.Image image, List<List<double>> points, img.Color fill) {
    if (points.isEmpty) return;

    double minY = points[0][1], maxY = points[0][1];
    for (final p in points) {
      if (p[1] < minY) minY = p[1];
      if (p[1] > maxY) maxY = p[1];
    }

    for (int y = minY.toInt(); y <= maxY.toInt(); y++) {
      final intersections = <double>[];
      for (int i = 0; i < points.length; i++) {
        final j = (i + 1) % points.length;
        final y1 = points[i][1], y2 = points[j][1];
        if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
          final x = points[i][0] +
              (y - y1) / (y2 - y1) * (points[j][0] - points[i][0]);
          intersections.add(x);
        }
      }
      intersections.sort();
      for (int i = 0; i + 1 < intersections.length; i += 2) {
        final x1 = intersections[i].toInt();
        final x2 = intersections[i + 1].toInt();
        for (int x = x1; x <= x2; x++) {
          if (x >= 0 && x < image.width && y >= 0 && y < image.height) {
            image.setPixel(x, y, fill);
          }
        }
      }
    }
  }

  static img.Color _parseColor(String color) {
    if (color.startsWith('#')) {
      final hex = color.substring(1);
      if (hex.length == 6) {
        final v = int.tryParse(hex, radix: 16) ?? 0;
        return img.ColorRgba8((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, 255);
      }
    } else if (color.startsWith('rgba')) {
      final match =
          RegExp(r'rgba\((\d+),(\d+),(\d+),([\d.]+)\)').firstMatch(color);
      if (match != null) {
        final r = int.parse(match.group(1)!);
        final g = int.parse(match.group(2)!);
        final b = int.parse(match.group(3)!);
        final a = (double.parse(match.group(4)!) * 255).toInt();
        return img.ColorRgba8(r, g, b, a);
      }
    }
    return img.ColorRgba8(128, 128, 128, 255);
  }

  static String _unescapeXml(String text) {
    return text
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&apos;', "'");
  }
}
