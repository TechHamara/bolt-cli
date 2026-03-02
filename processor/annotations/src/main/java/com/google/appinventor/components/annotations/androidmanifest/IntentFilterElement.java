package com.google.appinventor.components.annotations.androidmanifest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for declaring IntentFilter elements in manifest.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IntentFilterElement {
    String[] actions() default {};
    String[] categories() default {};
    String[] dataSchemes() default {};
    String[] dataTypes() default {};
}
