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

# Fabric entrypoint method names must remain exactly as declared in the Java interfaces.
# Keep the method names globally because Fabric API/Loader classes are runtime dependencies.
-keepclassmembers class * {
    public void onInitializeClient();
    public void onInitialize();
}

# Keep names of members that the mod accesses reflectively.
-keepclassmembers class com.example.chestdropper.Features {
    *** running;
    *** movementMode;
    *** batchStarted;
    *** target;
    *** resetOriginal(...);
}

-keepclassmembers class com.example.chestdropper.ChestDropperMod {
    *** isDropping;
    *** targetChestHit;
    *** tickCounter;
    *** dropPhase;
    *** reset(...);
    *** startQuickDrop(...);
}

-keepclassmembers class com.example.chestdropper.AutoMove {
    *** moving;
    *** moveTick;
    *** scanTick;
    *** travelYaw;
    *** currentPos;
    *** processed;
    *** rightKey;
    *** haveTravelYaw;
    *** isProcessed(...);
}

# Fabric entrypoints and reflective class names are adapted in fabric.mod.json
# and in Class.forName string constants.
