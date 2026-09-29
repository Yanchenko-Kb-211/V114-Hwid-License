-injars Homka-Avto-Farm_Hwid.jar
-outjars build/Homka-Avto-Farm_Hwid_OBF.jar

-libraryjars <java.home>/jmods/java.base.jmod

-dontshrink
-dontoptimize
-dontpreverify
-dontwarn **
-dontnote **
-ignorewarnings

-repackageclasses obf
-allowaccessmodification
-adaptclassstrings
-adaptresourcefilecontents fabric.mod.json

-keepattributes InnerClasses,EnclosingMethod,*Annotation*,Signature

# Keep names that are accessed reflectively by the mod itself.
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
# and in Class.forName string constants, so their class names can be obfuscated.
# Build pipeline: obfuscation only; optimization/shrinking are intentionally disabled for compatibility.
