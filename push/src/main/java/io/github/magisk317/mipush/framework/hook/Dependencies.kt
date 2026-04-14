package io.github.magisk317.mipush.framework.hook

class Dependencies {
    companion object {
        private var outerDependencies: OuterDependencies? = null

        @JvmStatic
        fun instance(): OuterDependencies? = outerDependencies

        @JvmStatic
        fun set(outerDependencies: OuterDependencies?) {
            Companion.outerDependencies = outerDependencies
        }
    }
}
