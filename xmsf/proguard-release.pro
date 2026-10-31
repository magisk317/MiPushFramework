# Release-only R8 rules.
#
# Strip verbose/debug logging call sites in release builds while keeping
# info/warn/error so the e/w ring buffer stays exportable. Debug builds,
# including -PminifyDebug, never load this file, so local diagnosis keeps
# full verbosity.

-assumenosideeffects class io.github.magisk317.xposed.logging.XLog {
    public static void v(...);
    public static void d(...);
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
}
