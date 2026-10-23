# kotlinx.serialization: mantém os serializers gerados dos DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class br.com.dhc.solar.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
