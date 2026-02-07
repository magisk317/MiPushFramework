package com.oasisfeng.condom

import android.content.Context

class CondomContext private constructor() {
    companion object {
        @JvmStatic
        fun wrap(base: Context, tag: String?): Context = base

        @JvmStatic
        fun wrap(base: Context, tag: String?, options: CondomOptions): Context = base
    }
}
