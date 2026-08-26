import 'dart:io';
import 'package:args/command_runner.dart';
import 'package:get_it/get_it.dart';
import 'package:path/path.dart' as p;
import 'package:bolt/src/config/config.dart';
import 'package:bolt/src/services/file_service.dart';
import 'package:bolt/src/services/logger.dart';
import 'package:bolt/src/utils/file_extension.dart';

class AuthCommand extends Command<int> {
  @override
  String get description => 'Automated Extension Licensing System (Offline RSA)';

  @override
  String get name => 'auth';

  AuthCommand() {
    addSubcommand(AuthInitCommand());
    addSubcommand(AuthGenerateCommand());
  }
}

class AuthInitCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  @override
  String get description => 'Initializes the RSA licensing system for your extension.';

  @override
  String get name => 'init';

  @override
  Future<int> run() async {
    final config = await Config.load(_fs.configFile, _lgr);
    if (config == null) return 1;

    _lgr.info('Generating RSA-2048 Keypair (This may take a moment)...');

    final javaRunner = _getJavaCode();
    final tempDir = Directory.systemTemp.createTempSync('bolt_auth_');
    final javaFile = File(p.join(tempDir.path, 'KeyGen.java'));
    javaFile.writeAsStringSync(javaRunner);

    // Compile Java
    final compileRes = await Process.run('javac', [javaFile.path]);
    if (compileRes.exitCode != 0) {
      _lgr.err('Failed to compile KeyGen.java:\\n\${compileRes.stderr}');
      return 1;
    }

    // Run Java KeyGen
    final runRes = await Process.run('java', ['-cp', tempDir.path, 'KeyGen']);
    if (runRes.exitCode != 0) {
      _lgr.err('Failed to run KeyGen:\\n\${runRes.stderr}');
      return 1;
    }

    final output = runRes.stdout.toString().trim();
    final parts = output.split('|');
    if (parts.length != 2) {
      _lgr.err('Invalid output from KeyGen: \$output');
      return 1;
    }

    final pubKey = parts[0];
    final privKey = parts[1];

    // Save Private Key securely
    final boltDir = p.join(_fs.srcDir.parent.path, '.bolt').asDir();
    if (!boltDir.existsSync()) boltDir.createSync(recursive: true);
    
    final privFile = File(p.join(boltDir.path, 'private_key.pem'));
    privFile.writeAsStringSync(privKey);
    _lgr.info('Saved Private Key to .bolt/private_key.pem (DO NOT SHARE THIS!).');

    // Scaffold LicenseVerifier.java
    String? packageName;
    Directory? targetDir;

    final entities = _fs.srcDir.listSync(recursive: true);
    for (final entity in entities) {
      if (entity is File && entity.path.endsWith('.java')) {
        final content = entity.readAsStringSync();
        final match = RegExp(r'package\s+([^;]+);').firstMatch(content);
        if (match != null) {
          packageName = match.group(1)?.trim();
          targetDir = entity.parent;
          break;
        }
      }
    }

    if (packageName == null || targetDir == null) {
      _lgr.err('Could not determine package name. Make sure you have at least one .java file in src/ with a package declaration.');
      return 1;
    }

    final verifierFile = File(p.join(targetDir.path, 'LicenseVerifier.java'));
    verifierFile.writeAsStringSync(_getVerifierTemplate(packageName, pubKey));

    _lgr.info('Scaffolded LicenseVerifier.java!');
    _lgr.info('You can now use `LicenseVerifier.verify(context, providedKey)` in your extension.');

    return 0;
  }
}

class AuthGenerateCommand extends Command<int> {
  final _fs = GetIt.I<FileService>();
  final _lgr = GetIt.I<Logger>();

  @override
  String get description => 'Generates a license key for a customer email prefix.';

  @override
  String get name => 'generate';

  AuthGenerateCommand() {
    argParser.addOption('email', abbr: 'e', help: 'Customer App Inventor Email (e.g. kapil@gmail.com)', mandatory: true);
  }

  @override
  Future<int> run() async {
    final email = argResults?['email'] as String?;
    if (email == null) {
      _lgr.err('Email is required.');
      return 1;
    }

    // Clean email prefix
    final prefix = email.split('@').first.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '');
    
    final boltDir = p.join(_fs.srcDir.parent.path, '.bolt').asDir();
    final privFile = File(p.join(boltDir.path, 'private_key.pem'));
    if (!privFile.existsSync()) {
      _lgr.err('Private key not found. Run `bolt auth init` first.');
      return 1;
    }

    final privKey = privFile.readAsStringSync().trim();

    final javaRunner = _getJavaCode();
    final tempDir = Directory.systemTemp.createTempSync('bolt_auth_');
    final javaFile = File(p.join(tempDir.path, 'KeyGen.java'));
    javaFile.writeAsStringSync(javaRunner);

    await Process.run('javac', [javaFile.path]);
    final runRes = await Process.run('java', ['-cp', tempDir.path, 'KeyGen', 'sign', prefix, privKey]);
    
    if (runRes.exitCode != 0) {
      _lgr.err('Failed to sign key:\\n\${runRes.stderr}');
      return 1;
    }

    final signature = runRes.stdout.toString().trim();
    _lgr.info('=============================================');
    _lgr.info('SUCCESS! License Key generated for "$prefix"');
    _lgr.info('Send this key to your customer:');
    _lgr.info(signature);
    _lgr.info('=============================================');

    return 0;
  }
}

String _getJavaCode() {
  return '''
import java.security.*;
import java.util.Base64;

public class KeyGen {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("sign")) {
            String prefix = args[1];
            String privKeyBase64 = args[2];
            
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(privKeyBase64)));
            
            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(privateKey);
            privateSignature.update(prefix.getBytes("UTF-8"));
            
            byte[] signature = privateSignature.sign();
            System.out.println(Base64.getEncoder().encodeToString(signature));
            return;
        }

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        System.out.println(pub + "|" + priv);
    }
}
''';
}

String _getVerifierTemplate(String packageName, String pubKey) {
  return '''
package \$packageName;

import android.content.Context;
import android.util.Log;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import android.util.Base64;

public class LicenseVerifier {
    private static final String PUBLIC_KEY = "\$pubKey";

    public static boolean verify(Context context, String licenseKey) {
        try {
            // MIT App Inventor package names always start with appinventor.ai_<username>.ProjectName
            String packageName = context.getPackageName();
            if (!packageName.startsWith("appinventor.ai_")) {
                Log.e("LicenseVerifier", "Not running inside App Inventor build environment!");
                return false; 
            }
            
            String[] parts = packageName.split("\\\\.");
            if (parts.length < 3) return false;
            
            String aiUserPrefix = parts[1]; // ai_username
            String username = aiUserPrefix.replace("ai_", "");
            
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)));
            
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(key);
            publicSignature.update(username.getBytes("UTF-8"));
            
            byte[] signatureBytes = Base64.decode(licenseKey, Base64.DEFAULT);
            return publicSignature.verify(signatureBytes);
            
        } catch (Exception e) {
            Log.e("LicenseVerifier", "Failed to verify license", e);
            return false;
        }
    }
}
''';
}
