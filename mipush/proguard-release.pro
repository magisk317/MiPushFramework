# Release-only R8 rules.
#
# Public releases own no application log channel. Internal CI releases skip
# this file in build-logic, while debug builds never load it.

-assumenosideeffects class io.github.magisk317.xposed.logging.XLog {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void log(...);
    public static void structured(...);
}

# The reified logD/logV extensions compile down to TaggedLog.d/v at each call site, so the rule
# above alone left 193 debug sites — and the whole in-app debug switch — reaching the daily
# runtime log in release. Release ships no debug channel: this file is never loaded by debug
# builds and is skipped for CI builds (build-logic magisk.android.application.gradle.kts), which
# stay the full-verbosity log-capture channel.
# Audited before adding: all 193 logD/logV arguments are pure string building — no assignment,
# mutator call or side-effecting interpolation — so removing the call cannot change behavior.
-assumenosideeffects class io.github.magisk317.xposed.logging.TaggedLog {
    public void v(...);
    public void d(...);
    public void i(...);
    public void w(...);
    public void e(...);
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
