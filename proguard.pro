-injars build/input/clean.jar
-outjars build/Homka-Avto-Farm_Hwid_OBF.jar

-libraryjars <java.home>/jmods/java.base.jmod

-dontshrink
-dontoptimize
-dontwarn **
-dontnote **
-ignorewarnings

-repackageclasses obf
-allowaccessmodification
-adaptclassstrings
-adaptresourcefilecontents fabric.mod.json

-keepattributes InnerClasses,EnclosingMethod,*Annotation*,Signature,Exceptions

# Preserve Fabric callback method names globally.
-keepclassmembers class * {
    public void onInitializeClient(...);
    public void onInitialize(...);
    public void onEndTick(...);
}

# Preserve the three anonymous Fabric tick adapters as classes too.
# This avoids loader/runtime dispatch differences after post-processing.
-keep class com.example.chestdropper.AutoMove$1 { *; }
-keep class com.example.chestdropper.Features$1 { *; }
-keep class com.example.chestdropper.LicenseBootstrap$1 { *; }

# GUI classes rely on Minecraft Screen virtual-method overrides and
# reflection from the F12 handler. Keep their classes and members intact.
-keep class com.example.chestdropper.ConfigScreen { *; }
-keep class com.example.chestdropper.LicenseScreen { *; }
-keep class com.example.chestdropper.F12KeyHandler { *; }

# The automation code intentionally uses reflection between its own classes.
# Keep member names so reflective field/method lookup continues to work.
-keepclassmembers class com.example.chestdropper.AutoMove { *; }
-keepclassmembers class com.example.chestdropper.Features { *; }
-keepclassmembers class com.example.chestdropper.ChestDropperMod { *; }
-keepclassmembers class com.example.chestdropper.SnakeTransition { *; }
-keepclassmembers class com.example.chestdropper.GuiBridge { *; }
-keepclassmembers class com.example.chestdropper.LicenseManager { *; }
-keepclassmembers class com.example.chestdropper.F10Poller { *; }
-keepclassmembers class com.example.chestdropper.F12Bootstrap { *; }
-keepclassmembers class com.example.chestdropper.LicenseBootstrap { *; }
-keepclassmembers class com.example.chestdropper.SnakeTriggerFix { *; }
