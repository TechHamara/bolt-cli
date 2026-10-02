package com.techhamara.bolt.testing

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.Retention
import kotlin.annotation.Target

/**
 * Declares that a test method or test class should be executed against specific Screen Size Classes.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class TestOnScreens(
    val value: Array<ScreenSizeClass> = [ScreenSizeClass.COMPACT, ScreenSizeClass.MEDIUM, ScreenSizeClass.EXPANDED]
)

/**
 * Filter for tests targeting Compact screens (standard smartphones, width < 600dp).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class TestCompactWidth

/**
 * Filter for tests targeting Medium screens (foldables, small tablets, 600dp <= width < 840dp).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class TestMediumWidth

/**
 * Filter for tests targeting Expanded screens (tablets, desktops, width >= 840dp).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class TestExpandedWidth

/**
 * Declares required foldable posture (e.g. TABLETOP, BOOK).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresPosture(
    val value: ScreenPosture = ScreenPosture.TABLETOP
)
