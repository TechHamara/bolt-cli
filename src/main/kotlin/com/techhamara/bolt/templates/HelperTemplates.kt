package com.techhamara.bolt.templates

object HelperTemplates {

    fun getHelperTemplate(
        orgName: String,
        type: String,
        helperName: String = if (type == "str") "IntervalType" else "ModeType"
    ): String {
        return if (type == "str") {
            """
            package $orgName.helpers;

            import com.google.appinventor.components.common.OptionList;

            public enum $helperName implements OptionList<String> {
                Demo1("Demo1"),
                Demo2("Demo2"),
                Demo3("Demo3");

                private final String value;

                $helperName(String value) {
                    this.value = value;
                }

                public String toUnderlyingValue() {
                    return value;
                }
            }
            """.trimIndent()
        } else {
            """
            package $orgName.helpers;

            import com.google.appinventor.components.common.OptionList;

            public enum $helperName implements OptionList<Integer> {
                Demo1(1),
                Demo2(2),
                Demo3(3);

                private final int value;

                $helperName(int value) {
                    this.value = value;
                }

                public Integer toUnderlyingValue() {
                    return value;
                }
            }
            """.trimIndent()
        }
    }
}
