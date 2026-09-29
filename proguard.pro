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

# Fabric entrypoint contract.
-keepclassmembers class * {
    public void onInitializeClient();
    public void onInitialize();
}

# Fabric client tick callback contract. Anonymous listener classes must keep the
# exact callback method name or Fabric's interface dispatch fails at runtime.
-keepclassmembers class * implements net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick {
    public void onEndTick(net.minecraft.class_310);
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
