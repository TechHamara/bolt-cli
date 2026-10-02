package com.techhamara.bolt.testing

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import java.lang.reflect.Field

object BoltMockHelper {

    val unsafe: sun.misc.Unsafe by lazy {
        try {
            val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            f.isAccessible = true
            f.get(null) as sun.misc.Unsafe
        } catch (e: Exception) {
            throw IllegalStateException("Unable to acquire sun.misc.Unsafe for mock allocation", e)
        }
    }

    private fun getOrCreateMockFormClass(classLoader: ClassLoader): Class<*> {
        val className = "com.google.appinventor.components.runtime.BoltMockForm"
        try {
            return classLoader.loadClass(className)
        } catch (_: ClassNotFoundException) {}

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(
            V1_8,
            ACC_PUBLIC or ACC_SUPER,
            "com/google/appinventor/components/runtime/BoltMockForm",
            null,
            "com/google/appinventor/components/runtime/Form",
            null
        )

        // Default constructor
        val mvInit = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mvInit.visitCode()
        mvInit.visitVarInsn(ALOAD, 0)
        mvInit.visitMethodInsn(INVOKESPECIAL, "com/google/appinventor/components/runtime/Form", "<init>", "()V", false)
        mvInit.visitInsn(RETURN)
        mvInit.visitMaxs(1, 1)
        mvInit.visitEnd()

        // canDispatchEvent(Component, String)Z
        val mvCanDispatch = cw.visitMethod(
            ACC_PUBLIC,
            "canDispatchEvent",
            "(Lcom/google/appinventor/components/runtime/Component;Ljava/lang/String;)Z",
            null,
            null
        )
        mvCanDispatch.visitCode()
        mvCanDispatch.visitInsn(ICONST_1)
        mvCanDispatch.visitInsn(IRETURN)
        mvCanDispatch.visitMaxs(1, 3)
        mvCanDispatch.visitEnd()

        // dispatchEvent(Component, String, String, Object[])Z
        val mvDispatch = cw.visitMethod(
            ACC_PUBLIC,
            "dispatchEvent",
            "(Lcom/google/appinventor/components/runtime/Component;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Z",
            null,
            null
        )
        mvDispatch.visitCode()
        mvDispatch.visitVarInsn(ALOAD, 1)
        mvDispatch.visitVarInsn(ALOAD, 2)
        mvDispatch.visitVarInsn(ALOAD, 3)
        mvDispatch.visitVarInsn(ALOAD, 4)
        mvDispatch.visitMethodInsn(
            INVOKESTATIC,
            "com/techhamara/bolt/testing/BoltMockContainer",
            "recordStaticEvent",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Z",
            false
        )
        mvDispatch.visitInsn(IRETURN)
        mvDispatch.visitMaxs(4, 5)
        mvDispatch.visitEnd()

        // dispatchGenericEvent(Component, String, boolean, Object[])V
        val mvDispatchGeneric = cw.visitMethod(
            ACC_PUBLIC,
            "dispatchGenericEvent",
            "(Lcom/google/appinventor/components/runtime/Component;Ljava/lang/String;Z[Ljava/lang/Object;)V",
            null,
            null
        )
        mvDispatchGeneric.visitCode()
        mvDispatchGeneric.visitVarInsn(ALOAD, 1)
        mvDispatchGeneric.visitLdcInsn("")
        mvDispatchGeneric.visitVarInsn(ALOAD, 2)
        mvDispatchGeneric.visitVarInsn(ALOAD, 4)
        mvDispatchGeneric.visitMethodInsn(
            INVOKESTATIC,
            "com/techhamara/bolt/testing/BoltMockContainer",
            "recordStaticEvent",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Z",
            false
        )
        mvDispatchGeneric.visitInsn(POP)
        mvDispatchGeneric.visitInsn(RETURN)
        mvDispatchGeneric.visitMaxs(4, 5)
        mvDispatchGeneric.visitEnd()

        cw.visitEnd()
        val bytes = cw.toByteArray()

        return try {
            val byteLoader = DirectByteClassLoader(classLoader)
            byteLoader.define(className, bytes)
        } catch (_: Throwable) {
            classLoader.loadClass("com.google.appinventor.components.runtime.Form")
        }
    }

    private class DirectByteClassLoader(parent: ClassLoader) : ClassLoader(parent) {
        fun define(name: String, bytes: ByteArray): Class<*> {
            return defineClass(name, bytes, 0, bytes.size)
        }
    }

    fun createMockForm(classLoader: ClassLoader): Any {
        return try {
            val formClass = try {
                getOrCreateMockFormClass(classLoader)
            } catch (_: Throwable) {
                classLoader.loadClass("com.google.appinventor.components.runtime.Form")
            }
            val form = unsafe.allocateInstance(formClass)
            initializeFormFields(form)
            form
        } catch (e: Exception) {
            throw RuntimeException("Failed to allocate mock Form", e)
        }
    }

    fun createMockActivity(classLoader: ClassLoader): Any {
        return try {
            val activityClass = classLoader.loadClass("android.app.Activity")
            unsafe.allocateInstance(activityClass)
        } catch (e: Exception) {
            throw RuntimeException("Failed to allocate mock Activity", e)
        }
    }

    private fun initializeFormFields(form: Any) {
        try {
            setField(form, "screenInitialized", true)
            setField(form, "allChildren", ArrayList<Any>())
        } catch (_: Exception) {}
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val f: Field = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }
}


