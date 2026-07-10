# Entry point — Forge finds by @Mod annotation scan, class name can change
-keep class io.github.jason13official.originsscout.OriginsScoutForge {
    public <init>(...);
}

# ServiceLoader MUST keep exact class name — service descriptor file references it by FQN
-keep class io.github.jason13official.originsscout.platform.ForgePlatformHelper { *; }

# Keep MOD_ID constant — referenced in string literals at runtime
-keepclassmembers class io.github.jason13official.originsscout.Constants {
    public static final java.lang.String MOD_ID;
    public static final org.slf4j.Logger LOG;
}

# Repack everything else under short namespace
-repackageclasses 'q'

# Forge/ASM interactions are fragile under optimization
-dontoptimize

# Keep annotations (Forge reads @Mod etc. at runtime)
-keepattributes *Annotation*, Signature, InnerClasses

# Suppress warnings for library classes not in classpath
-dontwarn net.minecraft.**
-dontwarn net.minecraftforge.**
-dontwarn io.github.**
-dontwarn com.mojang.**
-dontwarn org.slf4j.**