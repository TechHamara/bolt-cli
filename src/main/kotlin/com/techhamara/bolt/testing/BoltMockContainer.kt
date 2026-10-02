package com.techhamara.bolt.testing

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Universal Mock ComponentContainer for unit testing MIT App Inventor extensions.
 * Intercepts all EventDispatcher calls to verify @SimpleEvent executions in tests.
 */
class BoltMockContainer(val classLoader: ClassLoader = Thread.currentThread().contextClassLoader ?: BoltMockContainer::class.java.classLoader) : InvocationHandler {

    val formInstance: Any = BoltMockHelper.createMockForm(classLoader)
    val activityInstance: Any = BoltMockHelper.createMockActivity(classLoader)
    val childrenList = mutableListOf<Any>()
    val firedEvents = CopyOnWriteArrayList<FiredEvent>()

    val proxy: Any

    data class FiredEvent(
        val component: Any?,
        val componentName: String,
        val eventName: String,
        val args: List<Any?>
    )

    companion object {
        val globalFiredEvents = CopyOnWriteArrayList<FiredEvent>()
        val activeInstances = CopyOnWriteArrayList<BoltMockContainer>()

        @JvmStatic
        fun recordStaticEvent(comp: Any?, compId: String?, eventName: String?, args: Array<Any?>?): Boolean {
            val name = compId?.takeIf { it.isNotEmpty() } ?: (comp?.javaClass?.simpleName ?: "")
            val ev = FiredEvent(comp, name, eventName ?: "", args?.toList() ?: emptyList())
            globalFiredEvents.add(ev)
            activeInstances.forEach { it.firedEvents.add(ev) }
            return true
        }
    }

    init {
        activeInstances.add(this)

        val interfaces = mutableListOf<Class<*>>()
        try {
            interfaces.add(classLoader.loadClass("com.google.appinventor.components.runtime.ComponentContainer"))
        } catch (_: Throwable) {}
        try {
            interfaces.add(classLoader.loadClass("com.google.appinventor.components.runtime.HandlesEventDispatching"))
        } catch (_: Throwable) {}

        proxy = if (interfaces.isNotEmpty()) {
            Proxy.newProxyInstance(classLoader, interfaces.toTypedArray(), this)
        } else {
            this
        }

        try {
            val dispatcherCls = classLoader.loadClass("com.google.appinventor.components.runtime.EventDispatcher")
            val regMethod = dispatcherCls.methods.firstOrNull { it.name == "registerEventForDelegation" }
            regMethod?.invoke(null, formInstance, "", "")
            regMethod?.invoke(null, proxy, "", "")
        } catch (_: Throwable) {}
    }

    var currentProfile: ScreenProfile = ScreenProfile.PHONE_PORTRAIT

    fun setScreenProfile(profile: ScreenProfile) {
        this.currentProfile = profile
        notifyConfigurationChanged()
    }

    fun setScreenSize(sizeClass: ScreenSizeClass) {
        val newProfile = when (sizeClass) {
            ScreenSizeClass.COMPACT -> ScreenProfile.PHONE_PORTRAIT
            ScreenSizeClass.MEDIUM -> ScreenProfile.FOLDABLE_UNFOLDED
            ScreenSizeClass.EXPANDED -> ScreenProfile.TABLET_LANDSCAPE
        }
        setScreenProfile(newProfile)
    }

    fun setScreenDimensions(widthDp: Int, heightDp: Int, dpi: Int = 160) {
        val sizeClass = ScreenSizeClass.fromWidthDp(widthDp)
        val orientation = if (widthDp >= heightDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
        setScreenProfile(ScreenProfile(sizeClass, widthDp, heightDp, dpi, orientation, currentProfile.posture))
    }

    fun rotateOrientation(): ScreenOrientation {
        val flipped = currentProfile.copyFlipped()
        setScreenProfile(flipped)
        return flipped.orientation
    }

    fun setPosture(posture: ScreenPosture) {
        currentProfile = currentProfile.copy(posture = posture)
        notifyConfigurationChanged()
    }

    private fun notifyConfigurationChanged() {
        try {
            val orientMethod = formInstance.javaClass.methods.firstOrNull { it.name.equals("OrientationChanged", ignoreCase = true) }
            orientMethod?.invoke(formInstance)
        } catch (_: Throwable) {}
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val name = method.name
        val safeArgs = args ?: emptyArray()

        return when (name) {
            "\$form" -> formInstance
            "\$context" -> activityInstance
            "\$add" -> {
                if (safeArgs.isNotEmpty() && safeArgs[0] != null) {
                    childrenList.add(safeArgs[0]!!)
                }
                null
            }
            "setChildWidth", "setChildHeight", "setChildNeedsLayout" -> null
            "getChildren" -> childrenList
            "Width" -> currentProfile.widthDp
            "Height" -> currentProfile.heightDp
            "canDispatchEvent" -> true
            "dispatchEvent" -> {
                val comp = safeArgs.getOrNull(0)
                val compName = safeArgs.getOrNull(1)?.toString() ?: (comp?.javaClass?.simpleName ?: "")
                val evName = safeArgs.getOrNull(2)?.toString() ?: ""
                val evArgs = (safeArgs.getOrNull(3) as? Array<*>)?.toList() ?: emptyList<Any?>()
                val ev = FiredEvent(comp, compName, evName, evArgs)
                firedEvents.add(ev)
                globalFiredEvents.add(ev)
                true
            }
            "toString" -> "BoltMockContainer[${currentProfile}]"
            "hashCode" -> 42
            "equals" -> safeArgs.getOrNull(0) === proxy
            else -> null
        }
    }

    fun hasFired(eventName: String): Boolean {
        return firedEvents.any { it.eventName.equals(eventName, ignoreCase = true) } ||
               globalFiredEvents.any { it.eventName.equals(eventName, ignoreCase = true) }
    }

    fun hasFiredEvent(eventName: String): Boolean {
        return hasFired(eventName)
    }

    fun getFiredEvents(eventName: String): List<FiredEvent> {
        val combined = (firedEvents + globalFiredEvents).distinct()
        return combined.filter { it.eventName.equals(eventName, ignoreCase = true) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getContainer(): T = proxy as T

    fun getLastFiredEvent(): FiredEvent? = firedEvents.lastOrNull() ?: globalFiredEvents.lastOrNull()

    fun clearEvents() {
        firedEvents.clear()
        globalFiredEvents.clear()
    }
}
