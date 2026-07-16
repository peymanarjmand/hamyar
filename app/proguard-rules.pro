-keep class com.hesabyar.garage.data.local.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
