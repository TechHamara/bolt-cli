package com.techhamara.bolt.compiler

import org.objectweb.asm.*
import java.io.File

/**
 * Strips App Inventor metadata annotations (com.google.appinventor.components.annotations.*)
 * from compiled Java/Kotlin bytecode for maximum binary size reduction.
 */
object Deannotator {

    private const val ANNOTATION_PREFIX = "Lcom/google/appinventor/components/annotations/"

    /**
     * Strips App Inventor annotations from all .class files in [classesDir].
     * Returns the number of modified class files.
     */
    fun deannotateDirectory(classesDir: File, logger: ((String) -> Unit)? = null): Int {
        if (!classesDir.exists() || !classesDir.isDirectory) return 0

        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension.lowercase() == "class" }.toList()
        var strippedCount = 0

        for (file in classFiles) {
            try {
                val originalBytes = file.readBytes()
                val reader = ClassReader(originalBytes)
                val writer = ClassWriter(0)
                var modified = false

                val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
                    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
                        if (descriptor?.startsWith(ANNOTATION_PREFIX) == true) {
                            modified = true
                            return null // Discard annotation
                        }
                        return super.visitAnnotation(descriptor, visible)
                    }

                    override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, descriptor: String?, visible: Boolean): AnnotationVisitor? {
                        if (descriptor?.startsWith(ANNOTATION_PREFIX) == true) {
                            modified = true
                            return null // Discard type annotation
                        }
                        return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
                    }

                    override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor {
                        val fv = super.visitField(access, name, descriptor, signature, value)
                        return object : FieldVisitor(Opcodes.ASM9, fv) {
                            override fun visitAnnotation(desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc?.startsWith(ANNOTATION_PREFIX) == true) {
                                    modified = true
                                    return null
                                }
                                return super.visitAnnotation(desc, vis)
                            }

                            override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc?.startsWith(ANNOTATION_PREFIX) == true) {
                                    modified = true
                                    return null
                                }
                                return super.visitTypeAnnotation(typeRef, typePath, desc, vis)
                            }
                        }
                    }

                    override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                        val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                            override fun visitAnnotation(desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc?.startsWith(ANNOTATION_PREFIX) == true) {
                                    modified = true
                                    return null
                                }
                                return super.visitAnnotation(desc, vis)
                            }

                            override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc?.startsWith(ANNOTATION_PREFIX) == true) {
                                    modified = true
                                    return null
                                }
                                return super.visitTypeAnnotation(typeRef, typePath, desc, vis)
                            }

                            override fun visitParameterAnnotation(parameter: Int, desc: String?, vis: Boolean): AnnotationVisitor? {
                                if (desc?.startsWith(ANNOTATION_PREFIX) == true) {
                                    modified = true
                                    return null
                                }
                                return super.visitParameterAnnotation(parameter, desc, vis)
                            }
                        }
                    }
                }

                reader.accept(visitor, 0)

                if (modified) {
                    file.writeBytes(writer.toByteArray())
                    strippedCount++
                }
            } catch (e: Exception) {
                logger?.invoke("warning Failed to deannotate ${file.name}: ${e.message}")
            }
        }

        if (strippedCount > 0) {
            logger?.invoke("debug Stripped App Inventor metadata annotations (deannonate) from $strippedCount class file(s).")
        }
        return strippedCount
    }
}
