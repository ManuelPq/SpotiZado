# SpotiZado — reglas de ofuscación (release).

# El puente usa WebMessageListener con el nombre de objeto inyectado; hay que
# preservar el método anotado con @JavascriptInterface si se usa el fallback.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Modelos serializados con kotlinx.serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.imparable.spoti.bridge.** {
    *** Companion;
}
-keepclasseswithmembers class com.imparable.spoti.bridge.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# El JavaScript inyectado referencia estas clases por reflexión en el puente.
-keep class com.imparable.spoti.bridge.BridgeProtocol { *; }
