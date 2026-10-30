# R8 rules for the Barcode Scanner app.
# Derived from the upstream proguard.cfg, minus the ProGuard-only switches that
# R8 does not accept and minus -dontshrink/-dontobfuscate, which defeated the
# point of enabling the shrinker at all.

# Keep enum accessors: core looks formats up by name.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# onClick handlers referenced only from layout XML.
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}
