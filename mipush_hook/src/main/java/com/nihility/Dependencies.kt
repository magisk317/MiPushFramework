package com.nihility

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
