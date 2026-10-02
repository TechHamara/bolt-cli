package com.techhamara.bolt.cli.commands

import com.techhamara.bolt.cli.Logger
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

object AuthCommand {

    fun execute(args: List<String>, logger: Logger): Int {
        if (args.isEmpty()) {
            logger.err("Usage: bolt auth <init|generate> [options]")
            return 1
        }

        return when (val sub = args[0].lowercase()) {
            "init" -> handleInit(logger)
            "generate" -> handleGenerate(args.drop(1), logger)
            else -> {
                logger.err("Unknown auth subcommand: $sub. Available: init, generate")
                1
            }
        }
    }

    private fun handleInit(logger: Logger): Int {
        val projectDir = File(".").canonicalFile
        val ymlFile = File(projectDir, "bolt.yml")

        if (!ymlFile.exists()) {
            logger.err("Not a Bolt project (bolt.yml not found).")
            return 1
        }

        logger.info("Generating RSA-2048 Keypair natively...")

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val pubKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)

        val boltDir = File(projectDir, ".bolt").apply { mkdirs() }
        val privFile = File(boltDir, "private_key.pem")
        privFile.writeText(privKeyBase64, Charsets.UTF_8)
        logger.info("Saved Private Key to .bolt/private_key.pem (DO NOT SHARE THIS!).")

        // Find package name and source dir
        val srcDir = File(projectDir, "src")
        var targetDir: File? = null
        var packageName: String? = null

        val javaFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        for (f in javaFiles) {
            val content = f.readText(Charsets.UTF_8)
            val match = Regex("""package\s+([^;]+);""").find(content)
            if (match != null) {
                packageName = match.groupValues[1].trim()
                targetDir = f.parentFile
                break
            }
        }

        if (packageName == null || targetDir == null) {
            logger.err("Could not determine package name. Ensure at least one .java file exists with a package statement.")
            return 1
        }

        val verifierFile = File(targetDir, "LicenseVerifier.java")
        verifierFile.writeText(getVerifierTemplate(packageName, pubKeyBase64), Charsets.UTF_8)

        logger.info("Scaffolded LicenseVerifier.java in package $packageName!")
        logger.info("You can now call `LicenseVerifier.verify(context, providedKey)` in your extension.")
        return 0
    }

    private fun handleGenerate(args: List<String>, logger: Logger): Int {
        var email: String? = null
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-e", "--email" -> {
                    if (i + 1 < args.size) { email = args[++i] }
                }
                else -> {
                    if (!args[i].startsWith("-") && email == null) {
                        email = args[i]
                    }
                }
            }
            i++
        }

        if (email.isNullOrBlank()) {
            logger.err("Email is required. Use: bolt auth generate -e <customer@gmail.com>")
            return 1
        }

        val prefix = email.split("@").first().replace(Regex("[^a-zA-Z0-9]"), "")
        val projectDir = File(".").canonicalFile
        val privFile = File(projectDir, ".bolt/private_key.pem")

        if (!privFile.exists()) {
            logger.err("Private key not found. Run `bolt auth init` first.")
            return 1
        }

        try {
            val privKeyBase64 = privFile.readText(Charsets.UTF_8).trim()
            val kf = KeyFactory.getInstance("RSA")
            val privateKey: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privKeyBase64)))

            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(prefix.toByteArray(Charsets.UTF_8))
            val signature = Base64.getEncoder().encodeToString(signer.sign())

            logger.info("=============================================")
            logger.info("SUCCESS! License Key generated for \"$prefix\"")
            logger.info("Send this key to your customer:")
            logger.info(signature)
            logger.info("=============================================")
            return 0
        } catch (e: Exception) {
            logger.err("Failed to sign license key: ${e.message}")
            return 1
        }
    }

    private fun getVerifierTemplate(packageName: String, pubKeyBase64: String): String {
        return """
        package $packageName;

        import android.content.Context;
        import java.security.KeyFactory;
        import java.security.PublicKey;
        import java.security.Signature;
        import java.security.spec.X509EncodedKeySpec;

        /**
         * Automated Extension Licensing System (Offline RSA-2048)
         * Generated by Bolt CLI (bolt auth init)
         */
        public class LicenseVerifier {

            private static final String PUBLIC_KEY = "$pubKeyBase64";

            /**
             * Verify license key against a customer email offline using RSA-2048 with SHA256.
             *
             * @param email Customer email (e.g. demo@gmail.com)
             * @param licenseKey Base64-encoded signature generated via `bolt auth generate -e <email>`
             * @return true if valid signature matches customer email; false otherwise
             */
            public static boolean verify(String email, String licenseKey) {
                if (email == null || licenseKey == null || email.trim().isEmpty() || licenseKey.trim().isEmpty()) {
                    return false;
                }
                try {
                    String prefix = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
                    byte[] pubKeyBytes = decodeBase64(PUBLIC_KEY);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(pubKeyBytes));

                    Signature verifier = Signature.getInstance("SHA256withRSA");
                    verifier.initVerify(pubKey);
                    verifier.update(prefix.getBytes("UTF-8"));

                    byte[] sigBytes = decodeBase64(licenseKey.trim());
                    return verifier.verify(sigBytes);
                } catch (Exception e) {
                    return false;
                }
            }

            /**
             * Context-aware verification with customer email.
             */
            public static boolean verify(Context context, String email, String licenseKey) {
                return verify(email, licenseKey);
            }

            /**
             * Verification with default configured email (demo@gmail.com).
             */
            public static boolean verify(Context context, String licenseKey) {
                return verify("demo@gmail.com", licenseKey);
            }

            private static byte[] decodeBase64(String str) {
                try {
                    return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
                } catch (Throwable t) {
                    return java.util.Base64.getDecoder().decode(str);
                }
            }
        }
        """.trimIndent()
    }
}
