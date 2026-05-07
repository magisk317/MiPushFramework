# Proguard for Xposed.
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage
-keep class * implements de.robv.android.xposed.IXposedHookInitPackageResources
-keep class io.github.magisk317.mipush.hook.XposedMod{
    *;
}
