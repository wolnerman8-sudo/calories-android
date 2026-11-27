

# AiLink (balance Bluetooth) : méthodes JNI appelées par nom depuis libAILinkBle-lib.so
-keep class com.pinwang.ailinkble.** { *; }
-keep class com.elinkthings.ailinksecretlib.** { *; }
-dontwarn android.support.v4.util.ArraySet
-dontwarn com.elinkthings.**
-dontwarn com.pinwang.**

# LiteRT / TensorFlow Lite (classes appelées depuis le code natif)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# ML Kit Text Recognition (OCR étiquette, modèle embarqué)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
