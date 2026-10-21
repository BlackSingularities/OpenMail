# Compose i AndroidX dostarczają własne reguły konsumenckie.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Jakarta Mail ładuje dostawców protokołów, procedury obsługi typów MIME i mechanizmy SASL po nazwach.
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.**
-dontwarn javax.naming.**
-dontwarn javax.activation.**
-dontwarn com.sun.mail.**
-dontwarn myjava.awt.**
-dontwarn org.apache.harmony.**

# jsoup: opcjonalne zależności.
-dontwarn org.jspecify.**
-dontwarn javax.annotation.**
-dontwarn com.google.re2j.**
