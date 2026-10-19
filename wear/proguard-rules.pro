# kotlinx.serialization: keep generated serializers of the wearshared data classes
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class uk.co.tdsstudios.noterly.wearshared.**$$serializer { *; }
-keepclassmembers class uk.co.tdsstudios.noterly.wearshared.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
