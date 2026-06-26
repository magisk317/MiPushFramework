# Proguard for libxposed.
-keep class io.github.magisk317.mipush.hook.LibXposedEntry {
    *;
}

-keep class io.github.magisk317.mipush.xposed.LibXposedHookApi102 {
    *;
}

# Prevent R8 from stripping/inlining Hooker.intercept() implementations.
# LSPosed invokes these at runtime via the XposedInterface$Hooker interface.
-keep,allowobfuscation class * implements io.github.libxposed.api.XposedInterface$Hooker {
    <methods>;
}

# Keep XposedRuntime inner classes used by hook chain (in magisk-xposed-kit)
-keep,allowobfuscation class io.github.magisk317.xposed.XposedRuntime$* {
    <methods>;
}
-keep,allowobfuscation class io.github.magisk317.xposed.** {
    <methods>;
}
