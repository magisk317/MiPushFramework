package com.xiaomi.smack.packet

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class StreamError(val code: String?) {

    override fun toString(): String {
        return "stream:error ($code)"
    }
}
