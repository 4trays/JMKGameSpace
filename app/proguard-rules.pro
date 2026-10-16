# Nama class tidak diacak supaya stack trace di tab LOGS tetap kebaca.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**
