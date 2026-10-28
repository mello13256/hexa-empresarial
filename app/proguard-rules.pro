# kotlinx.serialization: keep generated serializers of the persisted state.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class pt.hexa.empresarial.domain.** {
    *** Companion;
}
-keepclasseswithmembers class pt.hexa.empresarial.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pt.hexa.empresarial.domain.**$$serializer { *; }
-keep class pt.hexa.empresarial.system.SystemActionsService
